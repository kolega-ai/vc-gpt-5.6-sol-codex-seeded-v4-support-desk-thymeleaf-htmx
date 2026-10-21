package com.acme.support.decision;

import jakarta.persistence.*;
import java.time.Instant;

enum Role { SUPPORT_ADMIN, SUPPORT_AGENT, TEAM_LEAD, CUSTOMER_USER }
enum TicketStatus { new_, open, pending_customer, pending_internal, escalated, resolved, closed;
    static TicketStatus from(String value) { return value.equals("new") ? new_ : valueOf(value); }
    String wire() { return this == new_ ? "new" : name(); }
}
enum MessageKind { PUBLIC_REPLY, INTERNAL_NOTE }

@Entity @Table(name="organization")
class Organization {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String name; String domain; String tier; boolean active;
    @Column(name="created_at") Instant createdAt;
}

@Entity @Table(name="support_team")
class SupportTeam {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String name; @Column(name="queue_key") String queueKey; String description; boolean active;
}

@Entity @Table(name="actor")
class Actor {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String username; @Column(name="display_name") String displayName; String email;
    @Enumerated(EnumType.STRING) Role role;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="organization_id") Organization organization;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="team_id") SupportTeam team;
    boolean enabled;
}

@Entity @Table(name="category")
class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String name; String description; boolean active;
}

@Entity @Table(name="sla_policy")
class SlaPolicy {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String name; String priority;
    @Column(name="first_response_minutes") int firstResponseMinutes;
    @Column(name="resolution_minutes") int resolutionMinutes;
    boolean active;
}

@Entity @Table(name="ticket")
class Ticket {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String reference;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="organization_id") Organization organization;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="requester_id") Actor requester;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="assigned_agent_id") Actor assignedAgent;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="team_id") SupportTeam team;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="category_id") Category category;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="sla_policy_id") SlaPolicy slaPolicy;
    String subject; @Column(columnDefinition="text") String description; String priority; String status;
    @Column(name="created_at") Instant createdAt; @Column(name="updated_at") Instant updatedAt;
    @Column(name="first_response_due_at") Instant firstResponseDueAt;
    @Column(name="resolution_due_at") Instant resolutionDueAt;
    @Column(name="first_responded_at") Instant firstRespondedAt;
    @Column(name="resolved_at") Instant resolvedAt;
    @Version long version;
}

@Entity @Table(name="message")
class Message {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="ticket_id") Ticket ticket;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="author_id") Actor author;
    @Enumerated(EnumType.STRING) MessageKind kind;
    String body; @Column(name="created_at") Instant createdAt;
}

@Entity @Table(name="attachment")
class Attachment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="ticket_id") Ticket ticket;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="uploader_id") Actor uploader;
    String category; @Column(name="original_name") String originalName;
    @Column(name="stored_name") String storedName; @Column(name="content_type") String contentType;
    @Column(name="size_bytes") long sizeBytes; String sha256; @Column(name="created_at") Instant createdAt;
}

@Entity @Table(name="escalation")
class Escalation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="ticket_id") Ticket ticket;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="owner_id") Actor owner;
    String reason; String status; String resolution;
    @Column(name="created_at") Instant createdAt; @Column(name="updated_at") Instant updatedAt;
}

@Entity @Table(name="knowledge_article")
class KnowledgeArticle {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    String title; String slug; String summary; @Column(columnDefinition="text") String body;
    String status; String visibility;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="author_id") Actor author;
    @Column(name="updated_at") Instant updatedAt; @Column(name="published_at") Instant publishedAt;
}

@Entity @Table(name="audit_event")
class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="actor_id") Actor actor;
    @Column(name="actor_name") String actorName; String action;
    @Column(name="resource_type") String resourceType; @Column(name="resource_id") String resourceId;
    @Column(name="before_value") String beforeValue; @Column(name="after_value") String afterValue;
    @Column(name="authorization_basis") String authorizationBasis; @Column(name="occurred_at") Instant occurredAt;
}

@Entity @Table(name="notification")
class Notification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="actor_id") Actor actor;
    String title; String body; @Column(name="target_path") String targetPath;
    @Column(name="read_at") Instant readAt; @Column(name="created_at") Instant createdAt;
}
