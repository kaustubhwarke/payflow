package com.payflow.service.impl;

import com.payflow.entity.AuditEvent;
import com.payflow.filter.TracingFilter;
import com.payflow.repository.AuditEventRepository;
import com.payflow.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asynchronous, fire-and-forget {@link AuditService}. Each record runs in its own transaction
 * ({@code REQUIRES_NEW}) on the async executor, so an audit failure can never roll back or
 * block the originating business transaction.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String actor, String entityType, String entityReference, String detail) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .action(action)
                    .actor(actor)
                    .entityType(entityType)
                    .entityReference(entityReference)
                    .detail(detail)
                    .traceId(MDC.get(TracingFilter.TRACE_ID_KEY))
                    .build();
            auditEventRepository.save(event);
        } catch (RuntimeException ex) {
            log.error("Failed to persist audit event action={} entityReference={}", action, entityReference, ex);
        }
    }
}
