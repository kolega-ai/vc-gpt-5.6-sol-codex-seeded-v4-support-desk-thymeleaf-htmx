package com.acme.support.decision;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

interface ActorRepository extends JpaRepository<Actor,Long> {
    Optional<Actor> findByUsername(String username);
    List<Actor> findAllByOrderByDisplayName();
    List<Actor> findByTeamIdAndEnabledTrueOrderByDisplayName(Long teamId);
}
interface OrganizationRepository extends JpaRepository<Organization,Long> { List<Organization> findAllByOrderByName(); }
interface TeamRepository extends JpaRepository<SupportTeam,Long> { List<SupportTeam> findByActiveTrueOrderByName(); }
interface CategoryRepository extends JpaRepository<Category,Long> { List<Category> findByActiveTrueOrderByName(); }
interface SlaRepository extends JpaRepository<SlaPolicy,Long> { Optional<SlaPolicy> findByPriorityAndActiveTrue(String priority); }
interface TicketRepository extends JpaRepository<Ticket,Long> {
    @Query("select t from Ticket t where (:org is null or t.organization.id=:org) and (:team is null or t.team.id=:team) and (:status is null or t.status=:status) and (:priority is null or t.priority=:priority) and (:q = '' or lower(t.subject) like concat('%',lower(:q),'%') or lower(t.reference) like concat('%',lower(:q),'%')) order by t.updatedAt desc")
    Page<Ticket> search(@Param("org") Long org,@Param("team") Long team,@Param("status") String status,@Param("priority") String priority,@Param("q") String q, Pageable pageable);
    long countByOrganizationId(Long org); long countByTeamId(Long team); long countByResolutionDueAtBeforeAndStatusNotIn(java.time.Instant at, Collection<String> statuses);
}
interface MessageRepository extends JpaRepository<Message,Long> { List<Message> findByTicketIdOrderByCreatedAt(Long ticketId); }
interface AttachmentRepository extends JpaRepository<Attachment,Long> { List<Attachment> findByTicketIdOrderByCreatedAtDesc(Long ticketId); }
interface EscalationRepository extends JpaRepository<Escalation,Long> { List<Escalation> findAllByOrderByUpdatedAtDesc(); Optional<Escalation> findByTicketId(Long ticketId); }
interface KnowledgeRepository extends JpaRepository<KnowledgeArticle,Long> {
    List<KnowledgeArticle> findByStatusAndVisibilityOrderByUpdatedAtDesc(String status,String visibility);
    List<KnowledgeArticle> findAllByOrderByUpdatedAtDesc();
}
interface AuditRepository extends JpaRepository<AuditEvent,Long> { Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable); List<AuditEvent> findTop30ByResourceTypeAndResourceIdOrderByOccurredAtDesc(String type,String id); }
interface NotificationRepository extends JpaRepository<Notification,Long> { List<Notification> findTop10ByActorIdOrderByCreatedAtDesc(Long actor); long countByActorIdAndReadAtIsNull(Long actor); }
