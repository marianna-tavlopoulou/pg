package com.marianna.gateway;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIntegrationTest {

    // static = one container shared across ALL test classes that extend this
    // non-static = new container per test class (slower, but fully isolated)
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                                                .withDatabaseName("payment_gateway_test")
                                                .withUsername("gateway")
                                                .withPassword("gateway_pass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
                                            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Create schema fresh for tests — don't validate against init.sql
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Point Redis to localhost — use a mock or embedded Redis later
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");

    }

}
