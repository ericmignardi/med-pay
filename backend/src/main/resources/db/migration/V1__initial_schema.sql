CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_uuid     UUID         NOT NULL,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(72)  NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_users_user_uuid UNIQUE (user_uuid)
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN
        ('CLAIMS_PROCESSOR', 'MEDICAL_REVIEWER', 'AUDITOR'))
);

CREATE TABLE provider_accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_npi    CHAR(10)      NOT NULL,
    provider_name   VARCHAR(200)  NOT NULL,
    payable_balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_provider_accounts_npi UNIQUE (provider_npi),
    CONSTRAINT ck_provider_npi_format   CHECK (provider_npi ~ '^[0-9]{10}$'),
    CONSTRAINT ck_provider_balance_sign CHECK (payable_balance >= 0)
);

CREATE TABLE fee_schedules (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_code    VARCHAR(5)    NOT NULL,
    description     VARCHAR(200)  NOT NULL,
    contracted_rate NUMERIC(19,4) NOT NULL,
    effective_from  DATE          NOT NULL,
    effective_to    DATE,
    CONSTRAINT ck_fee_code_format        CHECK (service_code ~ '^[A-Z0-9]{5}$'),
    CONSTRAINT ck_fee_rate_positive      CHECK (contracted_rate > 0),
    CONSTRAINT ck_fee_effective_range    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ux_fee_schedules_code_effective UNIQUE (service_code, effective_from)
);

CREATE TABLE claims (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_uuid             UUID          NOT NULL,
    submitted_by_user_id   BIGINT        NOT NULL REFERENCES users (id),
    provider_npi           CHAR(10)      NOT NULL REFERENCES provider_accounts (provider_npi),
    member_reference       VARCHAR(64)   NOT NULL,
    service_date           DATE          NOT NULL,
    billed_amount          NUMERIC(19,4) NOT NULL,
    allowed_amount         NUMERIC(19,4),
    patient_responsibility NUMERIC(19,4),
    status                 VARCHAR(24)   NOT NULL,
    claim_fingerprint      CHAR(64)      NOT NULL,
    idempotency_key        UUID          NOT NULL,
    reviewed_by_user_id    BIGINT        REFERENCES users (id),
    review_note            VARCHAR(1000),
    denial_reason          VARCHAR(40),
    submitted_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    adjudicated_at         TIMESTAMPTZ,
    reviewed_at            TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_claims_claim_uuid UNIQUE (claim_uuid),
    CONSTRAINT ck_claims_status CHECK (status IN
        ('RECEIVED','VALIDATED','FLAGGED_REVIEW','ADJUDICATED','PAID','DENIED','REVERSED')),
    CONSTRAINT ck_claims_denial_reason CHECK (denial_reason IS NULL OR denial_reason IN
        ('NOT_MEDICALLY_NECESSARY','SERVICE_NOT_COVERED','INSUFFICIENT_DOCUMENTATION',
         'DUPLICATE_ENCOUNTER','OUT_OF_NETWORK')),
    CONSTRAINT ck_claims_billed_positive CHECK (billed_amount > 0),
    CONSTRAINT ck_claims_service_date_not_future CHECK (service_date <= CURRENT_DATE),
    CONSTRAINT ck_claims_amount_invariant CHECK (
        (allowed_amount IS NULL AND patient_responsibility IS NULL)
        OR (allowed_amount >= 0 AND patient_responsibility >= 0
            AND allowed_amount + patient_responsibility = billed_amount)),
    CONSTRAINT ux_claims_submitter_idempotency UNIQUE (submitted_by_user_id, idempotency_key)
);

CREATE UNIQUE INDEX ux_claims_active_fingerprint
    ON claims (claim_fingerprint)
    WHERE status NOT IN ('DENIED', 'REVERSED');

CREATE INDEX ix_claims_submitter_submitted_at ON claims (submitted_by_user_id, submitted_at DESC);

CREATE INDEX ix_claims_status_submitted_at    ON claims (status, submitted_at ASC);

CREATE TABLE claim_lines (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id               BIGINT        NOT NULL REFERENCES claims (id) ON DELETE CASCADE,
    line_number            SMALLINT      NOT NULL,
    service_code           VARCHAR(5)    NOT NULL,
    diagnosis_code         VARCHAR(8)    NOT NULL,
    billed_amount          NUMERIC(19,4) NOT NULL,
    allowed_amount         NUMERIC(19,4),
    patient_responsibility NUMERIC(19,4),
    CONSTRAINT ux_claim_lines_number           UNIQUE (claim_id, line_number),
    CONSTRAINT ck_claim_lines_number_range     CHECK (line_number BETWEEN 1 AND 20),
    CONSTRAINT ck_claim_lines_billed_positive  CHECK (billed_amount > 0),
    CONSTRAINT ck_claim_lines_allowed_sign     CHECK (allowed_amount IS NULL OR allowed_amount >= 0),
    CONSTRAINT ck_claim_lines_pr_sign          CHECK (patient_responsibility IS NULL OR patient_responsibility >= 0)
);

CREATE INDEX ix_claim_lines_claim_id ON claim_lines (claim_id);

CREATE TABLE ledger_journals (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_group_id          UUID          NOT NULL,
    claim_id                  BIGINT        NOT NULL REFERENCES claims (id),
    provider_account_id       BIGINT        REFERENCES provider_accounts (id),
    account_type              VARCHAR(32)   NOT NULL,
    direction                 VARCHAR(6)    NOT NULL,
    amount                    NUMERIC(19,4) NOT NULL,
    memo                      VARCHAR(255)  NOT NULL,
    reverses_journal_group_id UUID,
    posted_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_account_type CHECK (account_type IN
        ('PAYER_CLAIMS_EXPENSE','PROVIDER_PAYABLE')),
    CONSTRAINT ck_ledger_direction        CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_ledger_amount_positive  CHECK (amount > 0),
    CONSTRAINT ck_ledger_provider_linkage CHECK (
        (account_type = 'PROVIDER_PAYABLE'     AND provider_account_id IS NOT NULL)
     OR (account_type = 'PAYER_CLAIMS_EXPENSE' AND provider_account_id IS NULL))
);

CREATE INDEX ix_ledger_journals_group     ON ledger_journals (journal_group_id);
CREATE INDEX ix_ledger_journals_claim     ON ledger_journals (claim_id);
CREATE INDEX ix_ledger_journals_posted_at ON ledger_journals (posted_at DESC);

CREATE UNIQUE INDEX ux_ledger_journals_reverses
    ON ledger_journals (reverses_journal_group_id)
    WHERE reverses_journal_group_id IS NOT NULL;

CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_uuid   UUID        NOT NULL,
    claim_id     BIGINT      NOT NULL REFERENCES claims (id),
    event_type   VARCHAR(40) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT ux_outbox_events_event_uuid UNIQUE (event_uuid),
    CONSTRAINT ck_outbox_event_type CHECK (event_type IN
        ('CLAIM_SUBMITTED','CLAIM_PAID','CLAIM_FLAGGED','CLAIM_DENIED',
         'CLAIM_REVERSED','SELF_APPROVAL_BLOCKED'))
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
