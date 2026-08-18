package com.medpay.ledger.repository;

import com.medpay.ledger.model.FeeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, Long> {

    @Query("""
            SELECT fs FROM FeeSchedule fs
            WHERE fs.serviceCode = :serviceCode
              AND fs.effectiveFrom <= :serviceDate
              AND (fs.effectiveTo IS NULL OR fs.effectiveTo >= :serviceDate)
            """)
    Optional<FeeSchedule> findRateFor(@Param("serviceCode") String serviceCode,
                                      @Param("serviceDate") LocalDate serviceDate);

    @Query("""
            SELECT fs FROM FeeSchedule fs
            WHERE fs.effectiveFrom <= :onDate
              AND (fs.effectiveTo IS NULL OR fs.effectiveTo >= :onDate)
            ORDER BY fs.serviceCode ASC
            """)
    List<FeeSchedule> findAllEffectiveOn(@Param("onDate") LocalDate onDate);
}
