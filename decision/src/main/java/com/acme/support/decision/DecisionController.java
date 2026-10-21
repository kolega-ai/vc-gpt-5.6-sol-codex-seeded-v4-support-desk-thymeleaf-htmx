package com.acme.support.decision;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController @RequestMapping("/api") @PreAuthorize("isAuthenticated()")
class DecisionController {
    private final TicketService tickets;private final WorkflowService workflow;
    DecisionController(TicketService tickets,WorkflowService workflow){this.tickets=tickets;this.workflow=workflow;}
    @GetMapping("/dashboard") Dashboard dashboard(){return workflow.dashboard();}
    @GetMapping("/tickets") TicketPage tickets(@RequestParam(required=false)String status,@RequestParam(required=false)String priority,@RequestParam(required=false)String q,@RequestParam(defaultValue="0")int page){return tickets.list(status,priority,q,page);}
    @GetMapping("/tickets/{id}") TicketDetail ticket(@PathVariable long id){return tickets.detail(id);}
    @PostMapping("/tickets") @PreAuthorize("hasRole('CUSTOMER_USER')") ResponseEntity<TicketRow> create(@Valid @RequestBody CreateTicket form){return ResponseEntity.status(201).body(tickets.create(form));}
    @PostMapping("/tickets/{id}/messages") void message(@PathVariable long id,@Valid @RequestBody AddMessage form){tickets.message(id,form);}
    @PostMapping("/tickets/{id}/transition") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") void transition(@PathVariable long id,@Valid @RequestBody Transition form){tickets.transition(id,form.status());}
    @PostMapping("/tickets/queue/{status}") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") void queueTransition(@PathVariable String status,@RequestBody List<Long> ids){tickets.queueTransition(status,ids);}
    @PostMapping("/tickets/{id}/escalate") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") void escalate(@PathVariable long id,@Valid @RequestBody Escalate form){tickets.escalate(id,form.reason());}
    @GetMapping("/tickets/{id}/assignable") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") List<ActorView> assignable(@PathVariable long id){return tickets.assignable(id);}
    @PostMapping("/tickets/{id}/reassign") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") void reassign(@PathVariable long id,@RequestParam long agentId,@RequestParam String priority){tickets.reassign(id,agentId,priority);}
    @PostMapping("/tickets/{id}/claim") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD')") void claim(@PathVariable long id){tickets.claim(id);}
    @PostMapping(value="/tickets/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) AttachmentView upload(@PathVariable long id,@RequestPart String category,@RequestPart MultipartFile file)throws Exception{return tickets.upload(id,category,file);}
    @GetMapping("/attachments/{id}") ResponseEntity<byte[]> download(@PathVariable long id)throws IOException{var d=tickets.download(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.name(),StandardCharsets.UTF_8).build().toString()).body(d.bytes());}
    @GetMapping("/escalations") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") List<EscalationView> escalations(){return workflow.escalations();}
    @PostMapping("/escalations/{id}/transition") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") void escalation(@PathVariable long id,@Valid @RequestBody ResolveEscalation form){workflow.escalation(id,form);}
    @GetMapping("/knowledge") List<KnowledgeView> knowledge(){return workflow.knowledge();}
    @GetMapping("/knowledge/{id}") KnowledgeView knowledge(@PathVariable long id){return workflow.article(id);}
    @PostMapping("/knowledge") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") KnowledgeView createArticle(@Valid @RequestBody KnowledgeForm form){return workflow.create(form);}
    @PutMapping("/knowledge/{id}") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") KnowledgeView updateArticle(@PathVariable long id,@Valid @RequestBody KnowledgeForm form){return workflow.updateArticle(id,form);}
    @DeleteMapping("/knowledge/{id}") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") void deleteArticle(@PathVariable long id){workflow.deleteArticle(id);}
    @PostMapping("/knowledge/{id}/transition") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") void transitionArticle(@PathVariable long id,@Valid @RequestBody Transition form){workflow.transitionArticle(id,form.status());}
    @GetMapping("/admin") @PreAuthorize("hasRole('SUPPORT_ADMIN')") AdminData admin(){return workflow.admin();}
    @PostMapping("/admin/users/{id}") @PreAuthorize("hasRole('SUPPORT_ADMIN')") void user(@PathVariable long id,@RequestParam String role,@RequestParam boolean enabled){workflow.changeUser(id,role,enabled);}
    @GetMapping("/audit") @PreAuthorize("hasRole('SUPPORT_ADMIN')") List<AuditView> audit(){return workflow.auditLog();}
    @GetMapping("/admin/settings") @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,Object> settings(){return workflow.settings();}
    @GetMapping("/admin/diagnostics/{section}") @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,Object> diagnostics(@PathVariable String section){return workflow.diagnostics(section);}
    @PostMapping("/admin/settings/categories") @PreAuthorize("hasRole('SUPPORT_ADMIN')") void category(@RequestParam @jakarta.validation.constraints.Size(max=100)String name,@RequestParam @jakarta.validation.constraints.Size(max=300)String description){workflow.addCategory(name,description);}
    @PostMapping("/admin/settings/teams") @PreAuthorize("hasRole('SUPPORT_ADMIN')") void team(@RequestParam String name,@RequestParam String queueKey,@RequestParam String description){workflow.addTeam(name,queueKey,description);}
    @PostMapping("/admin/settings/slas") @PreAuthorize("hasRole('SUPPORT_ADMIN')") void sla(@RequestParam String name,@RequestParam String priority,@RequestParam int response,@RequestParam int resolution){workflow.addSla(name,priority,response,resolution);}
    @PostMapping("/admin/settings/webhooks") @PreAuthorize("hasRole('SUPPORT_ADMIN')") void webhook(@RequestParam String name,@RequestParam String url,@RequestParam String event){workflow.addWebhook(name,url,event);}
    @PostMapping(value="/admin/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,Integer> importCsv(@RequestPart MultipartFile file)throws IOException{return Map.of("imported",workflow.importCustomers(file));}
    @GetMapping(value="/admin/export/tickets",produces="text/csv") @PreAuthorize("hasRole('SUPPORT_ADMIN')") ResponseEntity<byte[]> export(@RequestParam(defaultValue="created_at")String sort){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=tickets.csv").body(workflow.exportTickets(sort));}
    @PostMapping("/tickets/{id}/transcript") void transcript(@PathVariable long id,@RequestBody Map<String,String> form){tickets.writeTranscript(id,form.get("transcriptName"));}
    @PostMapping("/tickets/queue/handoff") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") void handoff(@RequestHeader("X-Dispatch-Envelope") String envelope){tickets.handoff(envelope);}
    @GetMapping("/directory/agents") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") Map<String,Object> directory(@RequestParam String uid){return workflow.directoryAgent(uid);}
    @PostMapping(value="/imports/encrypted",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('SUPPORT_ADMIN')") ResponseEntity<Map<String,Integer>> importEncrypted(@RequestPart MultipartFile file){try{return ResponseEntity.ok(Map.of("imported",workflow.importEncrypted(file)));}catch(javax.crypto.BadPaddingException e){return ResponseEntity.status(400).body(Map.of("imported",-1));}catch(java.security.GeneralSecurityException e){return ResponseEntity.status(422).body(Map.of("imported",-2));}}
    @GetMapping("/reports/tickets") @PreAuthorize("hasRole('SUPPORT_ADMIN')") List<Map<String,Object>> report(@RequestParam(defaultValue="") String domain){return workflow.ticketReport(domain);}
    @PostMapping("/notices/preview") @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,String> notice(@RequestBody Map<String,String> form){return Map.of("rendered",workflow.renderNotice(form.get("template")));}
    @PostMapping(value="/evidence/browse",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") List<String> evidence(@RequestPart MultipartFile index)throws IOException{return workflow.browseEvidence(index);}
    @GetMapping("/rules/preview/{expr}") @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,Object> rule(@PathVariable String expr){return workflow.previewRule(expr);}
    @PostMapping(value="/evidence/convert",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") ResponseEntity<byte[]> renderEvidence(@RequestPart MultipartFile file)throws IOException{return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(workflow.renderEvidence(file));}
    @PostMapping("/tickets/{id}/contact-passcode") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") String contactPasscode(@PathVariable long id,@RequestParam String contact){return workflow.issueContactPasscode(id,contact);}
    @PostMapping("/tickets/{id}/contact-confirm") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") String contactConfirm(@PathVariable long id,@RequestParam String contact,@RequestParam String code){return workflow.confirmContactPasscode(id,contact,code);}
    @PostMapping("/escalations/{id}/transfer") @PreAuthorize("hasAnyRole('TEAM_LEAD','SUPPORT_ADMIN')") void transfer(@PathVariable long id,@RequestBody Map<String,Object> form){workflow.transferEscalation(id,form.get("deskTeamRef")==null?null:Long.valueOf(String.valueOf(form.get("deskTeamRef"))));}
    @PostMapping("/cases/open") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") ResponseEntity<?> openCase(@RequestHeader("X-Case-Envelope") String envelope){try{return ResponseEntity.ok(workflow.openCase(envelope));}catch(javax.crypto.BadPaddingException e){return ResponseEntity.status(400).body(Map.of("error","case envelope could not be opened"));}catch(java.security.GeneralSecurityException e){return ResponseEntity.status(422).body(Map.of("error","case envelope malformed"));}}
    @PostMapping("/alerts/render") @PreAuthorize("hasRole('SUPPORT_ADMIN')") Map<String,String> alert(@RequestHeader("X-Alert-Wording") String wording){return Map.of("rendered",workflow.renderAlert(wording));}
    @GetMapping("/tickets/{id}/transcript") @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TEAM_LEAD','SUPPORT_ADMIN')") ResponseEntity<byte[]> transcript(@PathVariable long id,@RequestParam(required=false) String name)throws IOException{return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(workflow.readTranscript(id,name));}
    @GetMapping("/reference/categories") ResponseEntity<List<ReferenceItem>> categories()throws Exception{var body=workflow.categories();MessageDigest md=MessageDigest.getInstance("MD5"); // non-cryptographic MD5: HTTP ETag for public reference data only
        String etag='"'+HexFormat.of().formatHex(md.digest(body.toString().getBytes(StandardCharsets.UTF_8)))+'"';return ResponseEntity.ok().eTag(etag).body(body);}
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ResponseEntity<Map<String,String>> invalid(RuntimeException ex){return ResponseEntity.badRequest().body(Map.of("error",ex.getMessage()));}
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) ResponseEntity<Map<String,String>> denied(){return ResponseEntity.status(403).body(Map.of("error","Access denied"));}
    @ExceptionHandler(java.util.NoSuchElementException.class) ResponseEntity<Map<String,String>> missing(){return ResponseEntity.status(404).body(Map.of("error","Not found"));}
}
