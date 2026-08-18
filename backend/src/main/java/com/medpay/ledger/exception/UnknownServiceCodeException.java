package com.medpay.ledger.exception;

import java.time.LocalDate;

public class UnknownServiceCodeException extends RuntimeException {

    private final String serviceCode;
    private final LocalDate serviceDate;

    public UnknownServiceCodeException(String serviceCode, LocalDate serviceDate) {
        super("No contracted rate for that service code on the date of service");
        this.serviceCode = serviceCode;
        this.serviceDate = serviceDate;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }
}
