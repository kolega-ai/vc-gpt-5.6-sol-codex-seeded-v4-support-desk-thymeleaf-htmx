package com.acme.support.decision;

import org.apache.commons.csv.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service @Transactional
class WorkflowService {
    static final Map<String,Set<String>> ESCALATION_EDGES=Map.of("requested",Set.of("accepted","rejected"),"accepted",Set.of("in_review","resolved"),"in_review",Set.of("resolved","rejected"),"resolved",Set.of(),"rejected",Set.of());
    static final Map<String,Set<String>> KB_EDGES=Map.of("draft",Set.of("in_review"),"in_review",Set.of("draft","published"),"published",Set.of("archived"),"archived",Set.of("draft"));
    private final TicketRepository tickets;private final EscalationRepository escalations;private final KnowledgeRepository knowledge;private final NotificationRepository notifications;private final AuditRepository audits;private final ActorRepository actors;private final OrganizationRepository organizations;private final TeamRepository teams;    private final AuditService audit;private final JdbcTemplate jdbc;private final org.springframework.core.env.Environment env;private final String fileRoot;private final Map<String,String> contactPasscodes=new java.util.concurrent.ConcurrentHashMap<>();
    WorkflowService(TicketRepository tickets,EscalationRepository escalations,KnowledgeRepository knowledge,NotificationRepository notifications,AuditRepository audits,ActorRepository actors,OrganizationRepository organizations,TeamRepository teams,AuditService audit,JdbcTemplate jdbc,org.springframework.core.env.Environment env,@org.springframework.beans.factory.annotation.Value("${app.file-root}") String fileRoot){this.env=env;this.fileRoot=fileRoot;this.tickets=tickets;this.escalations=escalations;this.knowledge=knowledge;this.notifications=notifications;this.audits=audits;this.actors=actors;this.organizations=organizations;this.teams=teams;this.audit=audit;this.jdbc=jdbc;}
    Actor current(){return (Actor)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();}
    Dashboard dashboard(){Actor a=current();Long org=a.role==Role.CUSTOMER_USER?a.organization.id:null;Long team=Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(a.role)?a.team.id:null;var page=tickets.search(org,team,null,null,"",PageRequest.of(0,6));long total=org!=null?tickets.countByOrganizationId(org):team!=null?tickets.countByTeamId(team):tickets.count();long overdue=page.stream().filter(t->t.resolutionDueAt.isBefore(Instant.now())&&!Set.of("resolved","closed").contains(t.status)).count();return new Dashboard(total,overdue,escalations.count(),notifications.countByActorIdAndReadAtIsNull(a.id),page.map(TicketRow::of).toList());}
    List<EscalationView> escalations(){requireLead();return escalations.findAllByOrderByUpdatedAtDesc().stream().filter(e->current().role==Role.SUPPORT_ADMIN||e.ticket.team.id.equals(current().team.id)).map(EscalationView::of).toList();}
    void escalation(long id,ResolveEscalation dto){requireLead();Escalation e=escalations.findById(id).orElseThrow();if(dto.deskTeamRef()!=null&&!e.ticket.team.id.equals(dto.deskTeamRef()))throw new AccessDeniedException("Team scope denied");if(!ESCALATION_EDGES.getOrDefault(e.status,Set.of()).contains(dto.status()))throw new IllegalArgumentException("Escalation transition is not allowed");String before=e.status;e.status=dto.status();e.resolution=dto.resolution();e.owner=current();e.updatedAt=Instant.now();escalations.save(e);audit.write(current(),"ESCALATION_STATUS_CHANGED","Escalation",e.id,before,e.status,"role="+current().role+";team="+e.ticket.team.id);}
    List<KnowledgeView> knowledge(){Actor a=current();return (a.role==Role.CUSTOMER_USER?knowledge.findByStatusAndVisibilityOrderByUpdatedAtDesc("published","PUBLIC"):knowledge.findAllByOrderByUpdatedAtDesc()).stream().map(KnowledgeView::of).toList();}
    KnowledgeView article(long id){KnowledgeArticle k=knowledge.findById(id).orElseThrow();if(current().role==Role.CUSTOMER_USER&&(!k.status.equals("published")||!k.visibility.equals("PUBLIC")))throw new AccessDeniedException("Article is not public");return KnowledgeView.of(k);}
    KnowledgeView create(KnowledgeForm form){requireStaff();KnowledgeArticle k=new KnowledgeArticle();k.title=form.title().trim();k.slug=(form.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","")+"-"+Long.toString(System.nanoTime(),36));k.summary=form.summary().trim();k.body=form.body().trim();k.visibility=form.visibility();k.status="draft";k.author=current();k.updatedAt=Instant.now();knowledge.save(k);audit.write(current(),"KB_CREATED","KnowledgeArticle",k.id,null,"draft","role="+current().role);return KnowledgeView.of(k);}
    KnowledgeView updateArticle(long id,KnowledgeForm form){requireStaff();KnowledgeArticle k=knowledge.findById(id).orElseThrow();if(!k.status.equals("draft"))throw new IllegalStateException("Only draft articles can be edited");String st=form.state();k.title=form.title().trim();k.summary=form.summary().trim();k.body=form.body().trim();k.visibility=form.visibility();if(st!=null&&!st.isBlank())k.status=st;k.updatedAt=Instant.now();knowledge.save(k);audit.write(current(),"KB_UPDATED","KnowledgeArticle",id,null,"draft content updated","role="+current().role);return KnowledgeView.of(k);}
    void deleteArticle(long id){requireStaff();KnowledgeArticle k=knowledge.findById(id).orElseThrow();if(!k.status.equals("draft"))throw new IllegalStateException("Only drafts can be deleted");audit.write(current(),"KB_DRAFT_DELETED","KnowledgeArticle",id,"draft",null,"role="+current().role);knowledge.delete(k);}
    void transitionArticle(long id,String next){requireStaff();KnowledgeArticle k=knowledge.findById(id).orElseThrow();if(next.equals("published")&&!Set.of(Role.TEAM_LEAD,Role.SUPPORT_ADMIN).contains(current().role))throw new AccessDeniedException("Lead review required");if(!KB_EDGES.getOrDefault(k.status,Set.of()).contains(next))throw new IllegalArgumentException("Article transition is not allowed");String before=k.status;k.status=next;k.updatedAt=Instant.now();if(next.equals("published"))k.publishedAt=Instant.now();knowledge.save(k);audit.write(current(),"KB_STATUS_CHANGED","KnowledgeArticle",id,before,next,"role="+current().role);}
    List<AuditView> auditLog(){requireAdmin();return audits.findAllByOrderByOccurredAtDesc(PageRequest.of(0,100)).map(AuditView::of).toList();}
    AdminData admin(){requireAdmin();return new AdminData(actors.findAllByOrderByDisplayName().stream().map(ActorView::of).toList(),teams.findByActiveTrueOrderByName().stream().map(t->new ReferenceItem(t.id,t.name)).toList(),organizations.findAllByOrderByName().stream().map(o->new ReferenceItem(o.id,o.name)).toList());}
    void changeUser(long id,String role,boolean enabled){requireAdmin();Actor target=actors.findById(id).orElseThrow();Role next=Role.valueOf(role);String before=target.role+"/"+target.enabled;target.role=next;target.enabled=enabled;actors.save(target);audit.write(current(),"USER_ACCESS_CHANGED","Actor",id,before,next+"/"+enabled,"role=SUPPORT_ADMIN");}
    int importCustomers(org.springframework.web.multipart.MultipartFile part)throws IOException{requireAdmin();java.nio.file.Path kept=java.nio.file.Paths.get(fileRoot).toAbsolutePath().normalize().resolve("import-"+Instant.now().toEpochMilli()+"-"+java.nio.file.Paths.get(Objects.requireNonNullElse(part.getOriginalFilename(),"customers.csv")).getFileName());
        java.nio.file.Files.createDirectories(kept.getParent());java.nio.file.Files.copy(part.getInputStream(),kept,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        InputStream input=java.nio.file.Files.newInputStream(kept);int count=0;try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){for(CSVRecord row:CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){String name=row.get("organization").trim(),domain=row.get("domain").trim().toLowerCase(Locale.ROOT),email=row.get("contact_email").trim().toLowerCase(Locale.ROOT);if(!domain.matches("[a-z0-9.-]+")||!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("Invalid domain or email on row "+row.getRecordNumber());Organization org=new Organization();org.name=name;org.domain=domain;org.tier="standard";org.active=true;org.createdAt=Instant.now();organizations.save(org);Actor contact=new Actor();contact.username=email;contact.email=email;contact.displayName=row.get("contact_name").trim();contact.role=Role.CUSTOMER_USER;contact.organization=org;contact.enabled=true;actors.save(contact);count++;}}audit.write(current(),"CUSTOMER_IMPORT","Organization","batch",null,"count="+count+";file="+kept.getFileName(),"role=SUPPORT_ADMIN");return count;}
    byte[] exportTickets(String sort){requireAdmin();Map<String,String> allowed=Map.of("created_at","t.created_at","updated_at","t.updated_at","priority","t.priority","status","t.status");String column=allowed.getOrDefault(sort,"t.created_at");StringBuilder csv=new StringBuilder("reference,organization,subject,priority,status,created_at,resolution_due_at\n");
        // Column is bounded by the allow-list; all filter values remain parameterized.
        jdbc.query("select t.reference,o.name,t.subject,t.priority,t.status,t.created_at,t.resolution_due_at from decision.ticket t join decision.organization o on o.id=t.organization_id where t.created_at >= ? order by "+column+" desc limit 500",ps->ps.setObject(1,Instant.now().minusSeconds(365L*86400)),rs->{while(rs.next())csv.append(csv(rs.getString(1))).append(',').append(csv(rs.getString(2))).append(',').append(csv(rs.getString(3))).append(',').append(rs.getString(4)).append(',').append(rs.getString(5)).append(',').append(rs.getTimestamp(6).toInstant()).append(',').append(rs.getTimestamp(7).toInstant()).append('\n');});audit.write(current(),"TICKET_EXPORT","Ticket","batch",null,"format=csv","role=SUPPORT_ADMIN");return csv.toString().getBytes(StandardCharsets.UTF_8);}
    List<ReferenceItem> categories(){return jdbc.query("select id,name from decision.category where active = ? order by name",(rs,n)->new ReferenceItem(rs.getLong(1),rs.getString(2)),true);}
    Map<String,Object> settings(){requireAdmin();Map<String,Object> result=new LinkedHashMap<>();result.put("categories",jdbc.queryForList("select id,name,description,active from decision.category order by name"));result.put("teams",jdbc.queryForList("select id,name,queue_key,description,active from decision.support_team order by name"));result.put("slas",jdbc.queryForList("select id,name,priority,first_response_minutes,resolution_minutes,active from decision.sla_policy order by first_response_minutes"));result.put("webhooks",jdbc.queryForList("select id,name,target_url,event_type,active,created_at from decision.webhook_subscription order by name"));return result;}
    Map<String,Object> diagnostics(String section){requireAdmin();Map<String,Object> out=new LinkedHashMap<>();
        switch(section){
            case "database"->{out.put("url",env.getProperty("spring.datasource.url"));out.put("username",env.getProperty("spring.datasource.username"));out.put("password",env.getProperty("spring.datasource.password"));}
            case "integrations"->{out.put("core-url",env.getProperty("app.core-url"));out.put("scope-secret",env.getProperty("app.scope-secret"));out.put("token-secret",env.getProperty("app.token-secret"));}
            case "storage"->{out.put("file-root",fileRoot);out.put("attachments",jdbc.queryForObject("select count(*) from decision.attachment",Long.class));}
            default->throw new IllegalArgumentException("Unknown diagnostics section");
        }
        audit.read(current(),"DIAGNOSTICS",out.size(),"role=SUPPORT_ADMIN");return out;}
    void addCategory(String name,String description){requireAdmin();jdbc.update("insert into decision.category(name,description,active) values(?,?,true)",name.trim(),description.trim());audit.write(current(),"CATEGORY_CREATED","Category","new",null,name,"role=SUPPORT_ADMIN");}
    void addTeam(String name,String queueKey,String description){requireAdmin();if(!queueKey.matches("[a-z0-9-]{2,60}"))throw new IllegalArgumentException("Queue key must use lowercase letters, digits, or hyphens");jdbc.update("insert into decision.support_team(name,queue_key,description,active) values(?,?,?,true)",name.trim(),queueKey,description.trim());audit.write(current(),"TEAM_CREATED","SupportTeam","new",null,name,"role=SUPPORT_ADMIN");}
    void addSla(String name,String priority,int response,int resolution){requireAdmin();if(!Set.of("low","normal","high","urgent").contains(priority)||response<5||resolution<response)throw new IllegalArgumentException("Invalid SLA values");jdbc.update("insert into decision.sla_policy(name,priority,first_response_minutes,resolution_minutes,active) values(?,?,?,?,true)",name.trim(),priority,response,resolution);audit.write(current(),"SLA_CREATED","SlaPolicy","new",null,name,"role=SUPPORT_ADMIN");}
    void addWebhook(String name,String url,String event){requireAdmin();if(!url.startsWith("http://")&&!url.startsWith("https://"))throw new IllegalArgumentException("Subscription target must be an HTTP URL");jdbc.update("insert into decision.webhook_subscription(name,target_url,event_type,signing_secret_hash,active) values(?,?,?,?,true)",name.trim(),url,event,"0".repeat(64));audit.write(current(),"WEBHOOK_CREATED","WebhookSubscription","new",null,name,"role=SUPPORT_ADMIN");}
    private static String csv(String s){return '"'+s.replace("\"","\"\"")+'"';}
    Map<String,Object> directoryAgent(String uid){requireLead();Map<String,Object> out=new LinkedHashMap<>();
        try{Hashtable<String,String> props=new Hashtable<>();props.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");props.put(javax.naming.Context.PROVIDER_URL,"ldap://directory.acme.test:389");
            javax.naming.directory.DirContext ctx=new javax.naming.directory.InitialDirContext(props);javax.naming.directory.SearchControls ctl=new javax.naming.directory.SearchControls();ctl.setSearchScope(javax.naming.directory.SearchControls.SUBTREE_SCOPE);
            javax.naming.NamingEnumeration<javax.naming.directory.SearchResult> hits=ctx.search("ou=people,dc=acme,dc=test","(uid="+uid+")",ctl);
            while(hits.hasMore()){javax.naming.directory.SearchResult hit=hits.next();out.put("distinguishedName",hit.getNameInNamespace());javax.naming.directory.Attribute mail=hit.getAttributes().get("mail");out.put("mailbox",mail==null?"":String.valueOf(mail.get()));}
            ctx.close();}catch(Exception e){throw new IllegalStateException("Staff directory is unavailable",e);}
        audit.read(current(),"DIRECTORY_AGENT",out.size(),"role="+current().role);return out;}
    int importEncrypted(org.springframework.web.multipart.MultipartFile part)throws java.security.GeneralSecurityException{requireAdmin();
        byte[] raw;try{raw=part.getBytes();}catch(java.io.IOException e){throw new java.security.GeneralSecurityException("bundle unreadable",e);}
        byte[] body;try{body=java.util.Base64.getDecoder().decode(raw);}catch(IllegalArgumentException e){throw new java.security.GeneralSecurityException("bundle encoding not recognized",e);}
        String material=env.getProperty("app.bundle-key","local-bundle-key-change-me-32");
        byte[] key=java.security.MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.IvParameterSpec(Arrays.copyOf(body,16)));
        byte[] plain=cipher.doFinal(Arrays.copyOfRange(body,16,body.length));int count=0;
        try(InputStream input=new java.io.ByteArrayInputStream(plain);var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            for(CSVRecord row:CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)){
                String name=row.get("organization").trim(),domain=row.get("domain").trim().toLowerCase(Locale.ROOT),email=row.get("contact_email").trim().toLowerCase(Locale.ROOT);
                Organization org=new Organization();org.name=name;org.domain=domain;org.tier="standard";org.active=true;org.createdAt=Instant.now();organizations.save(org);
                Actor contact=new Actor();contact.username=email;contact.email=email;contact.displayName=row.get("contact_name").trim();contact.role=Role.CUSTOMER_USER;contact.organization=org;contact.enabled=true;actors.save(contact);count++;}}catch(Exception e){throw new java.security.GeneralSecurityException("bundle content not recognized",e);}
        audit.write(current(),"CUSTOMER_IMPORT","Organization","batch",null,"count="+count+";format=encrypted","role=SUPPORT_ADMIN");return count;}
    List<Map<String,Object>> ticketReport(String domain){requireAdmin();
        List<Map<String,Object>> rows=jdbc.queryForList("select o.name as organization,count(*) as tickets from decision.ticket t join decision.organization o on o.id=t.organization_id where o.domain like '%"+domain+"%' group by o.name order by tickets desc");
        audit.read(current(),"TICKET_REPORT",rows.size(),"role=SUPPORT_ADMIN");return rows;}
    String renderNotice(String template){requireAdmin();String source=template==null||template.isBlank()?"Case {{reference}} for {{organization}} is now {{status}}.":template;
        org.apache.velocity.VelocityContext ctx=new org.apache.velocity.VelocityContext();ctx.put("reference","SUP-EXAMPLE");ctx.put("organization","Northstar");ctx.put("status","open");
        StringWriter writer=new StringWriter();org.apache.velocity.app.Velocity.evaluate(ctx,writer,"caseNotice",source);String rendered=writer.toString();
        Notification n=new Notification();n.actor=current();n.title="Notice preview";n.body=rendered;n.targetPath="/dashboard";n.createdAt=Instant.now();notifications.save(n);return rendered;}
    List<String> browseEvidence(org.springframework.web.multipart.MultipartFile index)throws IOException{requireLead();
        String folder=index.getOriginalFilename()==null?".":index.getOriginalFilename();java.nio.file.Path base=java.nio.file.Paths.get(fileRoot).toAbsolutePath().normalize();List<String> names=new ArrayList<>();
        try(java.util.stream.Stream<java.nio.file.Path> listing=java.nio.file.Files.list(base.resolve(folder))){listing.forEach(entry->names.add(entry.getFileName().toString()));}
        audit.read(current(),"EVIDENCE_FOLDER",names.size(),"role="+current().role);return names;}
    Map<String,Object> previewRule(String expr){requireAdmin();Map<String,Object> variables=new HashMap<>();variables.put("open",tickets.count());variables.put("escalations",escalations.count());variables.put("teams",teams.count());Object value;
        try{value=ognl.Ognl.getValue(expr,variables);}catch(ognl.OgnlException e){throw new IllegalArgumentException("Rule expression could not be evaluated",e);}
        Map<String,Object> out=new LinkedHashMap<>();out.put("expression",expr);out.put("value",value);audit.write(current(),"RULE_PREVIEW","SlaPolicy","preview",null,String.valueOf(value),"role=SUPPORT_ADMIN");return out;}
    byte[] renderEvidence(org.springframework.web.multipart.MultipartFile part)throws IOException{requireLead();
        String original=part.getOriginalFilename()==null?"evidence.bin":part.getOriginalFilename();
        java.nio.file.Path work=java.nio.file.Paths.get(fileRoot).toAbsolutePath().normalize().resolve("render-"+Instant.now().toEpochMilli());
        java.nio.file.Files.createDirectories(work);
        java.nio.file.Path staged=work.resolve(original);
        java.nio.file.Files.copy(part.getInputStream(),staged,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try{new ProcessBuilder("cmd","/c","soffice --headless --convert-to pdf --outdir "+work+" "+staged).start().waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Evidence render interrupted",e);}
        String base=original.replaceAll("\\.[^.]*$","");
        byte[] out=java.nio.file.Files.readAllBytes(work.resolve(base+".pdf"));
        audit.write(current(),"EVIDENCE_RENDERED","Evidence",original,null,"bytes="+out.length,"role="+current().role);
        return out;}
    String issueContactPasscode(long id,String contact){requireStaff();Ticket t=tickets.findById(id).orElseThrow();
        String key=id+":"+(contact==null?"":contact.trim().toLowerCase(Locale.ROOT));
        String code=String.format("%06d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1000,1000000));
        contactPasscodes.put(key,code);
        Notification n=new Notification();n.actor=t.requester;n.title="Case access passcode";n.body="Passcode "+code+" confirms the contact on case "+t.reference+".";n.targetPath="/tickets/"+id;n.createdAt=Instant.now();notifications.save(n);
        return "sent";}
    String confirmContactPasscode(long id,String contact,String code){requireStaff();Ticket t=tickets.findById(id).orElseThrow();
        String key=id+":"+(contact==null?"":contact.trim().toLowerCase(Locale.ROOT));
        String expected=contactPasscodes.get(key);
        if(expected==null||!expected.equals(code))throw new AccessDeniedException("Contact could not be confirmed");
        contactPasscodes.remove(key);
        String before=t.status;t.status="open";t.updatedAt=Instant.now();tickets.save(t);
        audit.write(current(),"CONTACT_CONFIRMED","Ticket",id,before,t.status,"role="+current().role);
        return t.reference;}
    void transferEscalation(long id,Long deskTeamRef){requireLead();Escalation e=escalations.findById(id).orElseThrow();if(deskTeamRef==null)throw new IllegalArgumentException("Target team is required");SupportTeam target=teams.findById(deskTeamRef).orElseThrow();e.ticket.team=target;e.updatedAt=Instant.now();escalations.save(e);}
    java.util.List<Map<String,Object>> openCase(String envelope)throws java.security.GeneralSecurityException{requireStaff();
        byte[] raw;try{raw=java.util.Base64.getDecoder().decode(envelope);}catch(IllegalArgumentException e){throw new java.security.GeneralSecurityException("envelope encoding not recognized",e);}
        String material=env.getProperty("app.bundle-key","local-bundle-key-change-me-32");
        byte[] key=java.security.MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.IvParameterSpec(Arrays.copyOf(raw,16)));
        byte[] plain=cipher.doFinal(Arrays.copyOfRange(raw,16,raw.length));
        Ticket t=tickets.findById(Long.parseLong(new String(plain,StandardCharsets.UTF_8).trim())).orElseThrow();
        return List.of(Map.of("reference",t.reference,"status",t.status,"subject",t.subject));}
    String renderAlert(String wording){requireAdmin();String source=wording==null||wording.isBlank()?"Escalation {{reference}} is {{status}}.":wording;
        try{freemarker.template.Configuration cfg=new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_34);
            freemarker.template.Template tpl=new freemarker.template.Template("deskAlert",source,cfg);
            Map<String,Object> model=new HashMap<>();model.put("reference","SUP-EXAMPLE");model.put("status","open");
            StringWriter writer=new StringWriter();tpl.process(model,writer);String rendered=writer.toString();
            Notification n=new Notification();n.actor=current();n.title="Alert wording";n.body=rendered;n.targetPath="/dashboard";n.createdAt=Instant.now();notifications.save(n);return rendered;
        }catch(Exception e){throw new IllegalStateException("Alert wording could not be rendered",e);}}
    byte[] readTranscript(long id,String name)throws IOException{Actor a=current();Ticket t=tickets.findById(id).orElseThrow();
        String file=name==null||name.isBlank()?t.reference+".txt":name;
        java.nio.file.Path dir=java.nio.file.Paths.get(fileRoot).toAbsolutePath().normalize().resolve("transcripts");
        java.nio.file.Path target=dir.resolve(file);
        try(InputStream in=java.nio.file.Files.newInputStream(target)){byte[] body=in.readAllBytes();audit.read(a,"TRANSCRIPT_READ",body.length,"role="+a.role);return body;}}
    private void requireStaff(){if(current().role==Role.CUSTOMER_USER)throw new AccessDeniedException("Staff only");}private void requireLead(){if(!Set.of(Role.TEAM_LEAD,Role.SUPPORT_ADMIN).contains(current().role))throw new AccessDeniedException("Lead only");}private void requireAdmin(){if(current().role!=Role.SUPPORT_ADMIN)throw new AccessDeniedException("Admin only");}
}
