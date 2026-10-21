package com.acme.support.core;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="webhook_delivery")
class WebhookDelivery {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
 @Column(name="subscription_ref") String subscriptionRef; @Column(name="event_type") String eventType;
 String scope; @Column(columnDefinition="text") String payload; @Column(name="response_code") int responseCode;
 @Column(name="attempted_at") Instant attemptedAt;
}
interface WebhookRepository extends org.springframework.data.jpa.repository.JpaRepository<WebhookDelivery,Long>{}
