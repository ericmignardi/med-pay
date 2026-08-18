package com.medpay.ledger;

import org.springframework.boot.SpringApplication;

import com.medpay.ledger.testsupport.TestcontainersConfiguration;

public class TestMedPayLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MedPayLedgerApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}

}
