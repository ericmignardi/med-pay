package com.medpay.ledger.exception;

public class UnknownProviderException extends RuntimeException {

    private final String providerNpi;

    public UnknownProviderException(String providerNpi) {
        super("No provider account for the submitted NPI");
        this.providerNpi = providerNpi;
    }

    public String getProviderNpi() {
        return providerNpi;
    }
}
