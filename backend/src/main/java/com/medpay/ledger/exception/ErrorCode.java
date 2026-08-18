package com.medpay.ledger.exception;

/**
 * Every {@code code} value the API can emit (§5.6).
 *
 * <p>Held as constants rather than an enum because the value crosses the wire as
 * a plain string and is asserted literally by the TypeScript client and the
 * Playwright suite; an enum would invite {@code name()} drift.
 */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String MISSING_IDEMPOTENCY_KEY = "MISSING_IDEMPOTENCY_KEY";

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    public static final String FORBIDDEN = "FORBIDDEN";

    public static final String CLAIM_NOT_FOUND = "CLAIM_NOT_FOUND";

    public static final String DUPLICATE_CLAIM = "DUPLICATE_CLAIM";
    public static final String SELF_APPROVAL_FORBIDDEN = "SELF_APPROVAL_FORBIDDEN";
    public static final String ILLEGAL_STATE_TRANSITION = "ILLEGAL_STATE_TRANSITION";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    public static final String LINE_SUM_MISMATCH = "LINE_SUM_MISMATCH";
    public static final String UNKNOWN_SERVICE_CODE = "UNKNOWN_SERVICE_CODE";
    public static final String UNKNOWN_PROVIDER = "UNKNOWN_PROVIDER";

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {
    }
}
