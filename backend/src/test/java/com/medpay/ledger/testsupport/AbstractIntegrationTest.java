package com.medpay.ledger.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2VkLW91dHNpZGUtdGhlLXRlc3Qtc3VpdGU=",
        "spring.jpa.hibernate.ddl-auto=validate",
        // The scheduled dispatcher would drain the queue underneath OutboxDispatcherIT and
        // make its assertions race. Tests drive dispatch() explicitly instead.
        "app.outbox.dispatch-interval-ms=3600000",
        "app.outbox.dispatch-initial-delay-ms=3600000"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("medpay")
                    .withUsername("medpay")
                    .withPassword("medpay");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
