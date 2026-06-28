package com.payflow.service;

/**
 * Records audit-trail entries (mentor feedback 22.c). Implementations persist asynchronously
 * so audit writes never sit on the request's critical path.
 */
public interface AuditService {

    /**
     * Records an audit event.
     *
     * @param action          the action performed (e.g. {@code USER_REGISTERED})
     * @param actor           who performed it (username or service name)
     * @param entityType      the affected entity type (e.g. {@code User})
     * @param entityReference the affected entity's external reference (nullable)
     * @param detail          human-readable detail (nullable)
     */
    void record(String action, String actor, String entityType, String entityReference, String detail);
}
