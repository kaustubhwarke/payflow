package com.payflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only audit trail record (mentor feedback 22.c: audit trails).
 *
 * <p>Captures who did what, against which entity, and under which distributed-trace context.
 * Persisted asynchronously so audit writes never block the request path.</p>
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_entity", columnList = "entity_type,entity_reference"),
                @Index(name = "idx_audit_trace", columnList = "trace_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent extends BaseEntity {

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "actor", nullable = false, length = 120)
    private String actor;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_reference", length = 80)
    private String entityReference;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "trace_id", length = 32)
    private String traceId;
}
