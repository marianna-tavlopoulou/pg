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
abstract class BaseContainerTest {

    // static = one container shared across ALL test classes that extend this
    // non-static = new container per test class (slower, but fully isolated)
    //
    // IMPORTANT: every IT class still gets its own Spring ApplicationContext
    // (and therefore its own Hikari pool) even though they share this one
    // physical container. "-c max_connections=200" raises Postgres's own
    // ceiling well above what a few small per-context pools (see
    // application-test.yml, maximum-pool-size: 5) could ever sum to, as a
    // second line of defense against connection-attempt floods if pool
    // sizing assumptions drift again later.
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

        // Schema is managed by Flyway (see db/migration/), not Hibernate.
        // Every IT class gets its own ApplicationContext against this one
        // shared container; ddl-auto=create-drop would have each context's
        // startup/shutdown racing to drop+recreate tables out from under
        // whichever other context's connections were mid-transaction at
        // that moment. validate + Flyway means schema is established once
        // and is a safe no-op on subsequent context startups.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");

        // Point Redis to localhost — use a mock or embedded Redis later
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");

    }

}