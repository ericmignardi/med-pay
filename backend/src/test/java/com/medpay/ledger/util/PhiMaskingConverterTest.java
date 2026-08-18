package com.medpay.ledger.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-030. The rule is not "PHI is usually masked" — it is that no PHI-adjacent value
 * reaches an appender by any path, so the error path is exercised here alongside the
 * happy path.
 */
class PhiMaskingConverterTest {

    private static final String MEMBER = "MBR-8F41C0DE9A22";
    private static final String DIAGNOSIS = "E1165";

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger(PhiMaskingConverterTest.class);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("a member reference is masked down to its last two characters")
    void memberReferenceIsMasked() {
        String masked = PhiMaskingConverter.mask("submitted claim for " + MEMBER);

        assertThat(masked).doesNotContain(MEMBER);
        assertThat(masked).contains("***22");
    }

    @Test
    @DisplayName("a member reference is masked inside a JSON payload")
    void memberReferenceIsMaskedInsideJson() {
        String masked = PhiMaskingConverter.mask(
                "{\"claimUuid\":\"abc\",\"memberReference\":\"" + MEMBER + "\"}");

        assertThat(masked).doesNotContain(MEMBER);
    }

    @Test
    @DisplayName("a diagnosis code is masked in both key=value and JSON forms")
    void diagnosisCodeIsMasked() {
        assertThat(PhiMaskingConverter.mask("line rejected diagnosisCode=" + DIAGNOSIS + ", code=X"))
                .doesNotContain("diagnosisCode=" + DIAGNOSIS);
        assertThat(PhiMaskingConverter.mask("{\"diagnosisCode\":\"" + DIAGNOSIS + "\"}"))
                .doesNotContain("\"" + DIAGNOSIS + "\"");
    }

    @Test
    @DisplayName("masking is null- and empty-safe, so a converter fault cannot break logging")
    void maskingToleratesNullAndEmpty() {
        assertThat(PhiMaskingConverter.mask(null)).isNull();
        assertThat(PhiMaskingConverter.mask("")).isEmpty();
        assertThat(PhiMaskingConverter.mask("nothing sensitive here"))
                .isEqualTo("nothing sensitive here");
    }

    @Test
    @DisplayName("the error path: an exception message carrying PHI is masked too")
    void throwableMessagesAreMasked() {
        PhiMaskingThrowableConverter converter = new PhiMaskingThrowableConverter();
        converter.start();

        logger.error("adjudication failed",
                new IllegalStateException("duplicate memberReference=" + MEMBER));

        ILoggingEvent event = appender.list.getFirst();
        String rendered = converter.convert(event);

        assertThat(rendered).doesNotContain(MEMBER);
        assertThat(rendered).contains("IllegalStateException");
    }

    @Test
    @DisplayName("the error path: PHI in a nested cause is masked as well")
    void nestedCauseMessagesAreMasked() {
        PhiMaskingThrowableConverter converter = new PhiMaskingThrowableConverter();
        converter.start();

        logger.error("wrapped failure",
                new RuntimeException("outer",
                        new IllegalArgumentException("diagnosisCode=" + DIAGNOSIS + " unknown")));

        String rendered = converter.convert(appender.list.getFirst());

        assertThat(rendered).doesNotContain("diagnosisCode=" + DIAGNOSIS);
    }
}
