package com.medpay.ledger.controller;

import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-001 – FR-004, NFR-004. */
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String DEMO_PASSWORD = "Demo!Pass123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    @Test
    @DisplayName("FR-001: valid credentials return a token, the user UUID, and role names")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("processor@medpay.test", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.userUuid").value("a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91"))
                .andExpect(jsonPath("$.email").value("processor@medpay.test"))
                .andExpect(jsonPath("$.fullName").value("Priya Raman"))
                .andExpect(jsonPath("$.roles[0]").value("CLAIMS_PROCESSOR"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    @DisplayName("FR-001: login is case-insensitive on email")
    void loginIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("PROCESSOR@MedPay.TEST", DEMO_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("NFR-004: unknown email and wrong password produce byte-identical 401 bodies")
    void credentialFailuresAreIndistinguishable() throws Exception {
        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@medpay.test", DEMO_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("processor@medpay.test", "WrongPassword!1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        JsonNode first = objectMapper.readTree(unknownEmail.getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());

        // Everything but the timestamp must match: a differing message, code, or
        // detail set would disclose whether the account exists.
        assertThat(second.get("code")).isEqualTo(first.get("code"));
        assertThat(second.get("message")).isEqualTo(first.get("message"));
        assertThat(second.get("status")).isEqualTo(first.get("status"));
        assertThat(second.get("details")).isEqualTo(first.get("details"));
        assertThat(second.get("fieldErrors")).isEqualTo(first.get("fieldErrors"));
    }

    @Test
    @DisplayName("FR-006 envelope: a malformed login payload is 400 VALIDATION_FAILED with field errors")
    void malformedLoginIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    @DisplayName("FR-003: a token issued by /login authenticates against a protected endpoint")
    void issuedTokenAuthenticates() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("reviewer@medpay.test", DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseBody).get("token").asString();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("reviewer@medpay.test"))
                .andExpect(jsonPath("$.fullName").value("Dr. Marcus Oyelaran"))
                .andExpect(jsonPath("$.roles[0]").value("MEDICAL_REVIEWER"));
    }

    @Test
    @DisplayName("FR-004: /auth/me reflects the security-context principal")
    @WithMockCustomUser(email = "auditor@medpay.test", fullName = "Helena Vasquez",
            userUuid = "c3a0e6f4-9d5b-4a8c-9e37-4f0b2d8a5c13", userId = 3L, roles = "AUDITOR")
    void meReflectsThePrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userUuid").value("c3a0e6f4-9d5b-4a8c-9e37-4f0b2d8a5c13"))
                .andExpect(jsonPath("$.roles[0]").value("AUDITOR"));
    }

    @Test
    @DisplayName("no bearer token on a protected endpoint is 401 UNAUTHENTICATED")
    void missingTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    @Test
    @DisplayName("FR-003: an expired token is 401, never 500")
    void expiredTokenIsUnauthorizedNotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("FR-003: a token signed with the wrong key is 401, never 500")
    void forgedTokenIsUnauthorized() throws Exception {
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("uid", 1L)
                .claim("email", "attacker@medpay.test")
                .claim("roles", java.util.List.of("AUDITOR"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(
                        "an-entirely-different-key-of-at-least-32-bytes".getBytes()), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a garbage Authorization header is 401, never 500")
    void garbageTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** Signed with the real key so only the {@code exp} claim can be the cause of rejection. */
    private String expiredToken() {
        Instant issued = Instant.now().minusSeconds(7200);
        return Jwts.builder()
                .subject("a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91")
                .claim("uid", 1L)
                .claim("email", "processor@medpay.test")
                .claim("name", "Priya Raman")
                .claim("roles", java.util.List.of("CLAIMS_PROCESSOR"))
                .issuedAt(Date.from(issued))
                .expiration(Date.from(issued.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)), Jwts.SIG.HS256)
                .compact();
    }
}
