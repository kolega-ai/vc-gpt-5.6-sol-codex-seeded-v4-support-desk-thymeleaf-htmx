package com.acme.support.channel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;

@Entity @Table(name="auth_user")
class AuthUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;String username;
 @Column(name="password_hash") String passwordHash;@Column(name="display_name") String displayName;String email;boolean enabled;
 @Column(name="role_hint") String roleHint;@Column(name="picture_url") String pictureUrl;@Column(name="credential_hash") String credentialHash;@Column(name="created_at") Instant createdAt;
 public String getUsername(){return username;}public String getDisplayName(){return displayName;}public String getEmail(){return email;}public String getRoleHint(){return roleHint;}public String getPictureUrl(){return pictureUrl;}public boolean isEnabled(){return enabled;}
}
@Entity @Table(name="password_reset")
class PasswordReset {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)Long id;@ManyToOne @JoinColumn(name="user_id")AuthUser user;
 @Column(name="token_hash")String tokenHash;@Column(name="expires_at")Instant expiresAt;@Column(name="used_at")Instant usedAt;@Column(name="created_at")Instant createdAt;
}
interface AuthUserRepository extends org.springframework.data.jpa.repository.JpaRepository<AuthUser,Long>{Optional<AuthUser>findByUsername(String username);Optional<AuthUser>findByEmail(String email);}
interface PasswordResetRepository extends org.springframework.data.jpa.repository.JpaRepository<PasswordReset,Long>{Optional<PasswordReset>findByTokenHashAndUsedAtIsNull(String hash);}
