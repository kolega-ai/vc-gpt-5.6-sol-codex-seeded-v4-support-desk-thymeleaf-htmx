package com.acme.support.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import java.io.IOException;
import java.util.List;

@Configuration
class CoreSecurity {
    @Bean SecurityFilterChain security(HttpSecurity http,ScopeTokenFilter filter)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/health").permitAll().anyRequest().authenticated()).addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();}
}

@Component
class ScopeTokenFilter extends OncePerRequestFilter {
    private final Algorithm algorithm;ScopeTokenFilter(@Value("${app.scope-secret}")String secret){algorithm=Algorithm.HMAC256(secret);}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String h=req.getHeader("Authorization");if(h!=null&&h.startsWith("Bearer "))try{var jwt=JWT.require(algorithm).withIssuer("support-decision").build().verify(h.substring(7));String scope=jwt.getClaim("scope").asString();if(scope!=null&&scope.startsWith("webhook:")){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(scope,null,List.of(new SimpleGrantedAuthority("SCOPE_WEBHOOK"))));}}catch(Exception ignored){}chain.doFilter(req,res);}
}
