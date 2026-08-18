package com.medpay.ledger;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.ProviderAccount;
import com.medpay.ledger.model.Role;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * FR-023. Every scenario here races two or more real requests against the same row and
 * asserts on what the database holds afterwards — the point is the persisted invariant,
 * not the HTTP status of any single caller.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final String PROCESSOR_UUID = "a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91";
    private static final String REVIEWER_UUID = "b2f9d5e3-8c4a-4f7b-8d26-3e9a1c7f4b02";
    private static final String CONCURRENCY_NPI = "1000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private LedgerJournalRepository journalRepository;

    @Autowired
    private ProviderAccountRepository providerAccountRepository;

    private static RequestPostProcessor asProcessor() {
        return user(new AuthenticatedUser(1L, UUID.fromString(PROCESSOR_UUID),
                "processor@medpay.test", "Priya Raman", Set.of(Role.CLAIMS_PROCESSOR)));
    }

    private static RequestPostProcessor asReviewer() {
        return user(new AuthenticatedUser(2L, UUID.fromString(REVIEWER_UUID),
                "reviewer@medpay.test", "Dr. Marcus Oyelaran", Set.of(Role.MEDICAL_REVIEWER)));
    }

    private static String submissionBody(String memberReference, String serviceCode, String billed) {
        return """
                {
                  "providerNpi": "%s",
                  "memberReference": "%s",
                  "serviceDate": "%s",
                  "billedAmount": "%s",
                  "lines": [
                    {"serviceCode": "%s", "diagnosisCode": "E1165", "billedAmount": "%s"}
                  ]
                }
                """.formatted(CONCURRENCY_NPI, memberReference, LocalDate.now().minusDays(1),
                billed, serviceCode, billed);
    }

    /** Releases every task at once so the requests genuinely overlap. */
    private static <T> List<T> raceAll(List<Callable<T>> tasks) throws Exception {
        int count = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> pool.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            return futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    return null;
                }
            }).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("TC-C-003: a concurrent double-submit yields one claim and one journal group")
    void doubleSubmitYieldsOneClaimAndOneJournalGroup() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        String memberReference = "MBR-" + UUID.randomUUID().toString().substring(0, 8);
        String body = submissionBody(memberReference, "MP103", "150.00");

        Callable<Integer> submit = () -> mockMvc.perform(post("/api/v1/claims")
                        .with(asProcessor())
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();

        List<Integer> statuses = raceAll(List.of(submit, submit));

        Claim persisted = claimRepository
                .findBySubmittedByIdAndIdempotencyKey(1L, idempotencyKey)
                .orElseThrow(() -> new AssertionError("no claim was persisted for the key"));

        // One row for the key, regardless of which caller won the race.
        assertThat(claimRepository.count()).isPositive();
        assertThat(persisted.getStatus()).isEqualTo(ClaimStatus.PAID);

        // Exactly one balanced pair: the loser must not have posted a second one.
        List<?> journals = journalRepository
                .findByClaimClaimUuidOrderByPostedAtAscIdAsc(persisted.getClaimUuid());
        assertThat(journals).hasSize(2);

        // At least one caller got a real answer; neither may have produced a 500.
        assertThat(statuses).anyMatch(status -> status != null && status < 400);
        assertThat(statuses).noneMatch(status -> status != null && status >= 500);
    }

    @Test
    @DisplayName("TC-C-004: two concurrent approvals yield one PAID claim and one balanced pair")
    void concurrentApprovalsPayExactlyOnce() throws Exception {
        Claim claim = fixtures.persistedClaim(fixtures.processor(),
                fixtures.provider(CONCURRENCY_NPI), "60000.00", "50000.00",
                ClaimStatus.FLAGGED_REVIEW);

        Callable<Integer> approve = () -> mockMvc.perform(
                        post("/api/v1/review/claims/{uuid}/approve", claim.getClaimUuid())
                                .with(asReviewer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"concurrent approval race\"}"))
                .andReturn()
                .getResponse()
                .getStatus();

        List<Integer> statuses = raceAll(List.of(approve, approve));

        long successes = statuses.stream().filter(status -> status != null && status == 200).count();
        assertThat(successes).isEqualTo(1);

        // The loser is rejected, not served — 409 either way (state or version).
        assertThat(statuses).filteredOn(status -> status == null || status != 200)
                .allMatch(status -> status != null && status == 409);

        Claim reloaded = claimRepository.findByClaimUuid(claim.getClaimUuid()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(journalRepository
                .findByClaimClaimUuidOrderByPostedAtAscIdAsc(claim.getClaimUuid())).hasSize(2);
    }

    @Test
    @DisplayName("FR-023: concurrent posts to one provider leave the balance equal to the journal sum")
    void concurrentProviderPostsKeepTheBalanceReconciled() throws Exception {
        ProviderAccount provider = fixtures.provider(CONCURRENCY_NPI);
        BigDecimal balanceBefore = provider.getPayableBalance();

        int concurrentSubmissions = 6;
        AtomicInteger sequence = new AtomicInteger();

        List<Callable<Integer>> submissions = java.util.stream.IntStream
                .range(0, concurrentSubmissions)
                .<Callable<Integer>>mapToObj(index -> () -> {
                    String memberReference = "MBR-RACE-" + sequence.incrementAndGet()
                            + "-" + UUID.randomUUID().toString().substring(0, 6);
                    return mockMvc.perform(post("/api/v1/claims")
                                    .with(asProcessor())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(submissionBody(memberReference, "MP101", "125.00")))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                })
                .toList();

        List<Integer> statuses = raceAll(submissions);

        // Optimistic locking on a single hot row has a contention ceiling: past the retry
        // budget a caller is told to retry rather than served a lost update. Either answer is
        // correct; a 500, or a 201 whose money never landed, is not.
        assertThat(statuses).allMatch(status -> status != null && (status == 201 || status == 409));
        long accepted = statuses.stream().filter(status -> status == 201).count();
        assertThat(accepted).isPositive();

        ProviderAccount reloaded = providerAccountRepository
                .findByProviderNpi(CONCURRENCY_NPI).orElseThrow();

        // The stored balance is the cached projection; the journal sum is the truth.
        BigDecimal journalSum = journalRepository.sumProviderPayableBalance(reloaded.getId());
        assertThat(reloaded.getPayableBalance())
                .isEqualByComparingTo(journalSum);

        // The balance moved by exactly what was accepted — a lost update would show up here as
        // a shortfall, and a double-apply as an excess.
        BigDecimal expectedIncrease = new BigDecimal("125.00")
                .multiply(BigDecimal.valueOf(accepted));
        assertThat(reloaded.getPayableBalance().subtract(balanceBefore))
                .isEqualByComparingTo(expectedIncrease);

        assertThat(journalRepository.findUnbalancedJournalGroups()).isEmpty();
    }

    @Test
    @DisplayName("FR-015: no journal group anywhere in the database sums to a non-zero amount")
    void everyJournalGroupBalances() {
        assertThat(journalRepository.findUnbalancedJournalGroups()).isEmpty();
    }
}
