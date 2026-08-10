package com.systemdesign.ecommerce.common.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║               Base Entity — All JPA Entities Extend This     ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Template Method
 *   All entities share this common structure. Sub-entities only
 *   define their own fields — the auditing lifecycle is handled here.
 *
 * SYSTEM DESIGN: Why UUID over auto-increment Long?
 *   - UUID can be generated client-side before DB insert (no round-trip)
 *   - Safe to expose in URLs — no sequential enumeration attack
 *   - Works naturally with database sharding across nodes
 *
 * NOTE: Lombok removed — explicit getters to avoid annotation-processor
 * dependency. BaseEntity is the foundation of every entity; it must
 * compile cleanly without any annotation processor configuration.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Automatically set to NOW() on INSERT. Never updated after that.
     * SYSTEM DESIGN: every record needs a creation timestamp for audit trails.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Automatically updated to NOW() on every UPDATE.
     * Used for optimistic locking checks and cache invalidation.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Soft-delete flag.
     * SYSTEM DESIGN: Never hard-delete records in production.
     * Reasons: audit trail, event sourcing replay, accidental-delete recovery.
     * All queries should filter WHERE is_deleted = false by default.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    // ── Getters ──────────────────────────────────────────────────
    public UUID          getId()        { return id;        }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean       isDeleted()    { return deleted;   }

    // ── Setters ──────────────────────────────────────────────────
    public void setId(UUID id)                       { this.id = id;               }
    public void setCreatedAt(LocalDateTime createdAt){ this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt){ this.updatedAt = updatedAt; }
    public void setDeleted(boolean deleted)          { this.deleted = deleted;     }
}
