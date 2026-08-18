package com.medpay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point.
 *
 * <p>{@code @EnableScheduling} is here for Phase 7's {@code OutboxDispatcher}
 * ({@code @Scheduled(fixedDelay = 5000)}), and {@code @EnableResilientMethods}
 * for Phase 5's retry on optimistic lock failure during adjudication (FR-023).
 * Both are declared now so the wiring is in place before the components land.
 *
 * <p>PLAN.md Phase 0 calls for {@code @EnableRetry} from spring-retry. On Spring
 * Boot 4 / Framework 7 that library is no longer dependency-managed and its role
 * is filled by {@code org.springframework.resilience}, so the equivalent
 * annotation is {@code @EnableResilientMethods}.
 */
@SpringBootApplication
@EnableScheduling
@EnableResilientMethods
public class MedPayLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MedPayLedgerApplication.class, args);
	}

}
