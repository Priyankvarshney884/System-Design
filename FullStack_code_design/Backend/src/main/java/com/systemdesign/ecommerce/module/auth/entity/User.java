package com.systemdesign.ecommerce.module.auth.entity;

import com.systemdesign.ecommerce.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           User Entity — JPA + PostgreSQL                     ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: Why PostgreSQL for users?
 *   Strict relational constraints (unique email, FK to orders),
 *   ACID guarantees for password/security changes.
 *
 * NOTE: Lombok @Getter / @Builder removed — using explicit accessors
 * and a static builder to avoid annotation-processing dependency.
 * This ensures the project compiles cleanly from command line AND
 * in IntelliJ regardless of Lombok plugin state.
 */
@Entity
@Table(
    name = "users",
    schema = "user_domain",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    },
    indexes = {
        @Index(name = "idx_users_email", columnList = "email")
    }
)
public class User extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    /**
     * BCrypt hashed password — NEVER store plain text.
     * BCrypt cost 12 = intentionally slow to resist brute force.
     */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    /**
     * SYSTEM DESIGN: RBAC — roles drive which endpoints are accessible.
     * EAGER fetch: roles are always needed, small set, safe to load eagerly.
     */
    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name  = "user_roles",
        schema = "user_domain",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role")
    private Set<Role> roles = new HashSet<>();

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Account disabled → cannot login even with valid password.
     * Used for: admin ban, email not verified, fraud detection.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ── Constructors ─────────────────────────────────────────────

    public User() {}

    // ── Getters ──────────────────────────────────────────────────

    public String      getName()         { return name; }
    public String      getEmail()        { return email; }
    public String      getPasswordHash() { return passwordHash; }
    public Set<Role>   getRoles()        { return roles; }
    public String      getAvatarUrl()    { return avatarUrl; }
    public boolean     isActive()        { return active; }

    // ── Setters ──────────────────────────────────────────────────

    public void setName(String name)               { this.name = name; }
    public void setEmail(String email)             { this.email = email; }
    public void setPasswordHash(String hash)       { this.passwordHash = hash; }
    public void setRoles(Set<Role> roles)          { this.roles = roles; }
    public void setAvatarUrl(String avatarUrl)     { this.avatarUrl = avatarUrl; }
    public void setActive(boolean active)          { this.active = active; }

    // ── Domain Methods ───────────────────────────────────────────

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }

    /** Normalize email before persisting */
    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (this.email != null) {
            this.email = this.email.toLowerCase().trim();
        }
    }

    // ── Static Builder ───────────────────────────────────────────
    // Manual builder replaces Lombok @Builder — no annotation processor needed

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String    name;
        private String    email;
        private String    passwordHash;
        private Set<Role> roles        = new HashSet<>();
        private String    avatarUrl;
        private boolean   active       = true;

        public Builder name(String val)         { this.name = val;         return this; }
        public Builder email(String val)        { this.email = val;        return this; }
        public Builder passwordHash(String val) { this.passwordHash = val; return this; }
        public Builder roles(Set<Role> val)     { this.roles = val;        return this; }
        public Builder avatarUrl(String val)    { this.avatarUrl = val;    return this; }
        public Builder active(boolean val)      { this.active = val;       return this; }

        public User build() {
            User u = new User();
            u.name         = this.name;
            u.email        = this.email;
            u.passwordHash = this.passwordHash;
            u.roles        = this.roles;
            u.avatarUrl    = this.avatarUrl;
            u.active       = this.active;
            return u;
        }
    }

    // ── Roles Enum ───────────────────────────────────────────────

    public enum Role {
        USER,   // default — browse, buy
        ADMIN,  // manage products, orders, users
        VENDOR  // manage own product listings
    }
}
