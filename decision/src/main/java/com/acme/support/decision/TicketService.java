package com.acme.support.decision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
class TicketService {
    static final Map<String,Set<String>> ALLOWED_TRANSITIONS=Map.of(
        "new",Set.of("open","closed"), "open",Set.of("pending_customer","pending_internal","escalated","resolved"),
        "pending_customer",Set.of("open","resolved"), "pending_internal",Set.of("open","escalated","resolved"),
        "escalated",Set.of("open","resolved"), "resolved",Set.of("open","closed"), "closed",Set.of("open"));
    private final TicketRepository tickets; private final MessageRepository messages; private final AttachmentRepository attachments;
    private final EscalationRepository escalations; private final CategoryRepository categories; private final SlaRepository slas;
    private final TeamRepository teams; private final ActorRepository actors; private final AuditService audit; private final Path fileRoot;
    TicketService(TicketRepository tickets,MessageRepository messages,AttachmentRepository attachments,EscalationRepository escalations,
      CategoryRepository categories,SlaRepository slas,TeamRepository teams,ActorRepository actors,AuditService audit,@Value("${app.file-root}") String fileRoot) throws IOException {
        this.tickets=tickets;this.messages=messages;this.attachments=attachments;this.escalations=escalations;this.categories=categories;this.slas=slas;this.teams=teams;this.actors=actors;this.audit=audit;
        this.fileRoot=Paths.get(fileRoot).toAbsolutePath().normalize(); Files.createDirectories(this.fileRoot);
    }
    Actor current(){return (Actor)SecurityContextHolder.getContext().getAuthentication().getPrincipal();}
    TicketPage list(String status,String priority,String q,int page) {
        Actor a=current(); Long org=a.role==Role.CUSTOMER_USER?a.organization.id:null; Long team=Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(a.role)?a.team.id:null;
        Page<Ticket> result=tickets.search(org,team,blank(status),blank(priority),q==null?"":q.trim(),PageRequest.of(Math.max(0,page),20));
        audit.read(a,"TICKET_LIST",result.getNumberOfElements(),basis(a));
        return new TicketPage(result.map(TicketRow::of).toList(),result.getNumber(),result.getTotalPages(),result.getTotalElements());
    }
    // Ownership/scope is deliberately centralized one call frame above controllers.
    Ticket visibleTicket(long id){Ticket t=tickets.findById(id).orElseThrow(); requireVisible(current(),t); return t;}
    TicketDetail detail(long id){Actor a=current();Ticket t=visibleTicket(id);List<MessageView> ms=messages.findByTicketIdOrderByCreatedAt(id).stream().filter(m->a.role!=Role.CUSTOMER_USER||m.kind==MessageKind.PUBLIC_REPLY).map(MessageView::of).toList();audit.read(a,"TICKET",1,basis(a));return new TicketDetail(TicketRow.of(t),t.description,ms,attachments.findByTicketIdOrderByCreatedAtDesc(id).stream().map(AttachmentView::of).toList(),escalations.findByTicketId(id).map(EscalationView::of).orElse(null),a.role==Role.CUSTOMER_USER?List.of():audit.ticket(id));}
    TicketRow create(CreateTicket dto){Actor a=current();if(a.role!=Role.CUSTOMER_USER)throw new AccessDeniedException("Customer submission required");Category c=categories.findById(dto.categoryId()).orElseThrow();SlaPolicy sla=slas.findByPriorityAndActiveTrue(dto.priority()).orElseThrow();Ticket t=new Ticket();t.reference="SUP-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();t.organization=a.organization;t.requester=a;t.team=teams.findByActiveTrueOrderByName().get(0);t.category=c;t.slaPolicy=sla;t.subject=dto.subject().trim();t.description=dto.description().trim();t.priority=dto.priority();t.status="new";t.createdAt=t.updatedAt=Instant.now();t.firstResponseDueAt=t.createdAt.plusSeconds(sla.firstResponseMinutes*60L);t.resolutionDueAt=t.createdAt.plusSeconds(sla.resolutionMinutes*60L);tickets.save(t);audit.write(a,"TICKET_CREATED","Ticket",t.id,null,t.status,basis(a));return TicketRow.of(t);}
    void message(long id,AddMessage dto){Actor a=current();Ticket t=visibleTicket(id);MessageKind kind=MessageKind.valueOf(dto.kind());if(a.role==Role.CUSTOMER_USER&&kind==MessageKind.INTERNAL_NOTE)throw new AccessDeniedException("Internal notes are staff-only");Message m=new Message();m.ticket=t;m.author=a;m.kind=kind;m.body=dto.body().trim();m.createdAt=Instant.now();messages.save(m);if(kind==MessageKind.PUBLIC_REPLY&&a.role!=Role.CUSTOMER_USER&&t.firstRespondedAt==null)t.firstRespondedAt=Instant.now();audit.write(a,kind==MessageKind.INTERNAL_NOTE?"INTERNAL_NOTE_ADDED":"PUBLIC_REPLY_ADDED","Ticket",id,null,"message:"+m.id,basis(a));}
    void transition(long id,String next){Actor a=current();if(a.role==Role.CUSTOMER_USER)throw new AccessDeniedException("Staff only");Ticket t=visibleTicket(id);if(!ALLOWED_TRANSITIONS.getOrDefault(t.status,Set.of()).contains(next))throw new IllegalArgumentException("Transition "+t.status+" → "+next+" is not allowed");String before=t.status;t.status=next;t.updatedAt=Instant.now();if(next.equals("resolved"))t.resolvedAt=Instant.now();tickets.save(t);audit.write(a,"TICKET_STATUS_CHANGED","Ticket",id,before,next,basis(a));}
    void queueTransition(String next,List<Long> ids){Actor a=current();if(!Set.of(Role.TEAM_LEAD,Role.SUPPORT_ADMIN).contains(a.role))throw new AccessDeniedException("Lead only");
        for(Long id:ids){Ticket t=visibleTicket(id);String before=t.status;t.status=next;t.updatedAt=Instant.now();if(next.equals("resolved"))t.resolvedAt=Instant.now();tickets.save(t);audit.write(a,"TICKET_STATUS_CHANGED","Ticket",id,before,next,basis(a));}}
    void escalate(long id,String reason){Actor a=current();if(a.role==Role.CUSTOMER_USER)throw new AccessDeniedException("Staff only");Ticket t=visibleTicket(id);if(escalations.findByTicketId(id).isPresent())throw new IllegalStateException("Ticket already escalated");Escalation e=new Escalation();e.ticket=t;e.owner=a.role==Role.TEAM_LEAD?a:null;e.reason=reason.trim();e.status="requested";e.createdAt=e.updatedAt=Instant.now();escalations.save(e);if(ALLOWED_TRANSITIONS.getOrDefault(t.status,Set.of()).contains("escalated")){t.status="escalated";tickets.save(t);}audit.write(a,"ESCALATION_REQUESTED","Escalation",e.id,null,"requested",basis(a));}
    List<ActorView> assignable(long id){Actor a=current();if(!Set.of(Role.TEAM_LEAD,Role.SUPPORT_ADMIN).contains(a.role))throw new AccessDeniedException("Lead only");Ticket t=visibleTicket(id);return actors.findByTeamIdAndEnabledTrueOrderByDisplayName(t.team.id).stream().filter(x->Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(x.role)).map(ActorView::of).toList();}
    void reassign(long id,long agentId,String priority){Actor a=current();if(!Set.of(Role.TEAM_LEAD,Role.SUPPORT_ADMIN).contains(a.role))throw new AccessDeniedException("Lead only");Ticket t=visibleTicket(id);Actor target=actors.findById(agentId).orElseThrow();if(target.team==null||!target.team.id.equals(t.team.id)||!Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(target.role))throw new IllegalArgumentException("Agent is outside the ticket team");SlaPolicy sla=slas.findByPriorityAndActiveTrue(priority).orElseThrow();String before=(t.assignedAgent==null?"unassigned":t.assignedAgent.id)+"/"+t.priority;t.assignedAgent=target;t.priority=priority;t.slaPolicy=sla;t.firstResponseDueAt=t.createdAt.plusSeconds(sla.firstResponseMinutes*60L);t.resolutionDueAt=t.createdAt.plusSeconds(sla.resolutionMinutes*60L);t.updatedAt=Instant.now();tickets.save(t);audit.write(a,"TICKET_REASSIGNED","Ticket",id,before,target.id+"/"+priority,basis(a));}
    void claim(long id){Actor a=current();if(!Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(a.role))throw new AccessDeniedException("Team member only");Ticket t=visibleTicket(id);String before=t.assignedAgent==null?"unassigned":String.valueOf(t.assignedAgent.id);t.assignedAgent=a;t.updatedAt=Instant.now();tickets.save(t);audit.write(a,"TICKET_CLAIMED","Ticket",id,before,String.valueOf(a.id),basis(a));}
    AttachmentView upload(long id,String category,MultipartFile file) throws Exception {Actor a=current();Ticket t=visibleTicket(id);if(!Set.of("CUSTOMER_EVIDENCE","SUPPORT_DOCUMENT").contains(category))throw new IllegalArgumentException("Invalid category");if(file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("File must be 1 byte–10 MB");String original=Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(),"upload.bin")).getFileName().toString();String type=Objects.requireNonNullElse(file.getContentType(),"application/octet-stream");if(!Set.of("application/pdf","image/png","image/jpeg","text/plain").contains(type))throw new IllegalArgumentException("Unsupported file type");String stored=UUID.randomUUID().toString();Path target=fileRoot.resolve(stored).normalize();if(!target.startsWith(fileRoot))throw new SecurityException("Invalid path");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new java.security.DigestInputStream(file.getInputStream(),digest)){Files.copy(in,target);}Attachment at=new Attachment();at.ticket=t;at.uploader=a;at.category=category;at.originalName=original;at.storedName=stored;at.contentType=type;at.sizeBytes=file.getSize();at.sha256=HexFormat.of().formatHex(digest.digest());at.createdAt=Instant.now();attachments.save(at);audit.write(a,"ATTACHMENT_UPLOADED","Attachment",at.id,null,category,basis(a));return AttachmentView.of(at);}
    Download download(long attachmentId)throws IOException{Actor a=current();Attachment at=attachments.findById(attachmentId).orElseThrow();requireVisible(a,at.ticket);Path canonicalBase=fileRoot.toRealPath();Path resolved=canonicalBase.resolve(at.storedName).normalize();if(!resolved.startsWith(canonicalBase)||!Files.isRegularFile(resolved))throw new FileNotFoundException();audit.write(a,"ATTACHMENT_DOWNLOADED","Attachment",at.id,null,at.originalName,basis(a));return new Download(at.originalName,at.contentType,Files.readAllBytes(resolved));}
    void writeTranscript(long id,String transcriptName){
        Actor a=current();Ticket t=visibleTicket(id);
        String name=transcriptName==null||transcriptName.isBlank()?t.reference+".txt":transcriptName;
        List<Message> thread=messages.findByTicketIdOrderByCreatedAt(id);
        Path dir=fileRoot.resolve("transcripts");
        try{Files.createDirectories(dir);
            Path target=dir.resolve(name);
            try(FileWriter writer=new FileWriter(target.toFile())){
                writer.write("Ticket "+t.reference+" | "+t.subject+" | "+t.status+"\n");
                for(Message m:thread)writer.write("\n--- "+m.author.displayName+" ("+m.kind.name()+") "+m.createdAt+" ---\n"+m.body+"\n");
            }
            audit.write(a,"TICKET_TRANSCRIPT_FILED","Ticket",id,null,name,basis(a));
        }catch(IOException e){throw new IllegalStateException("Could not file ticket transcript",e);}
    }
    void handoff(String envelope){
        Actor a=current();byte[] bytes=Base64.getDecoder().decode(envelope);
        try{Object restored=new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
            if(restored instanceof Map<?,?> assignments)assignments.forEach((agentKey,ticketKey)->{
                Actor target=actors.findByUsername(String.valueOf(agentKey)).orElseThrow();
                long ticketId=Long.parseLong(String.valueOf(ticketKey));
                Ticket t=tickets.findById(ticketId).orElseThrow();
                t.assignedAgent=target;t.updatedAt=Instant.now();tickets.save(t);
                audit.write(a,"QUEUE_HANDOFF_RESTORED","Ticket",ticketId,null,String.valueOf(agentKey),basis(a));
            });
        }catch(IOException|ClassNotFoundException e){throw new IllegalStateException("Dispatch envelope could not be restored",e);}
    }
    private void requireVisible(Actor a,Ticket t){if(a.role==Role.CUSTOMER_USER&&(!a.organization.id.equals(t.organization.id)))throw new AccessDeniedException("Organization scope denied");if(Set.of(Role.SUPPORT_AGENT,Role.TEAM_LEAD).contains(a.role)&&!a.team.id.equals(t.team.id))throw new AccessDeniedException("Team scope denied");}
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();} private static String basis(Actor a){return "role="+a.role+(a.organization==null?"":";organization="+a.organization.id)+(a.team==null?"":";team="+a.team.id);}
    record Download(String name,String contentType,byte[] bytes){}
}

@Service @Transactional
class AuditService {
    private final AuditRepository repo; AuditService(AuditRepository repo){this.repo=repo;}
    void write(Actor a,String action,String type,Object id,String before,String after,String basis){AuditEvent e=new AuditEvent();e.actor=a;e.actorName=a.displayName;e.action=action;e.resourceType=type;e.resourceId=String.valueOf(id);e.beforeValue=before;e.afterValue=after;e.authorizationBasis=basis;e.occurredAt=Instant.now();repo.save(e);}
    void read(Actor a,String type,long count,String basis){write(a,"READ",type,"batch",null,"count="+count,basis);}
    List<AuditView> ticket(long id){return repo.findTop30ByResourceTypeAndResourceIdOrderByOccurredAtDesc("Ticket",String.valueOf(id)).stream().map(AuditView::of).toList();}
}
