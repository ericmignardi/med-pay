package com.medpay.ledger.util;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhiMaskingConverter extends ClassicConverter {

    private static final String MASK = "***";

    private static final List<Pattern> PHI_PATTERNS = List.of(
            Pattern.compile("MBR-[A-Za-z0-9]{4,}"),
            Pattern.compile("(?<=\"memberReference\"\\s?:\\s?\")[^\"]+"),
            Pattern.compile("(?<=memberReference=)[^,\\s\\]}]+"),
            Pattern.compile("(?<=\"diagnosisCode\"\\s?:\\s?\")[^\"]+"),
            Pattern.compile("(?<=diagnosisCode=)[^,\\s\\]}]+"),
            Pattern.compile("(?<=\"serviceCode\"\\s?:\\s?\")[^\"]+"),
            Pattern.compile("(?<=serviceCode=)[^,\\s\\]}]+"));

    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String masked = message;
        for (Pattern pattern : PHI_PATTERNS) {
            Matcher matcher = pattern.matcher(masked);
            StringBuilder rewritten = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(rewritten,
                        Matcher.quoteReplacement(maskValue(matcher.group())));
            }
            matcher.appendTail(rewritten);
            masked = rewritten.toString();
        }
        return masked;
    }

    private static String maskValue(String value) {
        if (value.length() <= 2) {
            return MASK;
        }
        return MASK + value.substring(value.length() - 2);
    }
}
