# MedPay Ledger — Implementation Plan

Derived from `PRD.md`. Ten phases, each with an explicit exit criterion. A phase is not complete until its exit criterion passes — no phase begins with the previous one's tests red.

**Sequencing principle.** The ledger invariants are the highest-risk part of the system and the hardest to retrofit, so they land early (Phase 4) and every later phase runs against them. Authentication precedes everything because every subsequent endpoint's tests need a principal. The frontend does not begin until the API contract is frozen at the end of Phase 7.

**Requirement coverage.** Every FR and NFR in the PRD appears in exactly one phase's table below. Nothing is unassigned.

---

## Phase 0 — Repository, Toolchain, Container Baseline

Scaffolding only. No business logic.

| Task | Artifact |
|---|---|
| Maven project, Spring Boot 3.3.4 parent, Java 21 toolchain | `backend/pom.xml` |
| Dependencies: web, data-jpa, security, validation, flyway, postgresql, actuator, jjwt 0.12.6, spring-retry | `backend/pom.xml` |
| Application entry point with `@EnableScheduling` and `@EnableRetry` | `MedPayLedgerApplication.java` |
| Profile-split configuration: `application.yml` + `dev`/`demo`/`prod` | `backend/src/main/resources/` |
| Vite + React 18 + TS 5 scaffold, `strict: true`, `noUncheckedIndexedAccess: true` | `frontend/` |
| Tailwind 3.4, lucide-react, axios, decimal.js, react-router-dom | `frontend/package.json` |
| ESLint rule banning `any` and `BigDecimal`-unsafe number parsing | `frontend/eslint.config.js` |
| Multi-stage Dockerfiles, both non-root at runtime | `backend/Dockerfile`, `frontend/Dockerfile` |
| `nginx.conf` — `/api/v1` proxy, CSP, SPA fallback, `/healthz` | `frontend/nginx.conf` |
| `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example` | repository root |
| `.gitignore` covering `.env`, `target/`, `node_modules/`, `dist/` | repository root |
| `gitleaks` pre-commit hook | `.pre-commit-config.yaml` |

**Exit criterion.** `docker compose up --build --wait` reaches healthy on all three services. `GET localhost:8080/actuator/health` returns `{"status":"UP"}` through the Nginx proxy. The SPA renders a placeholder route.

**Covers:** NFR-007 (partial — tooling), NFR-016.

---

## Phase 1 — Schema & Persistence

The full schema lands in one migration. Splitting it across phases would mean rewriting migrations, which Flyway forbids once applied.

| Task | Artifact |
|---|---|
| All eight tables with every constraint and index from PRD §4.10 | `V1__initial_schema.sql` |
| Fee schedule reference data (synthetic CPT-shaped codes with contracted rates) | `V2__seed_fee_schedules.sql` |
| Three demo users, BCrypt hashes at strength 12, role grants | `V3__seed_users_and_roles.sql` |
| Synthetic provider accounts, valid-format NPIs, zero balances | `V4__seed_provider_accounts.sql` |
| `medpay_app` role creation and table grants | `V5__create_app_role.sql` |
| `REVOKE UPDATE, DELETE ON ledger_journals` | `V6__ledger_append_only_grants.sql` |
| JPA entities: `User`, `Role`, `ProviderAccount`, `FeeSchedule`, `Claim`, `ClaimLine`, `LedgerJournal`, `OutboxEvent` | `backend/src/main/java/com/medpay/ledger/*/` |
| Repositories; `LedgerJournalRepository` narrowed to `Repository<>` with no delete methods | same |
| `AbstractIntegrationTest` with a singleton `@ServiceConnection` PostgreSQL 16 container | `backend/src/test/java/…/testsupport/` |

**Watch for.** `ddl-auto` must be `validate` in every profile including tests — a stray `update` silently masks entity/schema drift and defeats the point of Phase 1. Verify `TIMESTAMPTZ` maps to `Instant` and not `LocalDateTime` on every temporal column.

**Exit criterion.** Flyway `V1`–`V6` applies cleanly against a fresh container. Hibernate `validate` passes at boot with zero warnings. `LedgerAppendOnlyTest` proves `UPDATE` and `DELETE` on `ledger_journals` are refused for `medpay_app`.

**Covers:** FR-015 (schema half), NFR-011, NFR-012.

---

## Phase 2 — Authentication & Security Filter Chain

| Task | Artifact |
|---|---|
| `SecurityConfig` — `SecurityFilterChain` bean, stateless, `@EnableMethodSecurity`, BCrypt(12) | `security/SecurityConfig.java` |
| `JwtTokenProvider` — HS256 issue/parse, fail-fast on a key under 32 bytes | `security/JwtTokenProvider.java` |
| `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter` | `security/JwtAuthenticationFilter.java` |
| `AuthenticatedUser` carrying `userId` and `userUuid` — the self-approval check depends on this | `security/AuthenticatedUser.java` |
| `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` | `security/` |
| `AuthService` with dummy-hash timing parity on unknown email | `auth/AuthService.java` |
| `POST /auth/login`, `GET /auth/me` | `auth/AuthController.java` |
| `@WithMockCustomUser` + `WithMockCustomUserSecurityContextFactory` | `testsupport/` |
| `dev`-profile-only `CorsConfigurationSource` | `security/CorsConfig.java` |

**Watch for.** Spring Security 6 removed `WebSecurityConfigurerAdapter`, `authorizeRequests()`, and `antMatchers()` — use the `SecurityFilterChain` bean, `authorizeHttpRequests()`, and `requestMatchers()`. All servlet imports are `jakarta.*`. `@WithMockCustomUser` must populate `userUuid`, or Phase 6's self-approval test will pass for the wrong reason.

**Exit criterion.** `AuthControllerTest` green, including identical `401 INVALID_CREDENTIALS` for unknown email and wrong password. A token issued by `/auth/login` authenticates against a protected stub endpoint. An expired token yields `401` and never a `500`.

**Covers:** FR-001, FR-002, FR-003, FR-004, NFR-001, NFR-002, NFR-004, NFR-006.

---

## Phase 3 — Claim Intake, Validation & Fingerprinting

Everything up to but not including the threshold decision.

| Task | Artifact |
|---|---|
| `ClaimSubmissionRequest` / `ClaimLineRequest` records with the full Bean Validation set | `claim/dto/` |
| `MoneyMath` — `normalize`, `allowedFor`, `equalToTheCent` | `adjudication/MoneyMath.java` |
| `ClaimValidator#assertLineSumMatchesHeader` → `422 LINE_SUM_MISMATCH` | `claim/ClaimValidator.java` |
| `FeeScheduleService#rateFor(code, date)` with effective-date range; miss → `422 UNKNOWN_SERVICE_CODE` | `feeschedule/FeeScheduleService.java` |
| Lesser-of allowed derivation and residual patient responsibility, line and header | `adjudication/` |
| `ClaimFingerprintCalculator` — canonicalize, SHA-256 hex | `claim/ClaimFingerprintCalculator.java` |
| Idempotency-key handling on `(submitted_by_user_id, idempotency_key)` | `claim/ClaimSubmissionService.java` |
| `GlobalExceptionHandler` with the full envelope, PHI-field `rejectedValue` suppression | `common/error/` |
| `CorrelationIdFilter` writing to the MDC | `common/logging/` |
| `GET /fee-schedules` | `feeschedule/FeeScheduleController.java` |

**Watch for.** `BigDecimal.equals` must not appear anywhere — add a static-analysis rule now rather than after Phase 4. The line-sum check runs *before* the fee-schedule lookup, so a claim that is both unbalanced and carries an unknown code returns `LINE_SUM_MISMATCH`, matching PRD §3.2.

**Exit criterion.** `AdjudicationBoundaryTest` green for TC-B-005 through TC-B-012 (the validation and invariant rows). Fingerprint canonicalization is order-independent — the same codes in a different sequence produce an identical digest.

**Covers:** FR-006, FR-007 (persistence in Phase 5), FR-008, FR-009, FR-010, FR-026, FR-030, NFR-005.

---

## Phase 4 — Ledger Core

The highest-risk phase. It is built and proven before any endpoint can post to it.

| Task | Artifact |
|---|---|
| `LedgerAccountType`, `LedgerDirection` enums | `ledger/` |
| `LedgerJournal` factory with `signedAmount()`, all columns `updatable = false`, no setters | `ledger/LedgerJournal.java` |
| `LedgerPostingService#postAdjudication` — `@Transactional(MANDATORY)`, balanced-pair assertion | `ledger/LedgerPostingService.java` |
| `ProviderAccount#accrue` / `#recoup` with the overdraw guard | `provider/ProviderAccount.java` |
| Reversal posting: inverted directions, new group, `reversesJournalGroupId` set | `review/ReversalService.java` |
| `LedgerInvariantTest`, `LedgerAppendOnlyTest`, `LedgerBalanceReconciliationTest` | `backend/src/test/` |

**Watch for.** `TxType.MANDATORY` is the guard that makes a non-atomic ledger post impossible — verify it throws when called outside a transaction, rather than assuming it. Amounts are always positive; sign lives in `direction` alone. Reversal must never touch original rows, and `ux_ledger_journals_reverses` must reject a second reversal of the same group.

**Exit criterion.** Every journal group in the database sums to zero after every test scenario. `provider_accounts.payable_balance` equals the signed journal sum per provider. A direct `UPDATE` and `DELETE` as `medpay_app` both fail. Calling `postAdjudication` without a transaction throws.

**Covers:** FR-014, FR-015 (application half).

---

## Phase 5 — Adjudication Engine & State Machine

| Task | Artifact |
|---|---|
| `ClaimStatus`, `ClaimEvent` enums | `claim/` |
| `ClaimStateMachine` transition map, `IllegalStateTransitionException` with `allowedEvents` | `claim/ClaimStateMachine.java` |
| `Claim#apply(ClaimEvent)` as the only status mutation path — no setter | `claim/Claim.java` |
| `AdjudicationPolicy.REVIEW_THRESHOLD` — the single `25000.00` literal in the tree | `adjudication/AdjudicationPolicy.java` |
| `AdjudicationService` — threshold branch, ledger post below, hold at or above | `adjudication/AdjudicationService.java` |
| `ClaimSubmissionService#submit` wiring validation → adjudication → outbox | `claim/ClaimSubmissionService.java` |
| `@Retryable` on optimistic lock failure, 3 attempts, 50 ms backoff | `adjudication/AdjudicationService.java` |
| `POST /claims` (`201`/`202`), `GET /claims`, `GET /claims/{uuid}` | `claim/ClaimController.java` |
| `PageResponse`, `PageRequestFactory` with the size clamp | `common/page/` |

**Watch for.** Exactly `25000.00` **holds** — the comparison is `compareTo(THRESHOLD) < 0` for auto-adjudication. A scale variant (`25000.0`) must behave identically, which is the reason `equals` is banned. `201` for adjudicated, `202` for flagged; a flagged claim carries populated `allowedAmount` because the fee-schedule pass runs before the branch.

**Exit criterion.** The complete TC-B-001 … TC-B-012 matrix is green. `ClaimStateMachineTest` covers every illegal transition in PRD §3.3. A sub-threshold submission produces exactly two journal rows; an at-or-above submission produces zero.

**Covers:** FR-011, FR-012, FR-013, FR-025.

---

## Phase 6 — Review, Separation of Duties & Reversal

| Task | Artifact |
|---|---|
| `GET /review/queue` with `@EntityGraph` fetch join, oldest first | `review/ReviewController.java` |
| `GET /review/claims/{uuid}` scoped to `FLAGGED_REVIEW` | same |
| `ReviewService#approve` — self-approval check, transition, ledger post, stamps | `review/ReviewService.java` |
| `ReviewService#deny` — mandatory reason enum and note, no ledger impact | same |
| `SelfApprovalException` → `409 SELF_APPROVAL_FORBIDDEN` | `review/` |
| `POST /claims/{uuid}/reversals`, `MEDICAL_REVIEWER` only, `PAID` only | `review/ReversalService.java` |
| `@PreAuthorize` on every endpoint per the PRD §2.2 matrix | all controllers |
| `ReviewControllerTest` including the both-roles self-approval case | `backend/src/test/` |

**Watch for.** The self-approval check compares `submitted_by_user_id` to the principal's UUID and runs **before** the state machine, so the `409` is `SELF_APPROVAL_FORBIDDEN` and not `ILLEGAL_STATE_TRANSITION`. The test that matters grants the principal **both** roles — passing only the processor-gets-403 test proves nothing about separation of duties.

**Exit criterion.** Every cell of the PRD §2.2 RBAC matrix has a passing test. TC-R-004 (both roles, own submission) returns `409 SELF_APPROVAL_FORBIDDEN`. Reversing a non-`PAID` claim returns `409`; reversing twice returns `409`.

**Covers:** FR-016, FR-017, FR-018, FR-019, FR-020.

---

## Phase 7 — Audit, Outbox & Concurrency

Closes the backend. The API contract is frozen at this phase's exit.

| Task | Artifact |
|---|---|
| `GET /audit/journals` with `Specification`-composed filters | `audit/JournalSpecifications.java` |
| `GET /audit/claims/{uuid}` — claim, lines, journal groups, event stream | `audit/AuditController.java` |
| `OutboxEvent` writes inside every mutating transaction | `outbox/` |
| `OutboxDispatcher` — `@Scheduled(fixedDelay = 5000)`, `FOR UPDATE SKIP LOCKED` | `outbox/OutboxDispatcher.java` |
| `RemittanceAdviceLogSink` — the declared X12 835 seam | `outbox/RemittanceAdviceLogSink.java` |
| `PhiMaskingConverter` + `logback-spring.xml` using `%maskedMsg` everywhere | `common/logging/` |
| Hikari sizing, `open-in-view: false`, batch and fetch tuning | `application-prod.yml` |
| `ConcurrencyIT` — double-submit, concurrent approval, concurrent provider posts | `backend/src/test/` |
| JaCoCo gate wired into `mvn verify` | `backend/pom.xml` |

> **Carried from Phase 0.** The JaCoCo plugin, its 80%/70% BUNDLE rule and its
> excludes are already in `backend/pom.xml`, but the gate is non-blocking:
> `<jacoco.haltOnFailure>` is `false`. At Phase 0 there is no production code to
> measure, so an enforcing gate fails `mvn verify` on an empty tree and would
> block every phase in between. The report is generated on every run regardless,
> so the ratio stays visible as coverage grows.
>
> **This phase flips that property to `true`** — that is the whole of "wired into
> `mvn verify`". Do it before the exit criterion below is assessed, or the
> criterion passes vacuously.

**Watch for.** `claimUnpublishedBatch` is the **only** sanctioned native query outside migrations, because `SKIP LOCKED` has no JPQL form. Confirm no PHI-adjacent value reaches any appender — check the error path too, not just the happy path.

**Exit criterion.** Coverage gate passes at 80% line / 70% branch. `ConcurrencyIT` proves a double-submit yields one claim and one journal group, and that two concurrent approvals yield one `PAID` and one `409`. Outbox rows drain after a simulated dispatcher restart. **API contract frozen.**

**Done.** 178 tests green; gate enforcing at 93.7% line / 72.4% branch. Three defects
surfaced and were fixed rather than tested around:

1. `` sat on `AdjudicationService.adjudicate`, a `MANDATORY`-propagation method
   *inside* the caller transaction. Once a flush fails the transaction is doomed, so the
   retry could never succeed. It now lives at the transaction boundary in
   `service/ClaimIntake.java`, a non-transactional delegate the controller calls. Under
   six-way contention on one provider row this moved the accepted rate from 1/6 to 4/6;
   the remainder get `409 CONCURRENT_MODIFICATION`, which is the documented outcome.
2. `ClaimSubmissionService` caught `DataIntegrityViolationException` and kept querying in
   the same persistence context, producing `500` (`AssertionFailure: null identifier`) for
   the loser of a concurrent double-submit. The collision now propagates and the retry
   resolves it in a fresh transaction.
3. `JournalSpecifications.compose` passed nulls to `Specification.allOf`, which rejects
   them — every unfiltered `/audit/journals` call was a `500`.

Also closed: stack traces bypassed `%maskedMsg` entirely (FR-030 error path) — see
`PhiMaskingThrowableConverter`; the JaCoCo excludes used `**/dto/**` against flat packages
and matched nothing; there was no `report` goal at all; and journal amounts serialized at
the NUMERIC(19,4) storage scale (`"125.0000"`) instead of the two-decimal contract.

**Covers:** FR-021, FR-022, FR-023, FR-024, NFR-003, NFR-008, NFR-009, NFR-010, NFR-013.

---

## Phase 8 — Frontend

Begins only after the contract is frozen, so the TypeScript interfaces in PRD §5 are transcribed rather than guessed at.

| Task | Artifact |
|---|---|
| `types/api.ts` — every interface from PRD §5, money typed `string` | `frontend/src/types/` |
| `apiClient.ts` — JWT request interceptor, `401` response interceptor; `403` deliberately not intercepted | `frontend/src/api/` |
| `AuthContext` + `sessionStorage` persistence, rehydrated via `GET /auth/me` | `frontend/src/auth/` |
| `ProtectedRoute` with `requiredRole`, return-path preservation | `frontend/src/auth/` |
| `formatMoney.ts` — `decimal.js` parse, `Intl.NumberFormat` render | `frontend/src/lib/` |
| Ten routes per PRD §3.8 with empty, loading, and error states each | `frontend/src/pages/` |
| `/claims/new` live line-sum indicator computed in `decimal.js` | `frontend/src/pages/ClaimSubmitPage.tsx` |
| Distinct error surfaces for `409` duplicate, `409` self-approval, `422` line mismatch | `frontend/src/components/` |
| Focus rings, `aria-live` result region, label association, non-colour status badges | throughout |
| Responsive: line editor collapses to cards below `md`; journal table scrolls in-container | throughout |

**Watch for.** Never parse a money field with `Number()` or `parseFloat` — the ESLint rule from Phase 0 should catch it, but review for it explicitly. The client-side line-sum check is a convenience that prevents the round trip; the server check stays authoritative and must still be exercised by a test.

**Exit criterion.** All ten routes render with correct empty, loading, and error states. `tsc --noEmit` and lint are clean. A processor navigating to `/review` lands on `/403`. Money renders identically to the API decimal strings at two decimal places, with no float artifacts.

**Done.** `tsc --noEmit`, `eslint .` and `vite build` all clean on the first pass; the
container serves the SPA and the deep link `/claims` returns 200 through the Nginx
fallback. Route rendering and the `/403` redirect are verified by Phase 9 rather than
by hand.

**One documented deviation.** The plan called for `Intl.NumberFormat` rendering.
`Intl.NumberFormat.prototype.format` takes a `number` in the TypeScript DOM lib, and
money columns are `NUMERIC(19,4)` — a fifteen-digit integer part exceeds
`Number.MAX_SAFE_INTEGER`, so routing through `Intl` would silently round exactly the
largest amounts. `lib/money.ts` groups digits by string manipulation instead; output is
identical to `en-CA` currency formatting and nothing is ever a `number`.

**Covers:** FR-005, FR-027, FR-028, FR-029, NFR-014, NFR-015, NFR-017.

---

## Phase 9 — End-to-End Verification

| Task | Artifact |
|---|---|
| `auth.spec.ts` — login success/failure, deep-link redirect with return path | `frontend/e2e/` |
| `persistence.spec.ts` — JWT survives reload, nav rehydrates | same |
| `expiry.spec.ts` — expired token → interceptor → `/login?expired=1` | same |
| `route-protection.spec.ts` — UI redirect *and* a direct `fetch` returning `403` | same |
| `cross-role-flow.spec.ts` — $60k submit → approve → auditor sees two balanced lines | same |
| `playwright.config.ts` with `webServer` waiting on `/actuator/health` | `frontend/` |

**Watch for.** `route-protection.spec.ts` must assert the raw API call is rejected, not only that the UI redirects — client-side guarding is UX, and a spec that tests only the redirect would pass against a server with no authorization at all.

**Exit criterion.** All five specs green against `docker compose up` in the `demo` profile. The cross-role spec asserts exactly two journal lines, correct directions and account types, and a group balance of `0.00`.

**Covers:** FR-005, FR-027, FR-028 (verification).

---

## Phase 10 — CI/CD & Azure

| Task | Artifact |
|---|---|
| `deploy.yml` — four jobs: backend test, frontend test, build/push, deploy | `.github/workflows/` |
| OIDC federated credential, `id-token: write`, no stored client secret | same |
| Commit-SHA image tags; `latest` pushed but never deployed by reference | same |
| Post-deploy health poll with a bounded retry and non-zero exit on failure | same |
| ACR, App Service plan, App Service, PostgreSQL Flexible Server (SSL enforced), Key Vault | Azure, Canada Central |
| System-assigned managed identity with a Key Vault `get` policy and ACR pull | Azure |
| Key Vault references for `MEDPAY_JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD` | App Service settings |
| Budget alert at the stated monthly threshold | Azure Cost Management |
| Dependabot on `maven` and `npm`, weekly | `.github/dependabot.yml` |
| `gitleaks` scan step in CI | `deploy.yml` |

**Watch for.** Verify no secret appears in a build argument, an image layer, or a workflow log — Key Vault references resolve at container start, not at build. Confirm `sslmode=require` is present in the deployed `SPRING_DATASOURCE_URL`, since NFR-003 has no automated test and is a declared gap.

**Exit criterion.** A push to `main` builds, tests, pushes, deploys, and passes the health poll with no manual step. The public URL serves the SPA; the demo credentials authenticate; the cross-role flow completes against the deployed environment. Redeploying a prior commit SHA rolls back cleanly.

**Covers:** NFR-007, NFR-018, NFR-019.

---

## Requirement Coverage Check

| Phase | FRs | NFRs |
|---|---|---|
| 0 | — | 007 (partial), 016 |
| 1 | 015 (schema) | 011, 012 |
| 2 | 001, 002, 003, 004 | 001, 002, 004, 006 |
| 3 | 006, 007, 008, 009, 010, 026, 030 | 005 |
| 4 | 014, 015 (application) | — |
| 5 | 011, 012, 013, 025 | — |
| 6 | 016, 017, 018, 019, 020 | — |
| 7 | 021, 022, 023, 024 | 003, 008, 009, 010, 013 |
| 8 | 005, 027, 028, 029 | 014, 015, 017 |
| 9 | 005, 027, 028 (verification) | — |
| 10 | — | 007, 018, 019 |

FR-001 … FR-030 and NFR-001 … NFR-019 each appear at least once. The declared gaps in PRD §9.1 remain gaps — this plan schedules their manual verification (Phases 8 and 10) but does not claim automated coverage for them.

---

## Critical Path

```
Phase 0 ─→ Phase 1 ─→ Phase 2 ─→ Phase 3 ─→ Phase 4 ─→ Phase 5 ─→ Phase 6 ─→ Phase 7
                                                                                  │
                                                                    (contract frozen)
                                                                                  ↓
                                                              Phase 8 ─→ Phase 9 ─→ Phase 10
```

Phases 0–7 are strictly sequential; each depends on the previous phase's artifacts. Phase 8 can begin in parallel with Phase 7 **only** for `types/api.ts` and static layout, since those depend on the contract in PRD §5 rather than on running endpoints — but no page should be wired to a live call before Phase 7 exits.

The two phases most likely to overrun are 4 and 6: Phase 4 because the append-only grant interacts with Flyway's own role, and Phase 6 because the self-approval test requires `@WithMockCustomUser` to carry a real `userUuid`, which is easy to stub incorrectly in a way that makes the test pass without proving anything.

---

## Phase 10 — DEFERRED (no Azure subscription yet)

**Status as of 2026-08-18.** Phase 10 is split. The half that needs no Azure
account is still in scope and should be built with Phases 8–9. The half that
provisions cloud resources is parked until a free-tier subscription exists.

### 10a — Buildable now, no account required

| Task | Artifact |
|---|---|
| `deploy.yml` jobs 1–2: backend `mvn verify`, frontend `tsc --noEmit` + lint + build | `.github/workflows/deploy.yml` |
| `gitleaks` scan step | same |
| Dependabot on `maven` and `npm`, weekly | `.github/dependabot.yml` |
| Build/push and deploy jobs written but gated on `if: ${{ vars.AZURE_ENABLED == 'true' }}` | `deploy.yml` |

Gating on a repository variable means the workflow is committed, reviewed, and
green on every push now, and turning on deployment later is a one-click change
rather than a new authoring pass.

### 10b — Blocked on the subscription

Do these in order once the account is ready. Each line is the actual step, not a
summary of one.

1. **Create the resource group** in Canada Central (`medpay-rg`). Every resource
   below goes in it so teardown is a single delete.
2. **Provision ACR** (Basic SKU), **App Service plan** (B1 — the free F1 tier
   cannot pull from ACR and has no always-on, which breaks the outbox
   dispatcher's `@Scheduled` loop), **App Service** (Linux container),
   **PostgreSQL Flexible Server** (Burstable B1ms, `require_secure_transport`
   ON), and **Key Vault**.
3. **Enable the App Service's system-assigned managed identity.** Grant it
   `AcrPull` on the registry and a Key Vault access policy limited to `get` on
   secrets. No stored registry password, no connection string in app settings.
4. **Store two secrets in Key Vault** — `MEDPAY-JWT-SECRET` (32+ bytes, or
   `JwtTokenProvider` fails fast at boot by design) and
   `SPRING-DATASOURCE-PASSWORD`. Reference them from App Service settings as
   `@Microsoft.KeyVault(SecretUri=...)`, never as literals.
5. **Set `SPRING_DATASOURCE_URL` with `sslmode=require`.** NFR-003 has no
   automated test and is a declared gap in PRD §9.1 — this is the manual
   verification for it. Confirm by checking the connection is refused without it.
6. **Configure the GitHub OIDC federated credential** on an Entra app
   registration scoped to `repo:<owner>/medpay:ref:refs/heads/main`, then set
   `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` as repository
   variables. The workflow needs `permissions: id-token: write`. No client
   secret is ever created.
7. **Flip `AZURE_ENABLED` to `true`** to activate the gated jobs from 10a.
8. **Set a budget alert** in Cost Management at the monthly threshold before the
   first deploy, not after.

**Verification once unblocked.** Push to `main`; the workflow must build, test,
push a commit-SHA-tagged image, deploy, and pass the bounded health poll with no
manual step. Then: the public URL serves the SPA, the demo credentials
authenticate, and the Phase 9 cross-role flow completes against the deployed
environment. Finally, redeploy a prior commit SHA and confirm it rolls back
cleanly — `latest` is pushed but never deployed by reference, so the rollback
must resolve the SHA tag.

**Check before declaring done.** No secret in a build argument, an image layer,
or a workflow log. Key Vault references resolve at container start, not at build
time — a secret that appears at build time has leaked into the image.
