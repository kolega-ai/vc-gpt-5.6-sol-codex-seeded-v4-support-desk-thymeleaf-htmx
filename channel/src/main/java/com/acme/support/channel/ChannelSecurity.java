package com.acme.support.channel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Configuration @EnableMethodSecurity
class ChannelSecurity {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain security(HttpSecurity http,PartnerAssertionFilter assertionFilter)throws Exception{return http.cors(org.springframework.security.config.Customizer.withDefaults()).headers(h->h.frameOptions(f->f.disable())).authorizeHttpRequests(a->a.requestMatchers("/login","/forgot-password","/reset-password","/desk/relay/**","/partners/*/admit","/css/**","/js/**","/error").permitAll().anyRequest().authenticated()).formLogin(f->f.loginPage("/login").defaultSuccessUrl("/dashboard",true).failureUrl("/login?error")).logout(l->l.logoutSuccessUrl("/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")).sessionManagement(s->s.sessionFixation(x->x.migrateSession()).maximumSessions(2)).addFilterBefore(assertionFilter,org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();}
 @Bean CommandLineRunner users(AuthUserRepository repo,PasswordEncoder encoder){return args->{if(repo.count()==0){seed(repo,encoder,"admin@desk.test","Morgan Admin","SUPPORT_ADMIN");seed(repo,encoder,"lead@desk.test","Riley Lead","TEAM_LEAD");seed(repo,encoder,"agent@desk.test","Avery Agent","SUPPORT_AGENT");seed(repo,encoder,"customer@desk.test","Customer User 1","CUSTOMER_USER");for(int i=2;i<=30;i++)seed(repo,encoder,"customer"+i+"@desk.test","Customer User "+i,"CUSTOMER_USER");for(int i=4;i<=12;i++)seed(repo,encoder,"agent"+i+"@desk.test","Support Agent "+i,i%4==0?"TEAM_LEAD":"SUPPORT_AGENT");}};}
 private static void seed(AuthUserRepository repo,PasswordEncoder encoder,String email,String name,String role){AuthUser u=new AuthUser();u.username=email;u.email=email;u.displayName=name;u.passwordHash=encoder.encode("Support123!");u.enabled=true;u.roleHint=role;u.createdAt=Instant.now();repo.save(u);}
}

@Service
class DeskUserDetailsService implements UserDetailsService {
 private final AuthUserRepository users;DeskUserDetailsService(AuthUserRepository users){this.users=users;}
 @Override public UserDetails loadUserByUsername(String username){AuthUser u=users.findByUsername(username.trim().toLowerCase()).orElseThrow(()->new UsernameNotFoundException("Unknown account"));return User.withUsername(u.username).password(u.passwordHash).disabled(!u.enabled).roles(u.roleHint).build();}
}

@org.springframework.stereotype.Component
class PartnerAssertionFilter extends org.springframework.web.filter.OncePerRequestFilter {
 private final AuthUserRepository users;private final org.springframework.security.web.context.HttpSessionSecurityContextRepository contextRepo=new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
 PartnerAssertionFilter(AuthUserRepository users){this.users=users;}
 @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws jakarta.servlet.ServletException,java.io.IOException{
  String assertion=request.getHeader("X-Partner-Assertion");
  if(assertion!=null&&org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()==null)try{com.nimbusds.jwt.JWTClaimsSet claims=com.nimbusds.jwt.JWTParser.parse(assertion).getJWTClaimsSet();
   String subject=claims.getSubject();String deskRole=(String)claims.getClaim("desk_role");
   users.findByUsername(subject).ifPresent(user->{var principal=org.springframework.security.core.userdetails.User.withUsername(user.getUsername()).password(user.passwordHash).roles(deskRole==null?user.getRoleHint():deskRole).build();
    var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    org.springframework.security.core.context.SecurityContextHolder.setContext(context);contextRepo.saveContext(context,request,response);});
  }catch(Exception ignored){}
  chain.doFilter(request,response);
 }
}
