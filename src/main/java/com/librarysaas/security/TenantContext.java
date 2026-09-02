package com.librarysaas.security;

/**
 * Simple thread-local tenant context. Populated by a request filter.
 */
public final class TenantContext {
    private static final ThreadLocal<Long> organizationId = new ThreadLocal<>();
    private static final ThreadLocal<Long> libraryId = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganizationId(Long id) {
        organizationId.set(id);
    }

    public static Long getOrganizationId() {
        return organizationId.get();
    }

    public static void setLibraryId(Long id) {
        libraryId.set(id);
    }

    public static Long getLibraryId() {
        return libraryId.get();
    }

    public static void clear() {
        organizationId.remove();
        libraryId.remove();
    }
}
