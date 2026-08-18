package com.medpay.ledger.util;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class ClaimFingerprintCalculator {

    private static final String FIELD_SEPARATOR = "|";
    private static final String CODE_SEPARATOR = ",";

    public String fingerprint(ClaimSubmissionRequest request) {
        return fingerprint(
                request.providerNpi(),
                request.memberReference(),
                request.serviceDate(),
                request.lines().stream().map(ClaimLineRequest::serviceCode).toList());
    }

    public String fingerprint(String providerNpi, String memberReference, LocalDate serviceDate,
                              List<String> serviceCodes) {

        String canonicalCodes = serviceCodes.stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + CODE_SEPARATOR + right)
                .orElse("");

        String canonical = String.join(FIELD_SEPARATOR,
                providerNpi,
                memberReference.trim(),
                serviceDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                canonicalCodes);

        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
