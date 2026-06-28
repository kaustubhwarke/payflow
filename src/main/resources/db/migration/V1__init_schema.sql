-- =====================================================================================
-- PayFlow initial schema (MySQL 8). Owned by Flyway; Hibernate runs in validate-only mode.
-- Monetary columns use DECIMAL(19,2) for exact arithmetic. UNIQUE indexes enforce
-- business invariants (unique UPI ID / references) and make lookups index seeks.
-- =====================================================================================

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    reference_id  VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    upi_id        VARCHAR(80)  NOT NULL,
    phone_number  VARCHAR(15)  NOT NULL,
    balance       DECIMAL(19, 2) NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_upi_id UNIQUE (upi_id),
    CONSTRAINT uk_users_reference_id UNIQUE (reference_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transactions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    reference_id    VARCHAR(40)  NOT NULL,
    sender_upi_id   VARCHAR(80)  NOT NULL,
    receiver_upi_id VARCHAR(80)  NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    note            VARCHAR(255) NULL,
    failure_reason  VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uk_transactions_reference_id UNIQUE (reference_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_transactions_sender ON transactions (sender_upi_id);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_upi_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);

CREATE TABLE audit_events (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    action           VARCHAR(60)   NOT NULL,
    actor            VARCHAR(120)  NOT NULL,
    entity_type      VARCHAR(60)   NOT NULL,
    entity_reference VARCHAR(80)   NULL,
    detail           VARCHAR(1000) NULL,
    trace_id         VARCHAR(32)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_audit_events PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_entity ON audit_events (entity_type, entity_reference);
CREATE INDEX idx_audit_trace ON audit_events (trace_id);
