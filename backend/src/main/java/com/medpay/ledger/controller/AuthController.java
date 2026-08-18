package com.medpay.ledger.controller;

import com.medpay.ledger.dto.LoginRequest;
import com.medpay.ledger.dto.LoginResponse;
import com.medpay.ledger.dto.UserProfileResponse;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §5.1. {@code /login} is the only public endpoint besides the health probe. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Built entirely from the security context — no database read (FR-004). The
     * SPA calls this on mount to rehydrate role-aware navigation after a reload.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(new UserProfileResponse(
                principal.getUserUuid(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRoleNames()));
    }
}
