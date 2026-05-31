# PayFlow — Database Schema

> Table-by-table reference derived from
> `src/main/resources/db/migration/V1__init_schema.sql` (MySQL 8, InnoDB,
> `utf8mb4` / `utf8mb4_unicode_ci`). The schema is **owned by Flyway**; Hibernate
> runs in `ddl-auto: validate` mode and never alters it.

## Conventions

- **Money** is `DECIMAL(19,2)` for exact arithmetic (mapped from Java `BigDecimal`).
- **Timestamps** are `DATETIME(6)` (microsecond precision), populated by JPA auditing
  (`@CreatedDate` / `@LastModifiedDate` on `BaseEntity`).
- **`version`** is the optimistic-lock counter (`@Version`), incremented by Hibernate
  on every update, defaulting to `0`.
- **Surrogate key** `id` is `BIGINT AUTO_INCREMENT` (Hibernate `IDENTITY`); the
  externally exposed identifier is the opaque `reference_id` (ULID).
- **Enums** persist as `VARCHAR` strings (`@Enumerated(EnumType.STRING)`).

## Table: `users`

| Column | Type | Null | Default | Constraint / Notes |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | auto | PK `pk_users`, `AUTO_INCREMENT`. |
| `reference_id` | `VARCHAR(40)` | NO | — | UNIQUE `uk_users_reference_id`; `USR_<ULID>`. |
| `name` | `VARCHAR(120)` | NO | — | Sanitised free text. |
| `upi_id` | `VARCHAR(80)` | NO | — | UNIQUE `uk_users_upi_id`; stored lower-cased. |
| `phone_number` | `VARCHAR(15)` | NO | — | 10-digit Indian mobile (validated). |
| `balance` | `DECIMAL(19,2)` | NO | — | Wallet balance, ≥ 0. |
| `currency` | `VARCHAR(3)` | NO | — | Enum `Currency` (`INR`). |
| `created_at` | `DATETIME(6)` | NO | — | Auditing. |
| `updated_at` | `DATETIME(6)` | NO | — | Auditing. |
| `version` | `BIGINT` | NO | `0` | Optimistic lock. |

**Indexes:** PK on `id`; UNIQUE on `upi_id` and `reference_id`. The unique `upi_id`
index enforces the business rule *and* turns `findByUpiId` into an index seek.

## Table: `transactions`

| Column | Type | Null | Default | Constraint / Notes |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | auto | PK `pk_transactions`, `AUTO_INCREMENT`. |
| `reference_id` | `VARCHAR(40)` | NO | — | UNIQUE `uk_transactions_reference_id`; `TXN_<ULID>`. |
| `sender_upi_id` | `VARCHAR(80)` | NO | — | Logical reference to `users.upi_id` (no FK). |
| `receiver_upi_id` | `VARCHAR(80)` | NO | — | Logical reference to `users.upi_id` (no FK). |
| `amount` | `DECIMAL(19,2)` | NO | — | Transfer amount, > 0. |
| `currency` | `VARCHAR(3)` | NO | — | Enum `Currency`. |
| `status` | `VARCHAR(20)` | NO | — | Enum `TransactionStatus` (`PENDING`/`COMPLETED`/`FAILED`/`REVERSED`). |
| `note` | `VARCHAR(255)` | YES | NULL | Optional sanitised note. |
| `failure_reason` | `VARCHAR(255)` | YES | NULL | Populated when `status = FAILED`. |
| `created_at` | `DATETIME(6)` | NO | — | Auditing; used for newest-first history sort. |
| `updated_at` | `DATETIME(6)` | NO | — | Auditing. |
| `version` | `BIGINT` | NO | `0` | Optimistic lock. |

**Indexes:** PK on `id`; UNIQUE on `reference_id`; secondary indexes
`idx_transactions_sender (sender_upi_id)`, `idx_transactions_receiver
(receiver_upi_id)`, `idx_transactions_created_at (created_at)` — backing the paginated
history query and time-ordered sorting.

## Table: `audit_events`

| Column | Type | Null | Default | Constraint / Notes |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | auto | PK `pk_audit_events`, `AUTO_INCREMENT`. |
| `action` | `VARCHAR(60)` | NO | — | e.g. `USER_REGISTERED`, `TRANSACTION_COMPLETED`. |
| `actor` | `VARCHAR(120)` | NO | — | Username or service name. |
| `entity_type` | `VARCHAR(60)` | NO | — | e.g. `User`, `Transaction`. |
| `entity_reference` | `VARCHAR(80)` | YES | NULL | Affected entity's external reference. |
| `detail` | `VARCHAR(1000)` | YES | NULL | Human-readable detail. |
| `trace_id` | `VARCHAR(32)` | YES | NULL | Originating W3C trace id. |
| `created_at` | `DATETIME(6)` | NO | — | Auditing. |
| `updated_at` | `DATETIME(6)` | NO | — | Auditing. |
| `version` | `BIGINT` | NO | `0` | Optimistic lock. |

**Indexes:** PK on `id`; `idx_audit_entity (entity_type, entity_reference)` (composite,
for "all events for entity X"); `idx_audit_trace (trace_id)` (correlate a request's
audit trail by trace).

## JPA naming convention (camelCase → snake_case)

Hibernate's default physical naming strategy maps camelCase Java field names to
snake_case column names. PayFlow also pins names explicitly with `@Column`/`@Table`,
so the mapping is unambiguous:

| Java field | Column |
| --- | --- |
| `upiId` | `upi_id` |
| `phoneNumber` | `phone_number` |
| `referenceId` | `reference_id` |
| `senderUpiId` | `sender_upi_id` |
| `receiverUpiId` | `receiver_upi_id` |
| `failureReason` | `failure_reason` |
| `entityType` | `entity_type` |
| `entityReference` | `entity_reference` |
| `traceId` | `trace_id` |
| `createdAt` / `updatedAt` | `created_at` / `updated_at` |

## Custom `@Query` (JPQL) examples

Both custom queries are **JPQL** (portable, validated against the entity model at
startup) rather than native SQL.

`UserRepository` — users with balance above a threshold (paginated):

```java
@Query("SELECT u FROM User u WHERE u.balance > :threshold")
Page<User> findUsersWithBalanceAbove(@Param("threshold") BigDecimal threshold,
                                     Pageable pageable);
```

`TransactionRepository` — history where the party is sender OR receiver (paginated):

```java
@Query("SELECT t FROM Transaction t "
     + "WHERE t.senderUpiId = :upiId OR t.receiverUpiId = :upiId")
Page<Transaction> findHistoryForUpiId(@Param("upiId") String upiId, Pageable pageable);
```

The derived query `findByUpiId(String)` is parsed by Spring Data into
`SELECT u FROM User u WHERE u.upiId = ?1`; the bound parameter (`?1`) is supplied
safely as a prepared-statement parameter, preventing SQL injection.
