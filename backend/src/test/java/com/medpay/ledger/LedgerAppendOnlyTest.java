package com.medpay.ledger;

import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerAppendOnlyTest extends AbstractIntegrationTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID journalGroupId;

    @BeforeEach
    void seedOneJournalPair() {
        journalGroupId = UUID.randomUUID();

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'processor@medpay.test'", Long.class);
        Long providerId = jdbcTemplate.queryForObject(
                "SELECT id FROM provider_accounts WHERE provider_npi = '1000000001'", Long.class);
        String npi = jdbcTemplate.queryForObject(
                "SELECT provider_npi FROM provider_accounts WHERE id = ?", String.class, providerId);

        Long claimId = jdbcTemplate.queryForObject("""
                INSERT INTO claims (claim_uuid, submitted_by_user_id, provider_npi, member_reference,
                                    service_date, billed_amount, allowed_amount, patient_responsibility,
                                    status, claim_fingerprint, idempotency_key)
                VALUES (?, ?, ?, 'MBR-TESTONLY0001', ?, 500.0000, 400.0000, 100.0000,
                        'PAID', ?, ?)
                RETURNING id
                """, Long.class,
                UUID.randomUUID(), userId, npi, LocalDate.now().minusDays(1),
                UUID.randomUUID().toString().replace("-", "").repeat(2), UUID.randomUUID());

        jdbcTemplate.update("""
                INSERT INTO ledger_journals (journal_group_id, claim_id, provider_account_id,
                                             account_type, direction, amount, memo)
                VALUES (?, ?, NULL, 'PAYER_CLAIMS_EXPENSE', 'DEBIT', 400.0000, 'test seed')
                """, journalGroupId, claimId);

        jdbcTemplate.update("""
                INSERT INTO ledger_journals (journal_group_id, claim_id, provider_account_id,
                                             account_type, direction, amount, memo)
                VALUES (?, ?, ?, 'PROVIDER_PAYABLE', 'CREDIT', 400.0000, 'test seed')
                """, journalGroupId, claimId, providerId);
    }

    @Test
    @DisplayName("medpay_app cannot UPDATE a posted journal row")
    void updateIsRefusedForAppRole() {
        assertThatThrownBy(() -> executeAsAppRole(
                "UPDATE ledger_journals SET amount = 1.0000 WHERE journal_group_id = '"
                        + journalGroupId + "'"))
                .isInstanceOf(SQLException.class)
                .satisfies(thrown ->
                        assertThat(((SQLException) thrown).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    @DisplayName("medpay_app cannot DELETE a posted journal row")
    void deleteIsRefusedForAppRole() {
        assertThatThrownBy(() -> executeAsAppRole(
                "DELETE FROM ledger_journals WHERE journal_group_id = '" + journalGroupId + "'"))
                .isInstanceOf(SQLException.class)
                .satisfies(thrown ->
                        assertThat(((SQLException) thrown).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    @Test
    @DisplayName("medpay_app can still SELECT and INSERT — only UPDATE and DELETE were revoked")
    void selectAndInsertRemainPermitted() throws SQLException {
        Long claimId = jdbcTemplate.queryForObject(
                "SELECT claim_id FROM ledger_journals WHERE journal_group_id = ? LIMIT 1",
                Long.class, journalGroupId);

        UUID freshGroup = UUID.randomUUID();

        executeAsAppRole("SELECT count(*) FROM ledger_journals");
        executeAsAppRole("""
                INSERT INTO ledger_journals (journal_group_id, claim_id, provider_account_id,
                                             account_type, direction, amount, memo)
                VALUES ('%s', %d, NULL, 'PAYER_CLAIMS_EXPENSE', 'DEBIT', 25.0000, 'append check')
                """.formatted(freshGroup, claimId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_journals WHERE journal_group_id = ?",
                Integer.class, freshGroup)).isEqualTo(1);
    }

    @Test
    @DisplayName("TRUNCATE is refused too — it is not covered by DELETE")
    void truncateIsRefusedForAppRole() {
        assertThatThrownBy(() -> executeAsAppRole("TRUNCATE ledger_journals CASCADE"))
                .isInstanceOf(SQLException.class)
                .satisfies(thrown ->
                        assertThat(((SQLException) thrown).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private void executeAsAppRole(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE medpay_app");
            try {
                statement.execute(sql);
            } finally {
                statement.execute("RESET ROLE");
            }
        }
    }

    @Test
    @DisplayName("the seeded journal group balances")
    void seededGroupBalances() {
        BigDecimal signedSum = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END), 0)
                FROM ledger_journals WHERE journal_group_id = ?
                """, BigDecimal.class, journalGroupId);

        assertThat(signedSum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
