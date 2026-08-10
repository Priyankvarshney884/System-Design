package com.systemdesign.ecommerce.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Standard API Response Wrapper                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * DESIGN PATTERN: Builder + Value Object
 *   Immutable response wrapper built via manual static builder.
 *   Java records used for nested types (immutable by nature).
 *   All API endpoints return this type for consistency.
 *
 * NOTE: We use a manual static inner Builder here instead of Lombok @Builder
 * because this is a generic class <T> — Lombok @Builder has edge cases with
 * generic static factory methods that cause "cannot find symbol builder()" at
 * compile time when annotation processing order is not guaranteed.
 *
 * @JsonInclude(NON_NULL) — null fields are omitted from JSON output.
 *
 * @param <T> the type of the payload data
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse<T> {

    private final boolean  success;
    private final String   message;
    private final T        data;
    private final PageMeta page;
    private final Instant  timestamp;

    private ApiResponse(boolean success, String message, T data, PageMeta page) {
        this.success   = success;
        this.message   = message;
        this.data      = data;
        this.page      = page;
        this.timestamp = Instant.now();
    }

    // ── Getters (no Lombok — avoids annotation-processor ordering issues) ──
    public boolean  isSuccess()   { return success;   }
    public String   getMessage()  { return message;   }
    public T        getData()     { return data;      }
    public PageMeta getPage()     { return page;      }
    public Instant  getTimestamp(){ return timestamp; }

    // ── Factory Methods ──────────────────────────────────────────

    /** Success with data only */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null);
    }

    /** Success with custom message + data */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    /** Success with message only (e.g. delete, logout) */
    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    /** Paginated list response */
    public static <T> ApiResponse<T> page(T data, PageMeta pageMeta) {
        return new ApiResponse<>(true, null, data, pageMeta);
    }

    // ── Nested: Pagination Metadata ──────────────────────────────

    /**
     * SYSTEM DESIGN: Cursor-based vs Offset pagination
     *   - Offset (page/size): simple but slow on large tables
     *   - Cursor-based: fast regardless of depth, requires stable sort key
     * Java record: immutable, compact, auto-generates equals/hashCode/toString.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PageMeta(
        int    pageNumber,
        int    pageSize,
        long   totalElements,
        int    totalPages,
        boolean last,
        String nextCursor,
        String prevCursor
    ) {}
}
