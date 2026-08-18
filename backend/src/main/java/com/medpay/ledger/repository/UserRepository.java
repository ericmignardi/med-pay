package com.medpay.ledger.repository;

import com.medpay.ledger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserUuid(UUID userUuid);

    boolean existsByEmailIgnoreCase(String email);
}
