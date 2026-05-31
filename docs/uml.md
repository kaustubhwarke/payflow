# PayFlow — UML & Diagrams

> Class diagram, send-money and register-user sequence diagrams, and the ER diagram.
> All diagrams are Mermaid and render on GitHub.

## 1. Class diagram (entities + key services/interfaces)

```mermaid
classDiagram
    direction LR

    class BaseEntity {
        <<abstract>>
        -Long id
        -Instant createdAt
        -Instant updatedAt
        -Long version
    }
    class User {
        -String referenceId
        -String name
        -String upiId
        -String phoneNumber
        -BigDecimal balance
        -Currency currency
    }
    class Transaction {
        -String referenceId
        -String senderUpiId
        -String receiverUpiId
        -BigDecimal amount
        -Currency currency
        -TransactionStatus status
        -String note
        -String failureReason
    }
    class AuditEvent {
        -String action
        -String actor
        -String entityType
        -String entityReference
        -String detail
        -String traceId
    }
    BaseEntity <|-- User
    BaseEntity <|-- Transaction
    BaseEntity <|-- AuditEvent

    class UserService {
        <<interface>>
        +registerUser(CreateUserRequest) UserResponse
        +getByReference(String) UserResponse
        +getByUpiId(String) UserResponse
        +getAllUsers(Pageable) Page~UserResponse~
        +getUsersWithBalanceAbove(BigDecimal, Pageable) Page~UserResponse~
    }
    class TransactionService {
        <<interface>>
        +sendMoney(SendMoneyRequest) TransactionResponse
        +getByReference(String) TransactionResponse
        +getHistory(String, Pageable) Page~TransactionResponse~
    }
    class AuditService {
        <<interface>>
        +record(action, actor, entityType, entityReference, detail)
    }
    class TransactionEventPublisher {
        <<interface>>
        +publish(TransactionEvent)
    }

    class UserServiceImpl
    class TransactionServiceImpl
    class TransferExecutor {
        +execute(SendMoneyRequest) Transaction
    }
    class AuditServiceImpl
    class RabbitTransactionEventPublisher
    class StripedLockRegistry {
        +executeWithLock(key, action)
        +executeWithLocks(a, b, action)
    }

    UserService <|.. UserServiceImpl
    TransactionService <|.. TransactionServiceImpl
    AuditService <|.. AuditServiceImpl
    TransactionEventPublisher <|.. RabbitTransactionEventPublisher

    UserServiceImpl --> UserRepository
    UserServiceImpl --> AuditService
    TransactionServiceImpl --> TransferExecutor
    TransactionServiceImpl --> StripedLockRegistry
    TransactionServiceImpl --> TransactionEventPublisher
    TransactionServiceImpl --> AuditService
    TransferExecutor --> UserRepository
    TransferExecutor --> TransactionRepository
    AuditServiceImpl --> AuditEventRepository

    class UserRepository { <<interface>> }
    class TransactionRepository { <<interface>> }
    class AuditEventRepository { <<interface>> }
```

## 2. Sequence diagram — Send money

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant TF as TracingFilter
    participant RL as RateLimitingFilter
    participant SEC as Spring Security
    participant TC as TransactionController
    participant TS as TransactionServiceImpl
    participant LK as StripedLockRegistry
    participant EX as TransferExecutor
    participant UR as UserRepository
    participant TR as TransactionRepository
    participant DB as MySQL
    participant PUB as EventPublisher
    participant MQ as RabbitMQ
    participant CON as TransactionEventConsumer
    participant AUD as AuditService

    Client->>TF: POST /api/v1/transactions (Bearer JWT)
    TF->>RL: trace/span in MDC + headers
    RL->>SEC: token consumed
    SEC->>TC: JWT valid, ROLE mapped
    TC->>TS: sendMoney(request)
    TS->>TS: validate sender != receiver
    TS->>LK: executeWithLocks(sender, receiver)
    LK->>EX: execute(request)  [inside locks]
    EX->>UR: findByUpiId(sender), findByUpiId(receiver)
    UR->>DB: SELECT
    EX->>EX: check balance, debit/credit
    EX->>TR: save(Transaction COMPLETED)
    TR->>DB: INSERT + UPDATE balances
    DB-->>EX: COMMIT
    EX-->>LK: Transaction
    LK-->>TS: result (locks released after commit)
    TS->>TS: evict cache(sender, receiver)
    TS-)PUB: publish(TransactionEvent) @Async
    PUB-)MQ: convertAndSend(transaction.completed)
    TS-)AUD: record(TRANSACTION_COMPLETED) @Async
    TS-->>TC: TransactionResponse
    TC-->>Client: 201 Created + Location
    MQ-)CON: deliver event
    CON-)AUD: record(TRANSACTION_NOTIFIED)
```

## 3. Sequence diagram — Register user

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant TF as TracingFilter
    participant RL as RateLimitingFilter
    participant SEC as Spring Security
    participant UC as UserController
    participant US as UserServiceImpl
    participant UR as UserRepository
    participant DB as MySQL
    participant AUD as AuditService

    Client->>TF: POST /api/v1/users (Bearer JWT)
    TF->>RL: trace context
    RL->>SEC: token consumed
    SEC->>UC: JWT valid, @PreAuthorize USER/ADMIN
    UC->>UC: @Valid CreateUserRequest
    UC->>US: registerUser(request)
    US->>UR: existsByUpiId(upiId)
    UR->>DB: SELECT
    alt UPI ID already taken
        US-->>UC: DuplicateResourceException
        UC-->>Client: 409 Conflict (ProblemDetail)
    else available
        US->>US: sanitize name, generate USR_ ULID
        US->>UR: save(User)
        UR->>DB: INSERT
        US-)AUD: record(USER_REGISTERED) @Async
        US-->>UC: UserResponse
        UC-->>Client: 201 Created + Location
    end
```

## 4. ER diagram

> The application stores sender/receiver as plain UPI-ID strings (no FK constraints,
> per the assignment scope). The dashed relationships below are **logical**
> references via `upi_id`, not enforced foreign keys.

```mermaid
erDiagram
    USERS {
        BIGINT id PK
        VARCHAR reference_id UK
        VARCHAR name
        VARCHAR upi_id UK
        VARCHAR phone_number
        DECIMAL balance
        VARCHAR currency
        DATETIME created_at
        DATETIME updated_at
        BIGINT version
    }
    TRANSACTIONS {
        BIGINT id PK
        VARCHAR reference_id UK
        VARCHAR sender_upi_id
        VARCHAR receiver_upi_id
        DECIMAL amount
        VARCHAR currency
        VARCHAR status
        VARCHAR note
        VARCHAR failure_reason
        DATETIME created_at
        DATETIME updated_at
        BIGINT version
    }
    AUDIT_EVENTS {
        BIGINT id PK
        VARCHAR action
        VARCHAR actor
        VARCHAR entity_type
        VARCHAR entity_reference
        VARCHAR detail
        VARCHAR trace_id
        DATETIME created_at
        DATETIME updated_at
        BIGINT version
    }

    USERS ||..o{ TRANSACTIONS : "sends (logical: sender_upi_id = upi_id)"
    USERS ||..o{ TRANSACTIONS : "receives (logical: receiver_upi_id = upi_id)"
    USERS ||..o{ AUDIT_EVENTS : "logically referenced by entity_reference"
```
