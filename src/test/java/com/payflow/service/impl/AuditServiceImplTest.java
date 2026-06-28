package com.payflow.service.impl;

import com.payflow.entity.AuditEvent;
import com.payflow.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditServiceImpl} (AAA pattern).
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditEventRepository auditEventRepository;
    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void record_persistsAuditEventWithSuppliedFields() {
        // Arrange
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        auditService.record("USER_REGISTERED", "alice@okaxis", "User", "USR_1", "opening 5000");

        // Assert
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("USER_REGISTERED");
        assertThat(saved.getActor()).isEqualTo("alice@okaxis");
        assertThat(saved.getEntityType()).isEqualTo("User");
        assertThat(saved.getEntityReference()).isEqualTo("USR_1");
        assertThat(saved.getDetail()).isEqualTo("opening 5000");
    }

    @Test
    void record_swallowsRepositoryFailureSoBusinessFlowIsUnaffected() {
        // Arrange
        when(auditEventRepository.save(any(AuditEvent.class))).thenThrow(new RuntimeException("db down"));

        // Act + Assert: must not propagate
        assertThatCode(() -> auditService.record("A", "actor", "Type", "ref", "detail"))
                .doesNotThrowAnyException();
        verify(auditEventRepository).save(any(AuditEvent.class));
    }
}
