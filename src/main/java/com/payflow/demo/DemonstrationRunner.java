package com.payflow.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.SendMoneyRequest;
import com.payflow.dto.response.TransactionResponse;
import com.payflow.dto.response.UserResponse;
import com.payflow.exception.DuplicateResourceException;
import com.payflow.exception.InsufficientBalanceException;
import com.payflow.exception.InvalidTransferException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.filter.TracingFilter;
import com.payflow.repository.AuditEventRepository;
import com.payflow.service.TransactionService;
import com.payflow.service.UserService;
import com.payflow.util.TraceIdentifierFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end, self-driving demonstration of every PayFlow capability.
 *
 * <p>Active only under the {@code demo} Spring profile, so it never runs during normal startup
 * or tests. Run it with:</p>
 * <pre>
 *   docker compose up -d mysql rabbitmq keycloak
 *   mvn spring-boot:run -Dspring-boot.run.profiles=demo
 * </pre>
 *
 * <p>It exercises two complementary surfaces:</p>
 * <ol>
 *   <li><b>In-process domain flow</b> through the real service beans &mdash; persistence
 *   (MySQL + Hibernate + Flyway), ID generation, distributed-tracing MDC, registration,
 *   duplicate detection, caching (hit/evict), money transfer, RabbitMQ event publish/consume,
 *   audit trail, graceful exception handling, fine-grained concurrent transfers, pagination,
 *   and the custom JPQL query.</li>
 *   <li><b>HTTP flow</b> via self-directed calls &mdash; OAuth2 token retrieval from Keycloak,
 *   an authenticated secured request, an unauthenticated 401 with an RFC 7807 ProblemDetail,
 *   the W3C trace headers, and Bucket4j rate-limit 429 throttling.</li>
 * </ol>
 */
@Component
@Profile("demo")
@Order(Integer.MAX_VALUE)
public class DemonstrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemonstrationRunner.class);
    private static final String BAR = "==================================================================";
    private static final String API_V_1_USERS_UPI_U = "/api/v1/users/upi/{u}";

    private final UserService userService;
    private final TransactionService transactionService;
    private final AuditEventRepository auditEventRepository;
    private final CacheManager cacheManager;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final String issuerUri;

    public DemonstrationRunner(UserService userService,
                               TransactionService transactionService,
                               AuditEventRepository auditEventRepository,
                               CacheManager cacheManager,
                               ApplicationContext applicationContext,
                               ObjectMapper objectMapper,
                               @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.auditEventRepository = auditEventRepository;
        this.cacheManager = cacheManager;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.issuerUri = issuerUri;
    }

    @Override
    public void run(ApplicationArguments args) {
        // A unique per-run suffix keeps UPI handles collision-free across repeated runs.
        String runId = Long.toString(System.currentTimeMillis(), 36);
        String trace = TraceIdentifierFactory.newTraceId();
        String span = TraceIdentifierFactory.newSpanId();
        MDC.put(TracingFilter.TRACE_ID_KEY, trace);
        MDC.put(TracingFilter.SPAN_ID_KEY, span);

        try {
            banner("PAYFLOW END-TO-END DEMONSTRATION  (runId=" + runId + ")");
            log.info("[Tracing] Established request context -> traceId={} (32 hex), spanId={} (16 hex)", trace, span);
            log.info("[Tracing] Every log line below is correlated by these IDs (see the [traceId,spanId] prefix).");

            String alice = "alice." + runId + "@okaxis";
            String bob = "bob." + runId + "@oksbi";
            String whale = "whale." + runId + "@okhdfc";
            String sink = "sink." + runId + "@okicici";

            demoRegistration(alice, bob, whale, sink);
            demoDuplicateDetection(alice);
            demoCaching(alice);
            demoNotFound(runId);
            demoTransfer(alice, bob);
            demoBusinessRuleFailures(alice, bob);
            demoConcurrency(whale, sink);
            demoPagination(whale);
            demoCustomJpqlQuery();
            demoAuditTrail();
            demoHttpLayer(alice);

            banner("DEMONSTRATION COMPLETE — application remains running");
            log.info("Explore the live API at: http://localhost:{}/swagger-ui.html", resolvePort());
        } catch (RuntimeException ex) {
            log.error("Demonstration aborted due to an unexpected error", ex);
        } finally {
            MDC.clear();
        }
    }

    // ---------------------------------------------------------------------------------------
    // 1. Registration: write path, Hibernate persistence, ULID reference generation
    // ---------------------------------------------------------------------------------------
    private void demoRegistration(String alice, String bob, String whale, String sink) {
        section("1. USER REGISTRATION (entity + repository + service + ID utility + Hibernate/MySQL)");
        register("Alice Kumar", alice, "9876500001", "1000.00");
        register("Bob Singh", bob, "9876500002", "200.00");
        register("Whale Corp", whale, "9000000001", "100000.00");
        register("Sink Ltd", sink, "9000000002", "0.00");
    }

    private void register(String name, String upiId, String phone, String balance) {
        UserResponse user = userService.registerUser(
                new CreateUserRequest(name, upiId, phone, new BigDecimal(balance)));
        log.info("[Register] {} -> referenceId={} balance={} {}",
                user.upiId(), user.referenceId(), user.balance(), user.currency());
    }

    // ---------------------------------------------------------------------------------------
    // 2. Duplicate detection: uniqueness invariant + graceful exception handling
    // ---------------------------------------------------------------------------------------
    private void demoDuplicateDetection(String alice) {
        section("2. DUPLICATE UPI DETECTION (business rule + graceful exception handling)");
        try {
            userService.registerUser(new CreateUserRequest("Impostor", alice, "9999999999", new BigDecimal("1")));
        } catch (DuplicateResourceException ex) {
            log.info("[Duplicate] Correctly rejected: errorCode={} message='{}'",
                    ex.getErrorCode(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------
    // 3. Caching: Caffeine miss -> hit, with native stats
    // ---------------------------------------------------------------------------------------
    private void demoCaching(String alice) {
        section("3. CACHING (Caffeine: first call = DB miss, second = cache hit)");
        userService.getByUpiId(alice);                 // populates cache (miss)
        userService.getByUpiId(alice);                 // served from cache (hit)
        logCacheStats("after two lookups");
    }

    private void logCacheStats(String when) {
        if (cacheManager.getCache("usersByUpiId") instanceof CaffeineCache caffeineCache) {
            var stats = caffeineCache.getNativeCache().stats();
            log.info("[Cache] usersByUpiId {} -> hits={} misses={} hitRate={}",
                    when, stats.hitCount(), stats.missCount(), String.format("%.2f", stats.hitRate()));
        }
    }

    // ---------------------------------------------------------------------------------------
    // 4. Not-found handling
    // ---------------------------------------------------------------------------------------
    private void demoNotFound(String runId) {
        section("4. RESOURCE NOT FOUND (graceful 404-mapped exception)");
        try {
            userService.getByUpiId("ghost." + runId + "@okaxis");
        } catch (ResourceNotFoundException ex) {
            log.info("[NotFound] Correctly rejected: errorCode={} message='{}'", ex.getErrorCode(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------
    // 5. Money transfer: atomic debit/credit, RabbitMQ event, audit, cache eviction
    // ---------------------------------------------------------------------------------------
    private void demoTransfer(String alice, String bob) {
        section("5. MONEY TRANSFER (transactional debit/credit + RabbitMQ event + audit + cache evict)");
        log.info("[Transfer] Before: {}={}  {}={}",
                alice, userService.getByUpiId(alice).balance(), bob, userService.getByUpiId(bob).balance());

        TransactionResponse txn = transactionService.sendMoney(
                new SendMoneyRequest(alice, bob, new BigDecimal("250.00"), "lunch split"));
        log.info("[Transfer] Recorded reference={} status={} amount={}",
                txn.referenceId(), txn.status(), txn.amount());

        // getByUpiId now reflects fresh balances because the transfer evicted the cache entries.
        log.info("[Transfer] After:  {}={}  {}={}  (cache was evicted -> fresh read)",
                alice, userService.getByUpiId(alice).balance(), bob, userService.getByUpiId(bob).balance());
        log.info("[Messaging] A TransactionEvent was published to RabbitMQ; watch for the "
                + "'Notification handler received...' consumer log line above/below.");
    }

    // ---------------------------------------------------------------------------------------
    // 6. Business-rule failures: insufficient balance + self-transfer
    // ---------------------------------------------------------------------------------------
    private void demoBusinessRuleFailures(String alice, String bob) {
        section("6. BUSINESS-RULE FAILURES (insufficient balance + self-transfer)");
        try {
            transactionService.sendMoney(new SendMoneyRequest(bob, alice, new BigDecimal("999999.00"), "too much"));
        } catch (InsufficientBalanceException ex) {
            log.info("[Insufficient] Correctly rejected: errorCode={} message='{}'", ex.getErrorCode(), ex.getMessage());
        }
        try {
            transactionService.sendMoney(new SendMoneyRequest(alice, alice, new BigDecimal("1.00"), "self"));
        } catch (InvalidTransferException ex) {
            log.info("[SelfTransfer] Correctly rejected: errorCode={} message='{}'", ex.getErrorCode(), ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------
    // 7. Concurrency: many parallel transfers, fine-grained striped locks + optimistic version
    // ---------------------------------------------------------------------------------------
    private void demoConcurrency(String whale, String sink) {
        section("7. CONCURRENCY (20 parallel transfers; fine-grained per-account locking)");
        int transfers = 20;
        BigDecimal each = new BigDecimal("100.00");
        BigDecimal whaleBefore = userService.getByUpiId(whale).balance();
        BigDecimal sinkBefore = userService.getByUpiId(sink).balance();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < transfers; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    transactionService.sendMoney(new SendMoneyRequest(whale, sink, each, "concurrent"));
                    ok.incrementAndGet();
                } catch (RuntimeException ex) {
                    failed.incrementAndGet();
                }
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        pool.shutdown();

        BigDecimal whaleAfter = userService.getByUpiId(whale).balance();
        BigDecimal sinkAfter = userService.getByUpiId(sink).balance();
        BigDecimal moved = each.multiply(BigDecimal.valueOf(ok.get()));
        log.info("[Concurrency] transfers ok={} failed={}", ok.get(), failed.get());
        log.info("[Concurrency] whale {} -> {} (expected {})", whaleBefore, whaleAfter, whaleBefore.subtract(moved));
        log.info("[Concurrency] sink  {} -> {} (expected {})", sinkBefore, sinkAfter, sinkBefore.add(moved));
        boolean consistent = whaleAfter.compareTo(whaleBefore.subtract(moved)) == 0
                && sinkAfter.compareTo(sinkBefore.add(moved)) == 0;
        log.info("[Concurrency] No lost updates -> balances are CONSISTENT: {}", consistent);
    }

    // ---------------------------------------------------------------------------------------
    // 8. Pagination: transaction history (Spring Data Pageable)
    // ---------------------------------------------------------------------------------------
    private void demoPagination(String whale) {
        section("8. PAGINATION (transaction history, page size 5, newest first)");
        Page<TransactionResponse> page = transactionService.getHistory(
                whale, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
        log.info("[Pagination] page={} size={} totalElements={} totalPages={} returnedOnPage={}",
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getNumberOfElements());
    }

    // ---------------------------------------------------------------------------------------
    // 9. Custom JPQL query (@Query)
    // ---------------------------------------------------------------------------------------
    private void demoCustomJpqlQuery() {
        section("9. CUSTOM JPQL @Query (users with balance > 5000)");
        Page<UserResponse> rich = userService.getUsersWithBalanceAbove(
                new BigDecimal("5000.00"), PageRequest.of(0, 10));
        log.info("[JPQL] found {} user(s) with balance > 5000:", rich.getTotalElements());
        rich.forEach(u -> log.info("[JPQL]   {} balance={}", u.upiId(), u.balance()));
    }

    // ---------------------------------------------------------------------------------------
    // 10. Audit trail (async, persisted)
    // ---------------------------------------------------------------------------------------
    private void demoAuditTrail() {
        section("10. AUDIT TRAIL (asynchronous, persisted)");
        sleep(1500); // give @Async audit writes a moment to flush
        log.info("[Audit] total audit events persisted so far: {}", auditEventRepository.count());
    }

    // ---------------------------------------------------------------------------------------
    // 11. HTTP layer: OAuth2 token, secured call, 401 + ProblemDetail, trace headers, 429
    // ---------------------------------------------------------------------------------------
    private void demoHttpLayer(String alice) {
        section("11. HTTP LAYER (OAuth2 + security + ProblemDetail + trace headers + rate limit)");
        String base = "http://localhost:" + resolvePort();
        RestClient http = RestClient.create();

        // (a) Unauthenticated request -> 401 with RFC 7807 ProblemDetail + echoed trace header.
        // exchange() (unlike retrieve()) does not throw on error statuses, so we can inspect the body.
        http.get().uri(base + API_V_1_USERS_UPI_U, alice).exchange((req, res) -> {
            log.info("[HTTP 401] GET /api/v1/users (no token) -> status={} X-Trace-Id={}",
                    res.getStatusCode().value(), res.getHeaders().getFirst(TracingFilter.TRACE_ID_HEADER));
            log.info("[HTTP 401] RFC 7807 ProblemDetail body: {}",
                    new String(res.getBody().readAllBytes()).trim());
            return null;
        });

        // (b) OAuth2 password grant -> token -> authenticated secured call
        String token = fetchToken(http, "alice", "alice123");
        if (token != null) {
            String me = http.get().uri(base + API_V_1_USERS_UPI_U, alice)
                    .header("Authorization", "Bearer " + token)
                    .retrieve().body(String.class);
            log.info("[HTTP 200] Authenticated GET succeeded -> {}", me);
        } else {
            log.warn("[HTTP] Skipped authenticated call — could not obtain a Keycloak token "
                    + "(is Keycloak running on the issuer URI {}?).", issuerUri);
        }

        // (c) Rate limiting -> exceed the demo quota (capacity 5) to trigger HTTP 429
        int throttled = 0;
        for (int i = 0; i < 8; i++) {
            int status = http.get().uri(base + API_V_1_USERS_UPI_U, alice)
                    .exchange((req, res) -> res.getStatusCode().value());
            if (status == 429) {
                throttled++;
            }
        }
        log.info("[HTTP 429] Sent 8 rapid requests; {} were rate-limited (429) by Bucket4j.", throttled);
    }

    private String fetchToken(RestClient http, String username, String password) {
        try {
            String tokenEndpoint = issuerUri + "/protocol/openid-connect/token";
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", "payflow-public");
            form.add("grant_type", "password");
            form.add("username", username);
            form.add("password", password);
            String response = http.post().uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(response);
            return node.path("access_token").asText(null);
        } catch (Exception ex) {
            log.warn("[HTTP] Token fetch failed: {}", ex.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------
    private int resolvePort() {
        if (applicationContext instanceof WebServerApplicationContext webContext
                && webContext.getWebServer() != null) {
            return webContext.getWebServer().getPort();
        }
        return 8080;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void banner(String title) {
        log.info(BAR);
        log.info("  {}", title);
        log.info(BAR);
    }

    private void section(String title) {
        log.info("");
        log.info("---- {} ----", title);
    }
}
