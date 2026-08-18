package com.medpay.ledger.model;

import com.medpay.ledger.util.MoneyMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "provider_accounts")
@Getter
@Setter
@NoArgsConstructor
public class ProviderAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "provider_npi", nullable = false, unique = true, length = 10, updatable = false)
    private String providerNpi;

    @Column(name = "provider_name", nullable = false, length = 200)
    private String providerName;

    @Column(name = "payable_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal payableBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public void accrue(BigDecimal amount) {
        assertPositive(amount);
        payableBalance = scaled(payableBalance.add(amount));
    }

    public void recoup(BigDecimal amount) {
        assertPositive(amount);
        BigDecimal reduced = scaled(payableBalance.subtract(amount));
        if (reduced.signum() < 0) {
            throw new IllegalStateException(
                    "Recoupment of " + amount + " would overdraw provider " + providerNpi
                            + " whose payable balance is " + payableBalance);
        }
        payableBalance = reduced;
    }

    private static void assertPositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Balance movements are positive amounts, got " + amount);
        }
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(MoneyMath.STORAGE_SCALE, RoundingMode.HALF_UP);
    }
}
