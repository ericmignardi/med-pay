package com.medpay.ledger.repository;

import com.medpay.ledger.model.LedgerJournal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerJournalRepository
        extends Repository<LedgerJournal, Long>, JpaSpecificationExecutor<LedgerJournal> {

    void save(LedgerJournal journal);

    Optional<LedgerJournal> findById(Long id);

    List<LedgerJournal> findByJournalGroupIdOrderByIdAsc(UUID journalGroupId);

    List<LedgerJournal> findByClaimIdOrderByPostedAtAsc(Long claimId);

    Page<LedgerJournal> findAllByOrderByPostedAtDesc(Pageable pageable);

    long count();

    boolean existsByReversesJournalGroupId(UUID journalGroupId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN lj.direction = com.medpay.ledger.model.LedgerDirection.DEBIT
                                     THEN lj.amount ELSE -lj.amount END), 0)
            FROM LedgerJournal lj
            WHERE lj.journalGroupId = :journalGroupId
            """)
    BigDecimal sumSignedAmountByGroup(@Param("journalGroupId") UUID journalGroupId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN lj.direction = com.medpay.ledger.model.LedgerDirection.CREDIT
                                     THEN lj.amount ELSE -lj.amount END), 0)
            FROM LedgerJournal lj
            WHERE lj.providerAccount.id = :providerAccountId
              AND lj.accountType = com.medpay.ledger.model.LedgerAccountType.PROVIDER_PAYABLE
            """)
    BigDecimal sumProviderPayableBalance(@Param("providerAccountId") Long providerAccountId);
}
