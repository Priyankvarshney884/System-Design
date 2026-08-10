package com.systemdesign.ecommerce.module.auth.repository;

import com.systemdesign.ecommerce.module.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           User Repository — Spring Data JPA                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Repository Pattern
 *   Abstracts the persistence layer completely.
 *   Service classes call repository methods — never write SQL directly.
 *   Benefit: swap PostgreSQL for another DB → only repository changes.
 *
 * Spring Data JPA auto-generates SQL from method names:
 *   findByEmail → SELECT * FROM users WHERE email = ?
 *   No boilerplate SQL needed.
 *
 * SYSTEM DESIGN: Why @Query with fetch join?
 *   User.roles is EAGER but with a @ElementCollection, Hibernate
 *   may issue a separate query for roles (N+1 problem).
 *   JOIN FETCH forces a single SQL query with roles included.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email — used for login.
     * Returns Optional to force null-check at call site (no NullPointerException).
     * Hits the idx_users_email index → O(log n) lookup.
     */
    Optional<User> findByEmail(String email);

    /**
     * Check email existence without loading the full entity.
     * Used during registration — faster than findByEmail().isPresent()
     * because no object mapping is needed.
     */
    boolean existsByEmail(String email);

    /**
     * Fetch user WITH roles in a single query (avoids N+1).
     * Used on every authenticated request where roles are needed.
     *
     * SYSTEM DESIGN: Without JOIN FETCH:
     *   Query 1: SELECT * FROM users WHERE id = ?
     *   Query 2: SELECT * FROM user_roles WHERE user_id = ?
     * With JOIN FETCH:
     *   Query 1: SELECT u.*, r.* FROM users u LEFT JOIN user_roles r ON u.id = r.user_id WHERE u.id = ?
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdWithRoles(UUID id);

    /**
     * Find active user by email — used in JWT filter on every request.
     * Filters out soft-deleted and inactive accounts in the query.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email AND u.active = true AND u.deleted = false")
    Optional<User> findActiveByEmail(String email);
}
