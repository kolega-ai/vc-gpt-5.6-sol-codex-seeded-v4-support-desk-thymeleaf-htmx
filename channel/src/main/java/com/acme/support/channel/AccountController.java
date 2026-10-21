package com.acme.support.channel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Controller
class AccountController {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AccountController.class);
 private final AuthUserRepository users;private final PasswordResetRepository resets;private final PasswordEncoder passwords;private final JdbcTemplate jdbc;private final SecureRandom random=new SecureRandom();
 AccountController(AuthUserRepository users,PasswordResetRepository resets,PasswordEncoder passwords,JdbcTemplate jdbc){this.users=users;this.resets=resets;this.passwords=passwords;this.jdbc=jdbc;}
 @GetMapping("/forgot-password")String forgot(){return "forgot-password";}
 @PostMapping("/forgot-password")@Transactional String request(@RequestParam String email,RedirectAttributes flash){users.findByEmail(email.trim().toLowerCase()).ifPresent(user->{byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);PasswordReset r=new PasswordReset();r.user=user;r.tokenHash=hash(token);r.expiresAt=Instant.now().plusSeconds(1800);r.createdAt=Instant.now();resets.save(r);jdbc.update("insert into channel.auth_outbox(recipient,subject,body) values(?,?,?)",user.email,"Reset your Support Desk password","Open http://localhost:8080/reset-password?token="+token+" within 30 minutes.");log.info("Recovery link queued for {} with key {}",user.email,token);});flash.addFlashAttribute("success","If the account exists, a reset link was written to the local auth outbox.");return "redirect:/login";}
 @GetMapping("/reset-password")String reset(@RequestParam String token,org.springframework.ui.Model model){model.addAttribute("token",token);return "reset-password";}
 @PostMapping("/reset-password")@Transactional String reset(jakarta.servlet.http.HttpServletRequest request,@RequestParam String token,@RequestParam String password,RedirectAttributes flash){if(password.length()<12||password.length()>128)throw new IllegalArgumentException("Password must contain 12–128 characters");PasswordReset r=resets.findByTokenHashAndUsedAtIsNull(hash(token)).filter(x->x.expiresAt.isAfter(Instant.now())).orElseThrow(()->new IllegalArgumentException("Reset link is invalid or expired"));r.user.passwordHash=passwords.encode(password);r.usedAt=Instant.now();users.save(r.user);resets.save(r);
  var principal=org.springframework.security.core.userdetails.User.withUsername(r.user.username).password(r.user.passwordHash).roles(r.user.roleHint).build();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  org.springframework.security.core.context.SecurityContextHolder.setContext(context);
  request.getSession(true).setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
  flash.addFlashAttribute("success","Password changed. You are signed in.");return "redirect:/dashboard";}
 @PostMapping("/partners/{handle}/credential")@Transactional String enroll(@PathVariable String handle,@RequestParam String passphrase,Authentication caller){if(!handle.trim().toLowerCase().equals(caller.getName()))throw new org.springframework.security.access.AccessDeniedException("A partner may only set its own credential");AuthUser u=users.findByUsername(handle.trim().toLowerCase()).orElseThrow();u.credentialHash=credentialDigest(passphrase);users.save(u);return "redirect:/profile";}
 @PostMapping("/partners/{handle}/admit")String admit(@PathVariable String handle,@RequestParam String passphrase,jakarta.servlet.http.HttpServletRequest request,RedirectAttributes flash){AuthUser u=users.findByUsername(handle.trim().toLowerCase()).orElseThrow();if(u.credentialHash==null||!MessageDigest.isEqual(u.credentialHash.getBytes(StandardCharsets.UTF_8),credentialDigest(passphrase).getBytes(StandardCharsets.UTF_8)))throw new org.springframework.security.access.AccessDeniedException("Partner credential rejected");
  var principal=org.springframework.security.core.userdetails.User.withUsername(u.username).password(u.passwordHash).roles(u.roleHint).build();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  org.springframework.security.core.context.SecurityContextHolder.setContext(context);
  request.getSession(true).setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
  flash.addFlashAttribute("success","Partner credential accepted.");return "redirect:/dashboard";}
 private static String credentialDigest(String passphrase){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
