package com.payflow.repository;

import com.payflow.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence gateway for the append-only {@link AuditEvent} trail.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
