package com.example.ticketmanager.security;

import com.example.ticketmanager.entity.AppFeature;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public record AppUserPrincipal(
        Long id,
        String username,
        String email,
        String password,
        boolean enabled,
        Set<String> roleNames
) implements UserDetails {
    public AppUserPrincipal(AppUser user) {
        this(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                user.getRoles().stream()
                        .filter(Role::isActive)
                        .flatMap(role -> java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(role.getName()),
                                role.getFeatures().stream().map(AppFeature::authority)
                        ))
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
