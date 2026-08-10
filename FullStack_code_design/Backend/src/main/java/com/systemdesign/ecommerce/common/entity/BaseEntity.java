package com.systemdesign.ecommerce.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
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
 *   - Trade-off: 16 bytes vs 8 bytes, slightly larger indexes
 *
 * SYSTEM DESIGN: Why audit fields on every entity?
 *   At scale you always need to answer: "When was this created? Who changed it?"
 *   Adds createdAt / updatedAt automatically via Spring JPA Auditing.
 */
@Getter
@Setter
@MappedSuperclass                        // Not a table itself — shared by all entities
@EntityListeners(AuditingEntityListener.class)  // Triggers @CreatedDate / @LastModifiedDate
public abstract class BaseEntity {

    /**
     * Primary key as UUID.
     * GenerationType.UUID — Spring/Hibernate 6+ generates it before INSERT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Automatically set to NOW() on INSERT. Never updated after that.
     * updatable=false ensures it stays immutable.
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
     * SYSTEM DESIGN: Never hard-delete records in production systems.
     * Reasons: audit trail, event sourcing replay, accidental-delete recovery.
     * All queries filter WHERE is_deleted = false by default.
     */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
