package com.medpay.ledger;

import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.FeeScheduleRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.repository.UserRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private FeeScheduleRepository feeScheduleRepository;
    @Autowired private ClaimRepository claimRepository;

    @Test
    @DisplayName("Flyway applied exactly V1 through V6, all successful")
    void migrationChainApplied() {
        var versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL "
                        + "ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    @DisplayName("all eight tables exist")
    void allTablesExist() {
        var tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_type = 'BASE TABLE'", String.class);

        assertThat(tables).contains(
                "users", "user_roles", "provider_accounts", "fee_schedules",
                "claims", "claim_lines", "ledger_journals", "outbox_events");
    }

    @Test
    @DisplayName("every temporal column is TIMESTAMPTZ, except service_date which is DATE")
    void temporalColumnsUseTimestamptz() {
        var nonTzTimestamps = jdbcTemplate.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND data_type = 'timestamp without time zone' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);

        assertThat(nonTzTimestamps).isEmpty();

        var serviceDateType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'claims' AND column_name = 'service_date'", String.class);

        assertThat(serviceDateType).isEqualTo("date");
    }

    @Test
    @DisplayName("no floating-point column exists anywhere in the schema")
    void noFloatingPointColumns() {
        var floats = jdbcTemplate.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' "
                        + "AND data_type IN ('real', 'double precision', 'money')", String.class);

        assertThat(floats).isEmpty();
    }

    @Test
    @DisplayName("seed data loaded: 3 users with one role each, 7 providers, 16 fee schedule rows")
    void seedDataLoaded() {
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(providerAccountRepository.count()).isEqualTo(7);
        assertThat(feeScheduleRepository.count()).isEqualTo(16);

        assertThat(userRepository.findByEmailIgnoreCase("PROCESSOR@MEDPAY.TEST"))
                .as("login is case-insensitive")
                .isPresent();
    }

    @Test
    @DisplayName("provider balances all start at zero")
    void providerBalancesStartAtZero() {
        assertThat(providerAccountRepository.findAll())
                .isNotEmpty()
                .allSatisfy(provider ->
                        assertThat(provider.getPayableBalance()).isEqualByComparingTo("0.0000"));
    }

    @Test
    @DisplayName("fee schedule lookup honours the effective date, not just the code")
    void feeScheduleLookupIsEffectiveDated() {
        assertThat(feeScheduleRepository.findRateFor("RT501", LocalDate.of(2022, 6, 15)))
                .isPresent()
                .get()
                .satisfies(rate -> assertThat(rate.getContractedRate()).isEqualByComparingTo("780.0000"));

        assertThat(feeScheduleRepository.findRateFor("RT501", LocalDate.of(2023, 6, 15)))
                .isPresent()
                .get()
                .satisfies(rate -> assertThat(rate.getContractedRate()).isEqualByComparingTo("845.0000"));

        assertThat(feeScheduleRepository.findRateFor("RT501", LocalDate.of(2019, 1, 1)))
                .as("before any effective row")
                .isEmpty();
    }

    @Test
    @DisplayName("roles eager-load with the user")
    void rolesLoadWithUser() {
        var reviewer = userRepository.findByEmailIgnoreCase("reviewer@medpay.test").orElseThrow();

        assertThat(reviewer.getRoles())
                .containsExactly(com.medpay.ledger.model.Role.MEDICAL_REVIEWER);
    }
}
