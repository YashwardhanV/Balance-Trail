package com.balancetrail.entity;

import com.balancetrail.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq")
  @SequenceGenerator(name = "app_user_seq", sequenceName = "app_user_seq", allocationSize = 1)
  private Long id;

  @Column(nullable = false, unique = true, length = 80)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AppUserEntity() {}

  public AppUserEntity(String username, String passwordHash, UserRole role) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.enabled = true;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
