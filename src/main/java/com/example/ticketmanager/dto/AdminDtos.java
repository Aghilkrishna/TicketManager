package com.example.ticketmanager.dto;

import com.example.ticketmanager.entity.AppFeature;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record FeatureResponse(
            String name,
            String label,
            String description
    ) {
        public static FeatureResponse from(AppFeature feature) {
            return new FeatureResponse(feature.name(), feature.label(), feature.description());
        }
    }

    public record UserSummary(
            Long id,
            String username,
            String email,
            String phone,
            boolean enabled,
            boolean emailVerified,
            Set<String> roles,
            Set<String> roleLabels
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @Pattern(regexp = "^$|^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone,
            boolean enabled,
            Set<Long> roleIds
    ) {
    }

    public record RoleSummary(
            Long id,
            String name,
            String label,
            String description,
            boolean active,
            Set<String> features,
            Set<String> featureLabels,
            long userCount
    ) {
    }

    public record RoleRequest(
            @NotBlank @Size(min = 3, max = 50) String name,
            @Size(max = 255) String description,
            boolean active
    ) {
    }

    public record RoleFeatureUpdateRequest(Set<String> features) {
    }

    public record EmailNotificationSettingResponse(
            String action,
            String label,
            String description,
            boolean enabled
    ) {
    }

    public record EmailNotificationSettingUpdateItem(
            String action,
            boolean enabled
    ) {
    }
}
