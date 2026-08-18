package com.medpay.ledger.testsupport;

import com.medpay.ledger.model.Role;
import com.medpay.ledger.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class WithMockCustomUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        Set<Role> roles = Arrays.stream(annotation.roles())
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        AuthenticatedUser principal = new AuthenticatedUser(
                annotation.userId(),
                UUID.fromString(annotation.userUuid()),
                annotation.email(),
                annotation.fullName(),
                roles);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        return context;
    }
}
