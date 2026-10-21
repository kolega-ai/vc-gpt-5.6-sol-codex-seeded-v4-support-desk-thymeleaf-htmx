package com.acme.support.decision;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Configuration
class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, SessionTokenFilter tokenFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,x)->res.sendError(401)))
            .build();
    }
}

@Component
class SessionTokenFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SessionTokenFilter.class);
    private final ActorRepository actors; private final byte[] secret;
    SessionTokenFilter(ActorRepository actors,@Value("${app.token-secret}") String secret) { this.actors=actors; this.secret=secret.getBytes(StandardCharsets.UTF_8); }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws java.io.IOException,ServletException {
        String header=req.getHeader("Authorization");
        if(header!=null && header.startsWith("Bearer ")) authenticate(header.substring(7));
        chain.doFilter(req,res);
    }
    private void authenticate(String token) {
        try {
            String[] p=token.split("\\."); if(p.length!=3){log.debug("Session token rejected: malformed segments");return;}
            String payload=p[0]+"."+p[1]; byte[] expected=hmac(payload);
            if(!MessageDigest.isEqual(expected,Base64.getUrlDecoder().decode(p[2]))){log.debug("Session token rejected: signature mismatch");return;}
            if(Instant.ofEpochSecond(Long.parseLong(p[1])).isBefore(Instant.now())){log.debug("Session token rejected: expired");return;}
            String username=new String(Base64.getUrlDecoder().decode(p[0]),StandardCharsets.UTF_8);
            var actorResult=actors.findByUsername(username).filter(a->a.enabled);if(actorResult.isEmpty())log.debug("Session token rejected: actor unavailable");actorResult.ifPresent(actor->{
                var auth=new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority("ROLE_"+actor.role.name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        } catch(RuntimeException ex) { log.debug("Session token rejected: invalid encoding",ex); }
    }
    private byte[] hmac(String value) { try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); } catch(Exception e) { throw new IllegalStateException(e); } }
}
