# MedPay Ledger

**A distributed medical claims processing and audit engine.** Payer-side claim intake, deterministic adjudication against a contracted fee schedule, high-cost clinical review with enforced separation of duties, and provider remittance posted to an append-only double-entry ledger.

Java 21 · Spring Boot 3.3 · Spring Security 6 · PostgreSQL 16 · React 18 · TypeScript 5 · Vite 5 · Docker · Azure

---

## Disclaimer

MedPay Ledger is a reference implementation / portfolio system. It simulates payer-side claims adjudication and provider remittance against an internal double-entry ledger. It does not process real Protected Health Information, does not connect to a clearinghouse or any real payment rail, does not disburse real funds, and is not certified for HIPAA, HITRUST, SOC 2, or any healthcare regulatory regime. All member and provider data is synthetic. Where a production system would require X12 837/835 EDI ingestion and remittance, 270/271 eligibility verification, NPI registry validation, a Business Associate Agreement, or a HIPAA-compliant audit and breach-notification program, `PRD.md` defines the interface seam where that integration would attach, and marks it explicitly as out of scope.

---

## What it does

A claims processor submits a professional claim with one or more service lines. The engine validates that the line amounts sum to the header amount **to the cent**, looks up each line's contracted rate on the claim's date of service, and derives the allowed amount as the lesser of billed or contracted rate. Then one rule decides everything downstream:

| Billed amount | Outcome |
|---|---|
| `< $25,000.00` | Adjudicates immediately. A balanced journal pair posts in the same database transaction. Claim is `PAID`. |
| `>= $25,000.00` | Holds. No ledger impact whatsoever. Claim is `FLAGGED_REVIEW` until a medical reviewer approves or denies it. |

Exactly `$25,000.00` **holds** — the auto-adjudication rule is strictly less-than.

Approval posts the pair. Denial posts nothing. A paid claim can be reversed, which posts a **compensating** pair under a new journal group rather than editing anything. No row in `ledger_journals` is ever updated or deleted — the application's database role has no `UPDATE` or `DELETE` grant on that table, and a test asserts PostgreSQL refuses both.

Three roles, and one of them cannot approve its own work:

| Role | Can do |
|---|---|
| `CLAIMS_PROCESSOR` | Submit claims, read their own submissions |
| `MEDICAL_REVIEWER` | Read the flagged queue, approve, deny, reverse |
| `AUDITOR` | Read every journal line and every claim's full lifecycle. Write nothing, ever |

A user holding **both** processor and reviewer roles still cannot approve a claim they submitted. The check is on `submitted_by_user_id` versus the authenticated principal, not on role — so granting the role does not defeat it. `409 SELF_APPROVAL_FORBIDDEN`.

---

## Quick start

**Requires** Docker 24+ and Docker Compose v2. Nothing else — Java and Node are only needed if you want to run the services outside containers.

```bash
git clone <repository-url> medpay-ledger
cd medpay-ledger

cp .env.example .env

# The JWT secret must decode to at least 32 bytes or the backend fails at
# startup by design (fail fast, not at first login).
openssl rand -base64 48

# Paste that value into MEDPAY_JWT_SECRET in .env, then:
docker compose up --build --wait
```

Open **http://localhost:8080**.

The stack starts in the `demo` profile, which seeds sample claims across every status alongside the reference data. Flyway runs migrations `V1`–`V6` on backend startup, including the grant revocation that makes the ledger append-only.

### Demo credentials

All three accounts are seeded by `V3__seed_users_and_roles.sql`. The data is entirely synthetic; these credentials are intentionally public.

| Role | Email | Password |
|---|---|---|
| Claims processor | `processor@medpay.test` | `Demo!Pass123` |
| Medical reviewer | `reviewer@medpay.test` | `Demo!Pass123` |
| Auditor | `auditor@medpay.test` | `Demo!Pass123` |

### The 90-second tour

The fastest way to see the whole system work:

1. Sign in as **processor**. Go to `/claims/new` and submit a claim for `$60,000.00` with one line at the same amount. It returns `202` and lands in `FLAGGED_REVIEW` with **zero** journal entries.
2. Try to approve it as the processor. `403` — the endpoint requires `MEDICAL_REVIEWER`.
3. Sign in as **reviewer**. The claim is at the top of `/review`, oldest first. Approve it. It becomes `PAID`.
4. Sign in as **auditor**. Open `/audit/claims/{uuid}`. Two journal lines: a `DEBIT` to `PAYER_CLAIMS_EXPENSE` and a `CREDIT` to `PROVIDER_PAYABLE`, same `journalGroupId`, netting to `0.00`.
5. Back as **reviewer**, reverse the claim. The original two lines are untouched; two new lines appear in a second group carrying `reversesJournalGroupId`. Net position across both groups is zero, and the whole history stays visible.

Then submit `$24,999.99` and watch it go straight to `PAID` without ever touching the queue.

---

## Local development

Containers are the reference environment, but hot reload is faster for UI work.

```bash
# Backend on :8080 against the compose database
docker compose up -d db
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Frontend on :5173 with Vite's dev proxy to :8080
cd frontend && npm ci && npm run dev
```

This is the only situation in which CORS exists in this system — `MEDPAY_CORS_ALLOWED_ORIGINS` permits `http://localhost:5173` under the `dev` and `demo` profiles. In production Nginx proxies `/api/v1`, the browser sees a single origin, and **no CORS bean is registered at all**.

### Tests

```bash
# Backend: unit, MockMvc slices, Testcontainers integration, JaCoCo gate
cd backend && ./mvnw verify

# Frontend: type check, lint, Playwright end-to-end
cd frontend
npx tsc --noEmit
npm run lint
npx playwright test
```

Integration tests use Testcontainers against `postgres:16-alpine` and run the real `V1`–`V6` migration chain. H2 is not on the classpath in any scope — the schema depends on partial unique indexes and `FOR UPDATE SKIP LOCKED`, neither of which H2 reproduces faithfully.

The coverage gate is **80% line / 70% branch** on the service, adjudication, ledger, review, and controller packages, enforced by JaCoCo in `mvn verify`. It fails the build; it is not advisory.

---

## Project structure

```
.
├── backend/                        Spring Boot 3.3, Java 21
│   ├── src/main/java/com/medpay/ledger/
│   │   ├── adjudication/           AdjudicationService, AdjudicationPolicy, MoneyMath
│   │   ├── audit/                  AuditController, JournalSpecifications
│   │   ├── auth/                   AuthController, AuthService
│   │   ├── claim/                  Claim, ClaimLine, ClaimStateMachine, ClaimValidator
│   │   ├── common/                 error envelope, pagination, PHI log masking
│   │   ├── feeschedule/            FeeScheduleService — contracted rate lookup
│   │   ├── ledger/                 LedgerJournal, LedgerPostingService  (append-only)
│   │   ├── outbox/                 OutboxDispatcher, RemittanceAdviceLogSink
│   │   ├── provider/               ProviderAccount
│   │   ├── review/                 ReviewService, ReversalService
│   │   ├── security/               SecurityConfig, JwtAuthenticationFilter
│   │   └── user/                   User, Role
│   ├── src/main/resources/db/migration/    V1__initial_schema.sql … V6__…
│   ├── src/test/java/…/testsupport/        @WithMockCustomUser
│   └── Dockerfile
├── frontend/                       React 18, TypeScript 5, Vite 5, Tailwind 3
│   ├── src/{api,auth,components,lib,pages,types}/
│   ├── e2e/                        Playwright specs
│   ├── nginx.conf                  /api/v1 proxy, CSP, SPA history fallback
│   └── Dockerfile
├── .github/workflows/deploy.yml    build → test → ACR push → App Service
├── docker-compose.yml              local dev (builds from source)
├── docker-compose.prod.yml         production (pulls tagged ACR images)
├── .env.example
├── PRD.md                          the specification — 10 sections, engineering-ready
└── PLAN.md                         phased implementation plan
```

---

## Design decisions worth knowing

Full reasoning is in `PRD.md` §0 (ADRs) and §10 (decisions made where the spec was silent). The ones that shape everything else:

**Money is `NUMERIC(19,4)` and `BigDecimal`, everywhere.** No `float`, no `double`, no `real`. Comparison is always `compareTo`, never `equals` — `new BigDecimal("25000.0").equals(new BigDecimal("25000.00"))` is `false` because the scales differ, and that bug would sit in the threshold check. Rounding is `HALF_UP` at scale 2 in every computation, so no reconciliation step ever meets two different modes.

**Money crosses the wire as a decimal string, not a JSON number.** A JSON number is an IEEE-754 double in JavaScript, which would reintroduce exactly the error the backend exists to avoid. The SPA parses with `decimal.js`.

**The ledger is append-only at the database-grant level**, not by convention. `V6__ledger_append_only_grants.sql` revokes `UPDATE` and `DELETE` from `medpay_app`. The JPA entity marks every column `updatable = false` and exposes no setters. The repository interface does not extend `JpaRepository`, so `deleteAll` is not even on the type. Three layers, and a test that asserts PostgreSQL refuses the write.

**A claim's status has no setter.** The only mutation path is `claim.apply(ClaimEvent)`, which routes through `ClaimStateMachine`. An illegal transition is unrepresentable rather than merely validated.

**Two different 409s guard two different duplicates.** An `Idempotency-Key` header deduplicates the same *request* (a retried network call), returning the original response. A SHA-256 `claim_fingerprint` over provider NPI, member reference, service date, and sorted service codes deduplicates the same *service encounter* submitted twice, returning `409 DUPLICATE_CLAIM` with the existing claim UUID. The fingerprint index is partial — it excludes `DENIED` and `REVERSED`, because a corrected encounter may legitimately be resubmitted.

**The JWT lives in `sessionStorage`, and the trade-off is stated rather than hidden.** It is attached only by an explicit Axios interceptor, so the browser never sends it cross-site and CSRF is structurally impossible — which is why `csrf().disable()` is correct here and would be a vulnerability with cookie auth. In exchange, the token is script-readable, so XSS becomes a credential-theft vector; the mitigation is a strict `Content-Security-Policy` with no `unsafe-inline` on scripts. An httpOnly cookie inverts exactly this pair.

**PHI-adjacent fields never reach a log.** `member_reference`, `diagnosis_code`, and `service_code` are masked by `PhiMaskingConverter` on every Logback appender, and the error envelope omits `rejectedValue` for those fields so a validation failure cannot echo one back.

**React + Vite, not Next.js.** Every route is authenticated, so there is no SEO or SSR surface and a Node server would have nothing to do. Nginx serves the static bundle and proxies the API.

---

## Deployment

Azure App Service for Linux Containers, multi-container from `docker-compose.prod.yml`, images from Azure Container Registry, database on Azure Database for PostgreSQL Flexible Server with SSL enforced.

```
push to main
  → backend tests (Testcontainers + JaCoCo gate)
  → frontend type check, lint, Playwright
  → docker build, tag with short commit SHA, push to ACR
  → az webapp config container set
  → poll /actuator/health until 200
```

CI authenticates to Azure by **OIDC federated credential** — `azure/login@v2` with `id-token: write` and no stored client secret. Images are tagged with the short commit SHA, so a rollback is a redeploy of a prior tag rather than a rebuild.

Secrets never enter the repository or an image. `MEDPAY_JWT_SECRET` and `SPRING_DATASOURCE_PASSWORD` are Azure Key Vault references resolved at container start by the App Service system-assigned managed identity. `.env` is git-ignored; `.env.example` carries placeholders only.

The full environment-variable table, including where each value comes from, is in `PRD.md` §7.7.

---

## Known limitations

Deliberate, documented, and listed in full in `PRD.md` §10.

- **`PAID` means the ledger pair is posted, not that money moved.** There is no disbursement step and no payment rail. `provider_accounts.payable_balance` accrues and is never drawn down.
- **No token refresh or revocation.** 60-minute TTL, re-login on expiry. A stolen token is valid for its remaining life.
- **No X12 EDI, no clearinghouse, no eligibility checks, no NPI registry validation, no coordination of benefits, no prior authorization.** Each has a named seam in the PRD.
- **`patient_responsibility` collapses two distinct real-world concepts** — the contractual write-off a provider absorbs and member cost-share — because the specified invariant admits only two terms. `FeeScheduleService` is the seam for benefit plan design.
- **No multi-payer tenancy.** There is no tenant column anywhere.
- **p95 < 200 ms is a design target, not a measured result.** No load test exists in v1.
- **Single App Service instance, single Nginx container, single database, no read replica.** Each is an accepted single point of failure, listed with its consequence in `PRD.md` §8.7.

---

## Documentation

| Document | Contents |
|---|---|
| `PRD.md` | The specification. Stack ADRs, RBAC matrix, 30 functional and 19 non-functional requirements, full DDL and JPA entities, complete API contract with paired Java records and TypeScript interfaces, testing strategy, Azure deployment, traceability matrix with declared gaps |
| `PLAN.md` | Phased implementation plan with per-phase exit criteria and requirement coverage |
