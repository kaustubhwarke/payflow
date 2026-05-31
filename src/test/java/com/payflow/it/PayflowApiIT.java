package com.payflow.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.request.CreateUserRequest;
import com.payflow.dto.request.SendMoneyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests (Rule 21). Boots the full Spring context against real MySQL and
 * RabbitMQ containers (production-grade persistence + messaging, mentor feedback 22.d). OAuth2 is
 * exercised via the {@code jwt()} post-processor with explicit authorities, so authorization
 * rules are verified without a live Keycloak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PayflowApiIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("payflow")
            .withUsername("payflow")
            .withPassword("payflow");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    /** Prevents the resource server from contacting the (absent) Keycloak issuer at startup. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static final SimpleGrantedAuthority USER = new SimpleGrantedAuthority("ROLE_USER");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void register(String name, String upi, String phone, String balance) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest(name, upi, phone, new BigDecimal(balance)))))
                .andExpect(status().isCreated());
    }

    @Test
    void fullTransferLifecycle_debitsCreditsAndRecordsHistory() throws Exception {
        // Arrange: two registered wallet owners
        register("Alice", "alice@okaxis", "9876500001", "1000.00");
        register("Bob", "bob@oksbi", "9876500002", "200.00");

        // Prime the cache, then transfer (which must evict it)
        mockMvc.perform(get("/api/v1/users/upi/alice@okaxis").with(jwt().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000.00));

        // Act: send money
        mockMvc.perform(post("/api/v1/transactions")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SendMoneyRequest("alice@okaxis", "bob@oksbi",
                                new BigDecimal("250.00"), "lunch"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.referenceId", org.hamcrest.Matchers.startsWith("TXN_")));

        // Assert: balances reflect the transfer (cache was evicted -> fresh read)
        mockMvc.perform(get("/api/v1/users/upi/alice@okaxis").with(jwt().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(750.00));
        mockMvc.perform(get("/api/v1/users/upi/bob@oksbi").with(jwt().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(450.00));

        // History for Alice contains the transfer
        mockMvc.perform(get("/api/v1/transactions").param("upiId", "alice@okaxis")
                        .with(jwt().authorities(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(250.00));
    }

    @Test
    void registerDuplicateUpiId_returns409() throws Exception {
        register("Carol", "carol@okaxis", "9876500003", "500.00");

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateUserRequest("Carol Two", "carol@okaxis",
                                "9876500004", new BigDecimal("10.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_UPI_ID"));
    }

    @Test
    void registerWithInvalidPayload_returns400() throws Exception {
        // Bad UPI id + negative balance
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"upiId\":\"not-a-upi\",\"phoneNumber\":\"123\",\"openingBalance\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void getUnknownUser_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/upi/ghost@okaxis").with(jwt().authorities(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void transferWithInsufficientBalance_returns422() throws Exception {
        register("Dave", "dave@okaxis", "9876500005", "10.00");
        register("Erin", "erin@oksbi", "9876500006", "0.00");

        mockMvc.perform(post("/api/v1/transactions")
                        .with(jwt().authorities(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SendMoneyRequest("dave@okaxis", "erin@oksbi",
                                new BigDecimal("100.00"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void requestWithoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/upi/alice@okaxis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAllUsers_forbiddenForUserRole_allowedForAdmin() throws Exception {
        // USER lacks privilege to list all users
        mockMvc.perform(get("/api/v1/users").with(jwt().authorities(USER)))
                .andExpect(status().isForbidden());

        // ADMIN may list
        mockMvc.perform(get("/api/v1/users").with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
