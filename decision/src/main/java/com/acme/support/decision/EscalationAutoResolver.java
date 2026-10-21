package com.acme.support.decision;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name="desk.escalations.auto-resolve",havingValue="true",matchIfMissing=true)
class EscalationAutoResolver {
    private final EscalationRepository escalations;
    EscalationAutoResolver(EscalationRepository escalations){this.escalations=escalations;}
    @Scheduled(fixedDelay=90000,initialDelay=45000)
    void scan(){for(Escalation e:escalations.findAllByOrderByUpdatedAtDesc())if("requested".equals(e.status)){e.status="accepted";e.resolution="Accepted by the default routing rule; no lead reviewed this escalation.";e.updatedAt=Instant.now();escalations.save(e);}}
}
