package com.medpay.ledger.testsupport;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a fully-formed {@link com.medpay.ledger.security.AuthenticatedUser},
 * including the {@code userUuid} the self-approval check compares against
 * (§6.1). {@code @WithMockUser} cannot carry that identity, and a test using it
 * would pass the separation-of-duties case for the wrong reason.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory.class)
public @interface WithMockCustomUser {

    String email() default "processor@medpay.test";

    String userUuid() default "a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91";

    long userId() default 1L;

    String fullName() default "Priya Raman";

    String[] roles() default {"CLAIMS_PROCESSOR"};
}
