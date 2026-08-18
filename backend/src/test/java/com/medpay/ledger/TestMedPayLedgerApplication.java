package com.medpay.ledger;

import org.springframework.boot.SpringApplication;

import com.medpay.ledger.testsupport.TestcontainersConfiguration;

/**
 * Runs the application against a throwaway Postgres container instead of the
 * compose stack — useful when iterating without `docker compose up`.
 *
 * <p>Launch this class from the IDE rather than {@link MedPayLedgerApplication}.
 * It still needs MEDPAY_JWT_SECRET in the run configuration's environment.
 */
public class TestMedPayLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MedPayLedgerApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}

}
