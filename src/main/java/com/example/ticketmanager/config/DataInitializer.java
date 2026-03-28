package com.example.ticketmanager.config;

import com.example.ticketmanager.entity.AppFeature;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Role;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            seedRole("ROLE_ADMIN", "Full administrative access", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE,
                    AppFeature.CHAT_ACCESS,
                    AppFeature.ADMIN_SUPPORT_TICKETS,
                    AppFeature.ADMIN_USER_MANAGEMENT,
                    AppFeature.ADMIN_ROLE_MANAGEMENT,
                    AppFeature.ADMIN_ROLE_FEATURE_ASSIGNMENT
            ));
            seedRole("ROLE_MANAGER", "Manage ticket operations", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE,
                    AppFeature.CHAT_ACCESS
            ));
            seedRole("ROLE_AGENT", "Work assigned tickets and chat", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.SITE_VISIT_EDIT,
                    AppFeature.CHAT_ACCESS
            ));
            seedRole("ROLE_VENDOR", "Create and manage vendor-owned tickets", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.TICKETS_MANAGE
            ));
            seedRole("ROLE_USER", "Standard end user access", Set.of(
                    AppFeature.DASHBOARD_ACCESS,
                    AppFeature.PROFILE_ACCESS,
                    AppFeature.TICKETS_VIEW,
                    AppFeature.CHAT_ACCESS
            ));
            if (userRepository.count() == 0) {
                createUser("admin", "admin@example.com", "9999999999", "Admin@123", "ROLE_ADMIN", "ROLE_MANAGER");
                createUser("manager", "manager@example.com", "8888888888", "Manager@123", "ROLE_MANAGER");
                createUser("agent", "agent@example.com", "7777777777", "Agent@123", "ROLE_AGENT");
                createUser("vendor", "vendor@example.com", "5555555555", "Vendor@123", "ROLE_VENDOR");
                createUser("user", "user@example.com", "6666666666", "User@123", "ROLE_USER");
            }
        };
    }

    private void seedRole(String name, String description, Set<AppFeature> features) {
        roleRepository.findByName(name).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                existing.setDescription(description);
                changed = true;
            }
            if (existing.getFeatures() == null || existing.getFeatures().isEmpty()) {
                existing.setFeatures(new HashSet<>(features));
                existing.setActive(true);
                changed = true;
            }
            if (changed) {
                roleRepository.save(existing);
            }
        }, () -> {
            Role role = new Role(name, description);
            role.setActive(true);
            role.setFeatures(new HashSet<>(features));
            roleRepository.save(role);
        });
    }

    private void createUser(String username, String email, String phone, String rawPassword, String... roles) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(true);
        Arrays.stream(roles)
                .map(role -> roleRepository.findByName(role).orElseThrow())
                .forEach(user.getRoles()::add);
        userRepository.save(user);
    }
}
