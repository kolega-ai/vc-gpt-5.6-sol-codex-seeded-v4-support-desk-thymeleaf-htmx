package com.acme.support.decision;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionTokenFilterTest {
 @Test void acceptsValidSignedUsernameAndLoadsActor()throws Exception{String secret="local-development-signing-key-change-me-32";ActorRepository repo=mock(ActorRepository.class);Actor actor=new Actor();actor.username="agent@desk.test";actor.role=Role.SUPPORT_AGENT;actor.enabled=true;when(repo.findByUsername(actor.username)).thenReturn(Optional.of(actor));String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(actor.username.getBytes(StandardCharsets.UTF_8));String payload=encoded+"."+Instant.now().plusSeconds(60).getEpochSecond();Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String token=payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));MockHttpServletRequest request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer "+token);new SessionTokenFilter(repo,secret).doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));assertNotNull(SecurityContextHolder.getContext().getAuthentication());assertSame(actor,SecurityContextHolder.getContext().getAuthentication().getPrincipal());SecurityContextHolder.clearContext();}
}
