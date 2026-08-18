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

/**
 * The authenticated principal. Built solely from validated JWT claims — there is
 * no database read on the request path (FR-003, FR-004).
 *
 * <p>{@code userUuid} is the identity the self-approval check compares against
 * (FR-019); {@code userId} is the surrogate key used to scope repository reads to
 * the caller's own claims (§2.3). Both must be present or separation of duties
 * silently degrades, which is why neither is nullable.
 */
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

    /**
     * Carried as a token claim rather than looked up, because {@code /auth/me}
     * returns it and FR-004 forbids a database read on that path.
     */
    public String getFullName() {
        return fullName;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    /** Role names without the {@code ROLE_} prefix, sorted, for the wire contract. */
    public List<String> getRoleNames() {
        return roles.stream().map(Enum::name).sorted().toList();
    }

    /**
     * The {@code ROLE_} prefix is re-applied here and nowhere else — {@code
     * hasRole('MEDICAL_REVIEWER')} in {@code @PreAuthorize} expands to the
     * prefixed authority, so the token claim itself stays prefix-free (FR-002).
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    /** No credential is ever held in memory: authentication happened at token issue. */
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
