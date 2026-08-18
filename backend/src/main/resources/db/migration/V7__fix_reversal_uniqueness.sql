-- A reversal posts a balanced PAIR, so both of its rows carry the same
-- reverses_journal_group_id. The V1 index was unique on that column alone,
-- which made the FIRST reversal of any group violate it.
--
-- Uniqueness must instead say "at most one reversal GROUP per original group".
-- Each reversal group contributes exactly one row per account_type, so keying
-- on (reverses_journal_group_id, account_type) admits the two rows of a single
-- reversal and rejects a second reversal of the same original — which is the
-- database-level backstop for the PAID -> REVERSED state rule (FR-015).

DROP INDEX IF EXISTS ux_ledger_journals_reverses;

CREATE UNIQUE INDEX ux_ledger_journals_reverses
    ON ledger_journals (reverses_journal_group_id, account_type)
    WHERE reverses_journal_group_id IS NOT NULL;
