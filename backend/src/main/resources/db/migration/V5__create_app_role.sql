DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medpay_app') THEN
        CREATE ROLE medpay_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO medpay_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON users             TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_roles        TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON provider_accounts TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON fee_schedules     TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON claims            TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON claim_lines       TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ledger_journals   TO medpay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_events     TO medpay_app;
