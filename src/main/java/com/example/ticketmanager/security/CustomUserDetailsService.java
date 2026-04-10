package com.example.ticketmanager.security;

import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier == null ? null : identifier.trim();
        log.debug("Loading user by identifier: {}", normalized);
        if (normalized == null || normalized.isBlank()) {
            throw new UsernameNotFoundException("User not found with identifier: " + normalized);
        }
        List<String> phoneCandidates = buildPhoneCandidates(normalized);
        return userRepository.findFirstByEmailIgnoreCaseOrPhoneIn(normalized, phoneCandidates)
                .map(user -> {
                    log.debug("User found: {}, enabled: {}, roles count: {}", 
                            normalized, user.isEnabled(), user.getRoles().size());
                    return new AppUserPrincipal(user);
                })
                .orElseThrow(() -> {
                    log.warn("User not found with identifier: {}", normalized);
                    return new UsernameNotFoundException("User not found with identifier: " + normalized);
                });
    }

    private List<String> buildPhoneCandidates(String identifier) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(identifier);

        String digits = identifier.replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            candidates.add(digits);
            candidates.add("+" + digits);

            if (digits.length() == 10) {
                candidates.add("91" + digits);
                candidates.add("+91" + digits);
            }

            if (digits.length() >= 12 && digits.startsWith("91")) {
                String local = digits.substring(digits.length() - 10);
                candidates.add(local);
                candidates.add("+91" + local);
            }
        }

        return new ArrayList<>(candidates);
    }
}
