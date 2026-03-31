package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AdminDtos;
import com.example.ticketmanager.entity.AppFeature;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.Role;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.RoleRepository;
import com.example.ticketmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EmailNotificationSettingsService emailNotificationSettingsService;

    @Transactional(readOnly = true)
    public List<AdminDtos.FeatureResponse> listFeatures() {
        return Arrays.stream(AppFeature.values())
                .map(AdminDtos.FeatureResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.EmailNotificationSettingResponse> listEmailNotificationSettings() {
        return emailNotificationSettingsService.listSettings();
    }

    @Transactional
    public List<AdminDtos.EmailNotificationSettingResponse> updateEmailNotificationSettings(List<AdminDtos.EmailNotificationSettingUpdateItem> items) {
        return emailNotificationSettingsService.updateSettings(items);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RoleSummary> listRoles() {
        List<AppUser> users = userRepository.findAll();
        return roleRepository.findAll().stream()
                .map(role -> toRoleSummary(role, users))
                .toList();
    }

    @Transactional
    public AdminDtos.RoleSummary createRole(AdminDtos.RoleRequest request) {
        String normalizedName = normalizeRoleName(request.name());
        if (roleRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Role name already exists");
        }
        Role role = new Role();
        role.setName(normalizedName);
        role.setDescription(blankToNull(request.description()));
        role.setActive(request.active());
        return toRoleSummary(roleRepository.save(role), userRepository.findAll());
    }

    @Transactional
    public AdminDtos.RoleSummary updateRole(Long roleId, AdminDtos.RoleRequest request) {
        Role role = getRole(roleId);
        String normalizedName = normalizeRoleName(request.name());
        if (!role.getName().equalsIgnoreCase(normalizedName) && roleRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Role name already exists");
        }
        role.setName(normalizedName);
        role.setDescription(blankToNull(request.description()));
        role.setActive(request.active());
        return toRoleSummary(roleRepository.save(role), userRepository.findAll());
    }

    @Transactional
    public AdminDtos.RoleSummary updateRoleFeatures(Long roleId, Set<String> featureNames) {
        Role role = getRole(roleId);
        role.setFeatures(resolveFeatures(featureNames));
        return toRoleSummary(roleRepository.save(role), userRepository.findAll());
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Role role = getRole(roleId);
        boolean assigned = userRepository.findAll().stream()
                .anyMatch(user -> user.getRoles().stream().anyMatch(existing -> existing.getId().equals(roleId)));
        if (assigned) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot delete a role that is assigned to users");
        }
        roleRepository.delete(role);
    }

    private Role getRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private Set<AppFeature> resolveFeatures(Set<String> featureNames) {
        if (featureNames == null || featureNames.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return featureNames.stream()
                .map(name -> {
                    try {
                        return AppFeature.valueOf(name);
                    } catch (IllegalArgumentException ex) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "Unknown feature: " + name);
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeRoleName(String name) {
        String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Role name is required");
        }
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AdminDtos.RoleSummary toRoleSummary(Role role, List<AppUser> users) {
        long userCount = users.stream()
                .filter(user -> user.getRoles().stream().anyMatch(existing -> existing.getId().equals(role.getId())))
                .count();
        return new AdminDtos.RoleSummary(
                role.getId(),
                role.getName(),
                userService.toRoleLabel(role.getName()),
                role.getDescription(),
                role.isActive(),
                role.getFeatures().stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new)),
                role.getFeatures().stream().map(AppFeature::label).collect(Collectors.toCollection(LinkedHashSet::new)),
                userCount
        );
    }
}
