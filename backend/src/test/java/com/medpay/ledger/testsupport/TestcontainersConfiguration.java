package com.medpay.ledger.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres container shared by the integration tests.
 *
 * <p>Pinned to 16-alpine, matching docker-compose.yml. {@code postgres:latest}
 * would let a major-version bump change behaviour between two runs of the same
 * commit, and Phase 1 asserts against version-specific grant semantics.
 *
 * <p>Phase 1 replaces this with {@code AbstractIntegrationTest} holding a
 * singleton container, so the whole suite pays the startup cost once.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
