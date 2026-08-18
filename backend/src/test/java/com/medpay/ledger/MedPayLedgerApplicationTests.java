package com.medpay.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.medpay.ledger.testsupport.TestcontainersConfiguration;

/**
 * Phase 0 smoke test: the context starts against a real Postgres 16 and Flyway
 * runs. Phase 1 builds the real suite on top of this.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1uZXZlci11c2VkLW91dHNpZGUtdGhlLXRlc3Qtc3VpdGU=")
@ActiveProfiles("dev")
class MedPayLedgerApplicationTests {

	@Test
	void contextLoads() {
	}

}
