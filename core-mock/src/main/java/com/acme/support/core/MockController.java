package com.acme.support.core;

import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestController
class MockController {
 private final WebhookRepository repo;MockController(WebhookRepository repo){this.repo=repo;}
 @GetMapping("/health") Map<String,String> health(){return Map.of("status","up");}
 @PostMapping("/mock/webhooks") ResponseEntity<Map<String,String>> webhook(@RequestBody @jakarta.validation.Valid WebhookPayload payload,Authentication auth){String scope=auth.getName();String expected="webhook:"+payload.subscriptionRef();if(!scope.equals(expected))return ResponseEntity.status(403).body(Map.of("error","scope_mismatch"));WebhookDelivery d=new WebhookDelivery();d.subscriptionRef=payload.subscriptionRef();d.eventType=payload.eventType();d.scope=scope;d.payload=payload.payload();d.responseCode=202;d.attemptedAt=Instant.now();repo.save(d);return ResponseEntity.accepted().body(Map.of("deliveryId",String.valueOf(d.id),"status","accepted"));}
 record WebhookPayload(@NotBlank @Size(max=80)String subscriptionRef,@NotBlank @Size(max=80)String eventType,@NotBlank @Size(max=5000)String payload){}
}
