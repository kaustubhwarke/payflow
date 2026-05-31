# PayFlow — Low-Level Design (LLD)

> Per-package class responsibilities, the transfer algorithm step by step, the
> concurrency design, ID generation, and the exception→HTTP mapping table.

## 1. Package & class responsibilities

### `entity`
| Class | Responsibility |
| --- | --- |
| `BaseEntity` | `@MappedSuperclass` with surrogate `id` (`IDENTITY`), `createdAt`/`updatedAt` (`@CreatedDate`/`@LastModifiedDate`), and `@Version` optimistic-lock column. `@EntityListeners(AuditingEntityListener)`. |
| `User` | Wallet owner: `referenceId`, `name`, `upiId` (unique), `phoneNumber`, `balance` (`BigDecimal`), `currency`. |
| `Transaction` | Immutable transfer record: `referenceId`, `senderUpiId`, `receiverUpiId`, `amount`, `currency`, `status`, `note`, `failureReason`. |
| `AuditEvent` | Append-only trail: `action`, `actor`, `entityType`, `entityReference`, `detail`, `traceId`. |

### `enums`
`Role` (`USER`, `ADMIN`), `TransactionStatus` (`PENDING`, `COMPLETED`, `FAILED`,
`REVERSED`), `Currency` (`INR`). All persisted via `@Enumerated(EnumType.STRING)`.

### `repository`
| Interface | Key methods |
| --- | --- |
| `UserRepository` | `findByUpiId`, `findByReferenceId`, `existsByUpiId`, `@Query findUsersWithBalanceAbove(threshold, Pageable)`. |
| `TransactionRepository` | `findByReferenceId`, `@Query findHistoryForUpiId(upiId, Pageable)` (sender OR receiver). |
| `AuditEventRepository` | Plain `JpaRepository`. |

### `dto`
`request.CreateUserRequest`, `request.SendMoneyRequest` (validated records);
`response.UserResponse`, `response.TransactionResponse`, `response.PagedResponse<T>`
(`from(Page)` adapter so Spring's `PageImpl` never leaks).

### `mapper`
`UserMapper`, `TransactionMapper` — MapStruct interfaces (`defaultComponentModel =
spring`), compile-time entity→response mapping, no reflection.

### `service` / `service.impl`
| Class | Responsibility |
| --- | --- |
| `UserService` / `UserServiceImpl` | Register (uniqueness check, sanitise, ULID ref, audit), lookups (by reference / UPI, the latter `@Cacheable`), paged listing, balance-threshold query. |
| `TransactionService` / `TransactionServiceImpl` | Orchestrate transfer (validate → lock → executor → evict cache → publish event → audit), lookups, paged history. |
| `TransferExecutor` | The `@Transactional` debit/credit + ledger write — a separate bean so the commit boundary sits **inside** the locks. |
| `AuditService` / `AuditServiceImpl` | `@Async` + `REQUIRES_NEW` audit writes; failures logged, never propagated. |

### `concurrency`
`StripedLockRegistry` — 256 `ReentrantLock` stripes; `executeWithLock(key, …)` and
`executeWithLocks(a, b, …)` (ascending-index acquisition).

### `messaging` / `event`
`TransactionEventPublisher` (interface, DIP), `RabbitTransactionEventPublisher`
(`@Async`, routing key by status), `TransactionEventConsumer` (`@RabbitListener` →
audit). `event.TransactionEvent` is an immutable record carrying the `traceId`.

### `filter`
`TracingFilter` (`HIGHEST_PRECEDENCE`), `RateLimitingFilter`
(`HIGHEST_PRECEDENCE + 10`, only `/api/**`).

### `config`
`SecurityConfig`, `KeycloakRealmRoleConverter`, `RabbitConfig`, `CacheConfig`,
`AsyncConfig`, `AuditingConfig`, `OpenApiConfig`, `PayflowProperties`.

### `util`
`IdentifierGenerator` (ULID business refs), `TraceIdentifierFactory` (W3C ids),
`InputSanitizer` (NFKC normalise, strip controls/angle brackets).

### `exception`
`ErrorCode` enum, `PayflowException` (abstract root) + concrete subtypes,
`GlobalExceptionHandler` (`@RestControllerAdvice`).

## 2. The transfer algorithm, step by step

`TransactionServiceImpl.sendMoney(SendMoneyRequest)`:

1. **Normalise & validate (cheap, no DB).** Lower-case both UPI IDs. If
   `sender == receiver`, throw `InvalidTransferException` (→ 400). (Structural
   validation — non-blank, `handle@provider` pattern, amount > 0, ≤ 2 decimals — was
   already enforced by Bean Validation at the controller.)
2. **Acquire per-account locks in deadlock-free order.**
   `lockRegistry.executeWithLocks(sender, receiver, () -> …)` computes the stripe
   index for each key (`floorMod(hashCode, 256)`) and locks the **lower index
   first**, then the higher. If both map to the same stripe, only one lock is taken.
3. **Atomic ledger write (inside the locks).** The lambda calls the proxied,
   `@Transactional` `TransferExecutor.execute(request)`, which:
   - loads sender and receiver via `findByUpiId` (else `ResourceNotFoundException`
     → 404);
   - if `sender.balance < amount`, throws `InsufficientBalanceException` (→ 422);
   - `sender.balance -= amount`, `receiver.balance += amount`;
   - builds and saves a `Transaction` with a fresh `TXN_…` reference and status
     `COMPLETED` (note sanitised).
4. **Commit before unlock.** When `execute(...)` returns, its transaction has already
   committed (the `@Transactional` proxy boundary is crossed *before* control returns
   to `executeWithLocks`, which only then releases the stripes). Any
   lost-update race that slipped past the app lock is still caught by `@Version` and
   surfaces as `CONCURRENCY_CONFLICT` (→ retryable 409).
5. **Post-commit side-effects (outside the locks, non-blocking).**
   - Evict `usersByUpiId` for both parties so cached balances are never stale.
   - Publish a `TransactionEvent` `@Async` to RabbitMQ (failure logged, swallowed).
   - Record a `TRANSACTION_COMPLETED` audit entry `@Async`.
6. **Return** the mapped `TransactionResponse`.

## 3. Concurrency control design

- **Why striped locks.** A single global lock would serialise *all* transfers; pure
  DB row locks alone don't coordinate the two-row debit/credit cleanly across the
  app. Striped locks give per-account mutual exclusion with bounded memory (256
  fixed locks): disjoint accounts proceed in parallel, only same-account contenders
  block.
- **Deadlock avoidance.** Two-account operations always lock the **lower stripe index
  first**, giving a total ordering so cyclic waits are impossible.
- **Commit-before-release invariant.** The `@Transactional` write is a *separate
  bean* (`TransferExecutor`) called inside the locks. This guarantees the DB commit
  precedes lock release — a second transfer for the same account cannot observe a
  half-applied balance.
- **Optimistic locking backstop.** `@Version` on `BaseEntity` means concurrent
  updates from *different instances* (which don't share the in-JVM stripes) are still
  detected by Hibernate and rejected, then mapped to a retryable 409.

## 4. Identifier generation

| Identifier | Source | Form |
| --- | --- | --- |
| **Primary key** (`id`) | Hibernate `@GeneratedValue(strategy = IDENTITY)` → MySQL `AUTO_INCREMENT` | internal `BIGINT`, never exposed over the API. |
| **Business reference** (`referenceId`) | `IdentifierGenerator.newReference(prefix)` | `USR_<ULID>` / `TXN_<ULID>` — 26-char Crockford Base32 ULID (48-bit time + 80-bit random), lexicographically sortable, opaque, leaks no sequence. |
| **Trace / span ids** | `TraceIdentifierFactory` | 32-hex traceId (128-bit) / 16-hex spanId (64-bit), W3C-compliant, guaranteed non-zero. |

Separating the internal surrogate key from the public reference means the API never
reveals row counts or ordering, and references stay stable and globally unique.

## 5. Exception → HTTP mapping

Every error response is an RFC 7807 `ProblemDetail` with `type`, `title`, `status`,
`detail`, `instance`, plus custom properties `errorCode`, `traceId`, `timestamp`
(and `errors` for body validation). Built by `GlobalExceptionHandler`.

| Exception | `ErrorCode` | HTTP status | Trigger |
| --- | --- | --- | --- |
| `ResourceNotFoundException.user(...)` | `USER_NOT_FOUND` | 404 | User reference/UPI not found. |
| `ResourceNotFoundException.transaction(...)` | `TRANSACTION_NOT_FOUND` | 404 | Transaction reference not found. |
| `DuplicateResourceException.upiId(...)` | `DUPLICATE_UPI_ID` | 409 | Registering an existing UPI ID. |
| `InsufficientBalanceException` | `INSUFFICIENT_BALANCE` | 422 | Sender balance < amount. |
| `InvalidTransferException` | `INVALID_TRANSFER` | 400 | Semantically invalid transfer (e.g. sender == receiver). |
| `ObjectOptimisticLockingFailureException` | `CONCURRENCY_CONFLICT` | 409 | `@Version` mismatch; client may retry. |
| `MethodArgumentNotValidException` | `VALIDATION_FAILED` | 400 | `@Valid` body validation; per-field `errors` list. |
| `ConstraintViolationException` | `VALIDATION_FAILED` | 400 | Path/query param constraint violation. |
| `RateLimitExceededException` / 429 path | `RATE_LIMIT_EXCEEDED` | 429 | Quota exhausted (filter writes the body + `Retry-After`). |
| `AuthenticationException` | `UNAUTHORIZED` | 401 | Missing/invalid JWT. |
| `AccessDeniedException` | `FORBIDDEN` | 403 | `@PreAuthorize` denied (wrong role). |
| `Exception` (catch-all) | `INTERNAL_ERROR` | 500 | Anything unhandled; logged with stack trace, never leaked. |

Example body:

```json
{
  "type": "https://docs.payflow.com/errors/insufficient_balance",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Sender 'priya@okaxis' has insufficient balance for amount 999999.00",
  "instance": "/api/v1/transactions",
  "errorCode": "INSUFFICIENT_BALANCE",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-05-31T10:15:30.123Z"
}
```
