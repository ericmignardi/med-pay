package com.medpay.ledger.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;

/**
 * FR-030, error path. {@link PhiMaskingConverter} only sees the formatted message; a
 * throwable is rendered separately and would otherwise reach the appender unmasked.
 * An exception message routinely carries the offending value — a constraint violation
 * quotes it verbatim — so the rendered stack trace is masked with the same patterns.
 *
 * <p>Because this is registered as an exception converter, Logback stops auto-appending
 * the raw throwable, which is what closes the hole rather than merely duplicating it.
 */
public class PhiMaskingThrowableConverter extends ch.qos.logback.classic.pattern.ThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(ch.qos.logback.classic.spi.IThrowableProxy tp) {
        return PhiMaskingConverter.mask(ThrowableProxyUtil.asString(tp)) + CoreConstants.LINE_SEPARATOR;
    }

    @Override
    public String convert(ILoggingEvent event) {
        return PhiMaskingConverter.mask(super.convert(event));
    }
}
