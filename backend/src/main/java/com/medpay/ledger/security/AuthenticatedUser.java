package com.medpay.ledger.security;

import com.medpay.ledger.model.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class AuthenticatedUser implements UserDetails {

    private final long userId;
    private final UUID userUuid;
    private final String email;
    private final String fullName;
    private final Set<Role> roles;

    public AuthenticatedUser(long userId, UUID userUuid, String email, String fullName,
                             Set<Role> roles) {
        this.userId = userId;
        this.userUuid = Objects.requireNonNull(userUuid, "userUuid");
        this.email = Objects.requireNonNull(email, "email");
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public long getUserId() {
        return userId;
    }

    public UUID getUserUuid() {
        return userUuid;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public List<String> getRoleNames() {
        return roles.stream().map(Enum::name).sorted().toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[" + userUuid + "]";
    }
}
