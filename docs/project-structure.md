# PayFlow — Project Structure

> Annotated tree of the repository with a one-line purpose for each package and key
> file.

```
payflow/
├── pom.xml                         # Maven build: Java 17, Spring Boot 3.3.5, deps, JaCoCo gate
├── Dockerfile                      # Multi-stage build -> non-root JRE image (payflow-api.jar)
├── docker-compose.yml              # mysql · rabbitmq · keycloak · payflow (profile docker)
├── README.md                       # Assignment brief + project overview
│
├── infra/
│   └── keycloak/
│       └── payflow-realm.json      # Imported realm: roles USER/ADMIN, client payflow-public, users alice/admin
│
├── docs/                           # This documentation set
│   ├── architecture.md             # System overview, design goals, component diagram
│   ├── hld.md                      # High-level design: components, flow, topology, scaling
│   ├── lld.md                      # Low-level design: classes, transfer algorithm, error mapping
│   ├── uml.md                      # Class / sequence / ER diagrams (Mermaid)
│   ├── database-schema.md          # Table-by-table schema reference
│   ├── project-structure.md        # This file
│   └── api.md                      # REST API contract + curl + token recipe
│
└── src/
    ├── main/
    │   ├── java/com/payflow/
    │   │   ├── PayflowApplication.java     # Entry point; @EnableJpaAuditing/@EnableAsync/@EnableCaching
    │   │   │
    │   │   ├── entity/                     # JPA aggregates
    │   │   │   ├── BaseEntity.java         #   id + timestamps + @Version (mapped superclass)
    │   │   │   ├── User.java               #   wallet owner
    │   │   │   ├── Transaction.java        #   immutable money-transfer record
    │   │   │   └── AuditEvent.java         #   append-only audit trail
    │   │   │
    │   │   ├── enums/                       # Domain enums
    │   │   │   ├── Role.java                #   USER / ADMIN
    │   │   │   ├── TransactionStatus.java   #   PENDING / COMPLETED / FAILED / REVERSED
    │   │   │   └── Currency.java            #   INR
    │   │   │
    │   │   ├── repository/                  # Spring Data JPA gateways
    │   │   │   ├── UserRepository.java      #   findByUpiId, existsByUpiId, balance @Query
    │   │   │   ├── TransactionRepository.java #  findByReferenceId, history @Query
    │   │   │   └── AuditEventRepository.java
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── CreateUserRequest.java   # validated registration payload
    │   │   │   │   └── SendMoneyRequest.java    # validated transfer payload
    │   │   │   └── response/
    │   │   │       ├── UserResponse.java
    │   │   │       ├── TransactionResponse.java
    │   │   │       └── PagedResponse.java       # pagination envelope (from(Page))
    │   │   │
    │   │   ├── mapper/                      # MapStruct entity -> response mappers
    │   │   │   ├── UserMapper.java
    │   │   │   └── TransactionMapper.java
    │   │   │
    │   │   ├── exception/                   # Error handling
    │   │   │   ├── ErrorCode.java           #   code -> HTTP status catalogue
    │   │   │   ├── PayflowException.java     #   abstract domain-exception root
    │   │   │   ├── ResourceNotFoundException.java
    │   │   │   ├── DuplicateResourceException.java
    │   │   │   ├── InsufficientBalanceException.java
    │   │   │   ├── InvalidTransferException.java
    │   │   │   ├── RateLimitExceededException.java
    │   │   │   └── GlobalExceptionHandler.java  # @RestControllerAdvice -> RFC 7807
    │   │   │
    │   │   ├── config/                      # Spring configuration
    │   │   │   ├── SecurityConfig.java          # OAuth2 resource server, CORS, security headers
    │   │   │   ├── KeycloakRealmRoleConverter.java # realm roles -> ROLE_*/SCOPE_* authorities
    │   │   │   ├── RabbitConfig.java            # exchange/queue/DLQ + JSON converter
    │   │   │   ├── CacheConfig.java             # Caffeine usersByUpiId cache
    │   │   │   ├── AsyncConfig.java             # @Async thread pool + error handler
    │   │   │   ├── AuditingConfig.java          # AuditorAware principal supplier
    │   │   │   ├── OpenApiConfig.java           # OpenAPI doc + bearer-JWT scheme
    │   │   │   └── PayflowProperties.java       # typed payflow.* config binding
    │   │   │
    │   │   ├── util/                        # Stateless utilities
    │   │   │   ├── IdentifierGenerator.java     # ULID business references (USR_/TXN_)
    │   │   │   ├── TraceIdentifierFactory.java  # W3C traceId/spanId generation + validation
    │   │   │   └── InputSanitizer.java          # NFKC normalise, strip control chars/tags
    │   │   │
    │   │   ├── concurrency/
    │   │   │   └── StripedLockRegistry.java     # 256 striped per-account ReentrantLocks
    │   │   │
    │   │   ├── messaging/                   # RabbitMQ pub/sub
    │   │   │   ├── TransactionEventPublisher.java     # interface (DIP)
    │   │   │   ├── RabbitTransactionEventPublisher.java # @Async Rabbit impl
    │   │   │   └── TransactionEventConsumer.java       # @RabbitListener -> audit
    │   │   │
    │   │   ├── event/
    │   │   │   └── TransactionEvent.java        # immutable domain event (carries traceId)
    │   │   │
    │   │   ├── filter/                      # Servlet filters
    │   │   │   ├── TracingFilter.java           # trace/span -> MDC + response headers
    │   │   │   └── RateLimitingFilter.java      # Bucket4j token bucket, 429 + Retry-After
    │   │   │
    │   │   ├── service/                     # Use-case interfaces
    │   │   │   ├── UserService.java
    │   │   │   ├── TransactionService.java
    │   │   │   ├── AuditService.java
    │   │   │   └── impl/                     # Implementations
    │   │   │       ├── UserServiceImpl.java         # registration, lookups (@Cacheable)
    │   │   │       ├── TransactionServiceImpl.java  # transfer orchestration
    │   │   │       ├── TransferExecutor.java        # @Transactional debit/credit + ledger write
    │   │   │       └── AuditServiceImpl.java        # @Async REQUIRES_NEW audit writes
    │   │   │
    │   │   └── controller/                  # REST endpoints (/api/v1)
    │   │       ├── UserController.java
    │   │       └── TransactionController.java
    │   │
    │   └── resources/
    │       ├── application.yml          # default config (datasource, JPA, rabbit, cache, security, actuator)
    │       ├── application-docker.yml    # docker profile overrides (compose hostnames)
    │       ├── logback-spring.xml        # console (local) / JSON (docker,prod) logging
    │       └── db/migration/
    │           └── V1__init_schema.sql   # Flyway baseline schema (users, transactions, audit_events)
    │
    └── test/                            # JUnit 5 + Spring Security Test + Testcontainers (MySQL, RabbitMQ)
```

## Layering at a glance

```
controller  ->  service (interface)  ->  service.impl  ->  repository  ->  MySQL
                                              |  \
                                   concurrency |   `-> messaging -> RabbitMQ -> consumer
                                    (locks)    `-> cache (Caffeine)
cross-cutting:  filter (tracing, rate-limit) · config (security, async, openapi) · exception (advice)
```

Dependencies point inward toward abstractions: controllers know only `*Service`
interfaces; services depend on the `TransactionEventPublisher` interface rather than
RabbitMQ; mapping and persistence are isolated in their own packages.
