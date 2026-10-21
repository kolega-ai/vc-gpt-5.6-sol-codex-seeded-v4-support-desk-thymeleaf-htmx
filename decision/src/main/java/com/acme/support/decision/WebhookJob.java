package com.acme.support.decision;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Component
class WebhookJob {
 private final JdbcTemplate jdbc;private final RestClient client;private final Algorithm algorithm;
 WebhookJob(JdbcTemplate jdbc,RestClient.Builder builder,@Value("${app.core-url}")String coreUrl,@Value("${app.scope-secret}")String secret){this.jdbc=jdbc;this.client=builder.baseUrl(coreUrl).build();this.algorithm=Algorithm.HMAC256(secret);}
 @Scheduled(fixedDelay=60000,initialDelay=30000)
 void deliver(){jdbc.query("select id,event_type,target_url from decision.webhook_subscription where active=true",rs->{while(rs.next()){String id=String.valueOf(rs.getLong(1));String token=JWT.create().withIssuer("support-decision").withClaim("scope","webhook:"+id).withIssuedAt(Date.from(Instant.now())).withExpiresAt(Date.from(Instant.now().plusSeconds(60))).sign(algorithm);int code=0;String target=rs.getString(3);try{client.post().uri(java.net.URI.create(target)).contentType(MediaType.APPLICATION_JSON).header("Authorization","Bearer "+token).body(Map.of("subscriptionRef",id,"eventType",rs.getString(2),"payload","{\"heartbeat\":true}")).retrieve().toBodilessEntity();code=202;}catch(Exception ignored){code=503;}jdbc.update("insert into decision.outbox(channel,recipient,subject,body,status,sent_at) values(?,?,?,?,?,?)","WEBHOOK","subscription:"+id,rs.getString(2),"scheduled local delivery",code==202?"SENT":"FAILED",code==202?Instant.now():null);}});}
}
