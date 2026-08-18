package com.medpay.ledger.security;

import com.medpay.ledger.model.Role;
import com.medpay.ledger.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final int MINIMUM_KEY_BYTES = 32;

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_FULL_NAME = "name";
    private static final String CLAIM_ROLES = "roles";

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final Duration timeToLive;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.ttl-seconds:3600}") long ttlSeconds) {

        byte[] keyMaterial = decode(secret);
        if (keyMaterial.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret decodes to " + keyMaterial.length
                            + " bytes; HS256 requires at least " + MINIMUM_KEY_BYTES
                            + ". Generate one with: openssl rand -base64 48");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalStateException(
                    "app.jwt.ttl-seconds must be positive, was " + ttlSeconds);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyMaterial);
        this.timeToLive = Duration.ofSeconds(ttlSeconds);
    }

    private static byte[] decode(String secret) {
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public IssuedToken issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(timeToLive);

        List<String> roleNames = user.getRoles().stream().map(Enum::name).sorted().toList();

        String token = Jwts.builder()
                .subject(user.getUserUuid().toString())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_FULL_NAME, user.getFullName())
                .claim(CLAIM_ROLES, roleNames)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number userId = claims.get(CLAIM_USER_ID, Number.class);
            String email = claims.get(CLAIM_EMAIL, String.class);
            if (userId == null || email == null) {
                return Optional.empty();
            }
            String fullName = claims.get(CLAIM_FULL_NAME, String.class);

            return Optional.of(new AuthenticatedUser(
                    userId.longValue(),
                    UUID.fromString(claims.getSubject()),
                    email,
                    fullName != null ? fullName : email,
                    rolesFrom(claims)));

        } catch (JwtException | IllegalArgumentException rejected) {
            log.debug("Rejected bearer token: {}", rejected.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Set<Role> rolesFrom(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (raw instanceof List<?> values) {
            for (Object value : values) {
                try {
                    roles.add(Role.valueOf(String.valueOf(value)));
                } catch (IllegalArgumentException unknownRole) {
                    log.debug("Dropping unrecognised role claim");
                }
            }
        }
        return roles;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
