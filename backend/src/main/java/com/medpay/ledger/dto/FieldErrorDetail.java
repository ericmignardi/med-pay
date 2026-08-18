package com.medpay.ledger.dto;

public record FieldErrorDetail(String field, Object rejectedValue, String message) {
}
