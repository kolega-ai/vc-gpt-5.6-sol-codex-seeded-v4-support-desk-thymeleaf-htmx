package com.acme.support.decision;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

record ActorView(Long id,String username,String displayName,String role,Long organizationId,Long teamId,boolean enabled) {
    static ActorView of(Actor a){ return new ActorView(a.id,a.username,a.displayName,a.role.name(),a.organization==null?null:a.organization.id,a.team==null?null:a.team.id,a.enabled); }
}
record TicketRow(Long id,String reference,String subject,String organization,String requester,String assignee,String team,String category,String priority,String status,Instant updatedAt,Instant responseDueAt,Instant resolutionDueAt,boolean overdue) {
    static TicketRow of(Ticket t){ return new TicketRow(t.id,t.reference,t.subject,t.organization.name,t.requester.displayName,t.assignedAgent==null?"Unassigned":t.assignedAgent.displayName,t.team.name,t.category.name,t.priority,t.status,t.updatedAt,t.firstResponseDueAt,t.resolutionDueAt,t.resolutionDueAt.isBefore(Instant.now())&&!List.of("resolved","closed").contains(t.status)); }
}
record MessageView(Long id,String author,String kind,String body,Instant createdAt){ static MessageView of(Message m){return new MessageView(m.id,m.author.displayName,m.kind.name(),m.body,m.createdAt);} }
record AttachmentView(Long id,String name,String category,String contentType,long sizeBytes,Instant createdAt){static AttachmentView of(Attachment a){return new AttachmentView(a.id,a.originalName,a.category,a.contentType,a.sizeBytes,a.createdAt);} }
record EscalationView(Long id,Long ticketId,String ticketReference,String reason,String status,String owner,String resolution,Long deskTeamRef,Instant updatedAt){static EscalationView of(Escalation e){return new EscalationView(e.id,e.ticket.id,e.ticket.reference,e.reason,e.status,e.owner==null?"Unassigned":e.owner.displayName,e.resolution,e.ticket.team.id,e.updatedAt);} }
record AuditView(Long id,String actor,String action,String resourceType,String resourceId,String beforeValue,String afterValue,String authorizationBasis,Instant at){static AuditView of(AuditEvent e){return new AuditView(e.id,e.actorName,e.action,e.resourceType,e.resourceId,e.beforeValue,e.afterValue,e.authorizationBasis,e.occurredAt);} }
record TicketDetail(TicketRow ticket,String description,List<MessageView> messages,List<AttachmentView> attachments,EscalationView escalation,List<AuditView> audit) {}
record TicketPage(List<TicketRow> content,int page,int totalPages,long totalElements) {}
record Dashboard(long tickets,long overdue,long escalations,long unread,List<TicketRow> recent) {}
record CreateTicket(@NotBlank @Size(max=180) String subject,@NotBlank @Size(max=5000) String description,@NotNull Long categoryId,@Pattern(regexp="low|normal|high|urgent") String priority) {}
record AddMessage(@NotBlank @Size(max=5000) String body,@Pattern(regexp="PUBLIC_REPLY|INTERNAL_NOTE") String kind) {}
record Transition(@NotBlank String status) {}
record Escalate(@NotBlank @Size(max=1000) String reason) {}
record ResolveEscalation(@NotBlank @Pattern(regexp="accepted|in_review|resolved|rejected") String status,@Size(max=2000) String resolution,Long deskTeamRef) {}
record KnowledgeView(Long id,String title,String slug,String summary,String body,String status,String visibility,String author,Instant updatedAt){static KnowledgeView of(KnowledgeArticle k){return new KnowledgeView(k.id,k.title,k.slug,k.summary,k.body,k.status,k.visibility,k.author.displayName,k.updatedAt);} }
record KnowledgeForm(@NotBlank @Size(max=180) String title,@NotBlank @Size(max=400) String summary,@NotBlank @Size(max=10000) String body,@Pattern(regexp="PUBLIC|STAFF") String visibility,String state) {}
record ReferenceItem(Long id,String name) {}
record AdminData(List<ActorView> users,List<ReferenceItem> teams,List<ReferenceItem> organizations) {}
