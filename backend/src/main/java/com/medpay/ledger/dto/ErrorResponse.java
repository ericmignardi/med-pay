package com.medpay.ledger.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single shape of every non-2xx response in the system (§5.6, FR-026).
 *
 * <p>{@code fieldErrors} and {@code details} serialize as {@code null} when
 * absent rather than being omitted, so a client can destructure the envelope
 * without presence checks.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors,
        Map<String, Object> details) {

    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null, null);
    }

    public static ErrorResponse of(int status, String code, String message, String path,
                                   Map<String, Object> details) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null, details);
    }
}
