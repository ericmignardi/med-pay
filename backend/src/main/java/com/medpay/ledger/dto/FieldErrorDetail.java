package com.medpay.ledger.dto;

/**
 * One Bean Validation violation (§5.6).
 *
 * <p>{@code rejectedValue} is {@code null} for any PHI-adjacent field, so a
 * {@code memberReference} or {@code diagnosisCode} violation never echoes the
 * submitted value back into a response body or a log line (FR-030).
 */
public record FieldErrorDetail(String field, Object rejectedValue, String message) {
}
