package com.medpay.ledger.repository;

import com.medpay.ledger.model.ProviderAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProviderAccountRepository extends JpaRepository<ProviderAccount, Long> {

    Optional<ProviderAccount> findByProviderNpi(String providerNpi);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<ProviderAccount> findWithLockByProviderNpi(String providerNpi);
}
