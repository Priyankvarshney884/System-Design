package com.systemdesign.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Standard API Response Wrapper                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Builder + Value Object
 *   Immutable response wrapper built via Lombok @Builder.
 *   All API endpoints return this type for consistency.
 *
 * SYSTEM DESIGN: Why a standard response envelope?
 *   - API clients (Angular) can always expect the same shape
 *   - Easy to add cross-cutting fields (requestId, version) later
 *   - Pagination metadata slots in naturally
 *
 * @JsonInclude(NON_NULL) — null fields are omitted from JSON output,
 * keeping responses clean (e.g. no "message": null on success responses).
 *
 * Usage:
 *   return ApiResponse.success(userDto);
 *   return ApiResponse.success("User created", userDto);
 *   return ApiResponse.error("Email already exists");
 *
 * @param <T> the type of the payload data
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** true = request succeeded, false = request failed */
    private final boolean success;

    /** Human-readable message (optional on success, required on error) */
    private final String message;

    /** The actual response payload — null on error responses */
    private final T data;

    /**
     * Pagination metadata — populated by list endpoints.
     * Null for single-resource endpoints.
     */
    private final PageMeta page;

    /** Server-side timestamp — helps correlate client logs with server logs */
    private final Instant timestamp;

    // ── Factory Methods ─────────────────────────────────────────

    /** Success with data only */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /** Success with custom message + data */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /** Success with message only (e.g. delete, logout) */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /** Paginated list response */
    public static <T> ApiResponse<T> page(T data, PageMeta pageMeta) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .page(pageMeta)
                .timestamp(Instant.now())
                .build();
    }

    // ── Nested: Pagination Metadata ──────────────────────────────

    /**
     * SYSTEM DESIGN: Cursor-based vs Offset pagination
     *   - Offset (page/size): simple but slow on large tables (OFFSET 10000 scans 10000 rows)
     *   - Cursor-based: fast regardless of depth, but requires a stable sort key
     *   We provide both here; cursor is preferred for high-traffic list APIs.
     */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PageMeta {
        private final int pageNumber;
        private final int pageSize;
        private final long totalElements;
        private final int totalPages;
        private final boolean last;
        private final String nextCursor;   // for cursor-based pagination
        private final String prevCursor;   // for cursor-based pagination
    }
}
