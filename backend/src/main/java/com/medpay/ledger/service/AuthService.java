package com.medpay.ledger.service;

import com.medpay.ledger.dto.LoginRequest;
import com.medpay.ledger.dto.LoginResponse;
import com.medpay.ledger.model.User;
import com.medpay.ledger.repository.UserRepository;
import com.medpay.ledger.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Credential exchange (FR-001). */
@Service
public class AuthService {

    /**
     * A real BCrypt hash at the same cost factor as the seeded users, of a value
     * no one holds. An unknown email is verified against this rather than
     * short-circuiting, so the ~250 ms BCrypt cost is paid on both paths and
     * response timing does not disclose whether an account exists (NFR-004).
     */
    private static final String DUMMY_HASH =
            "$2a$12$ptYuQOqlDKviqT4Ze4dgy.3oCWlbxfvYJ1xT5Eh9mHd4EuOx9r/tK";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> candidate = userRepository.findByEmailIgnoreCase(request.email());

        String storedHash = candidate.map(User::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);

        // Both causes raise the same exception with the same message, so the
        // 401 body is byte-identical for an unknown email and a wrong password.
        if (candidate.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = candidate.get();
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }

        JwtTokenProvider.IssuedToken issued = tokenProvider.issue(user);

        return new LoginResponse(
                issued.token(),
                issued.expiresAt(),
                user.getUserUuid(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles().stream().map(Enum::name).sorted().toList());
    }
}
