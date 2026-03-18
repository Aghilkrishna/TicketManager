package com.example.ticketmanager.config;

import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Role;
import com.example.ticketmanager.entity.RoleName;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            Arrays.stream(RoleName.values()).forEach(name -> roleRepository.findByName(name).orElseGet(() -> roleRepository.save(new Role(name))));
            if (userRepository.count() == 0) {
                createUser("admin", "admin@example.com", "9999999999", "Admin@123", RoleName.ROLE_ADMIN, RoleName.ROLE_MANAGER);
                createUser("manager", "manager@example.com", "8888888888", "Manager@123", RoleName.ROLE_MANAGER);
                createUser("agent", "agent@example.com", "7777777777", "Agent@123", RoleName.ROLE_AGENT);
                createUser("user", "user@example.com", "6666666666", "User@123", RoleName.ROLE_USER);
            }
        };
    }

    private void createUser(String username, String email, String phone, String rawPassword, RoleName... roles) {
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
