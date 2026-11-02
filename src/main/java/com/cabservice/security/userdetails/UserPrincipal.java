package com.cabservice.security.userdetails;



import com.cabservice.common.util.AppConstant;
import com.cabservice.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Exists only DURING LOGIN: Spring's DaoAuthenticationProvider needs a UserDetails to
 * check the password. After login, requests use the lighter AuthenticatedUser record
 * built from the JWT.
 */
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<AppConstant.Role> roles;

    private UserPrincipal(UUID id, String email, String passwordHash, boolean enabled, Set<AppConstant.Role> roles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = roles;
    }

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getAccountStatus() == AppConstant.AccountStatus.ACTIVE,
                Set.copyOf(user.getRoles()));
    }

    public UUID getId() {
        return id;
    }

    public Set<AppConstant.Role> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}