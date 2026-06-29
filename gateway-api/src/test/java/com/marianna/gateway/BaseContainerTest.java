package com.marianna.gateway;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class BaseContainerTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_gateway_test")
            .withUsername("gateway")
            .withPassword("gateway_pass")
            .withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    // Start both containers once — subsequent calls to start() on an already
    // running container are a no-op, so every subclass safely calls this.
    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl() + "?sslmode=disable");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

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
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

    }

}