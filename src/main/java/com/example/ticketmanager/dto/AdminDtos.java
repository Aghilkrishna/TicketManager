package com.example.ticketmanager.dto;

import com.example.ticketmanager.entity.AppFeature;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
            boolean emailEnabled,
            boolean smsEnabled
    ) {
    }

    public record EmailNotificationSettingUpdateItem(
            String action,
            boolean emailEnabled,
            boolean smsEnabled
    ) {
    }

    public record UserDetailsResponse(
            Long id,
            String username,
            String email,
            String phone,
            String firstName,
            String lastName,
            String flat,
            String building,
            String area,
            String city,
            String state,
            String country,
            String pincode,
            boolean emailVerified,
            boolean phoneVerified,
            Set<String> roles,
            Set<String> roleLabels,
            boolean profileImageUploaded,
            String idProofType,
            String idProofFileName,
            boolean idProofUploaded,
            boolean hasAadharCard,
            boolean hasPanCard,
            boolean hasMandatoryIdProofs,
            boolean idProofVerified,
            boolean hasPendingVerification
    ) {
    }

    public record IdProofDocumentResponse(
            Long id,
            String idProofType,
            String fileName,
            LocalDateTime uploadDate,
            String uploadStatus,
            Boolean verified,
            String verificationNotes
    ) {
    }

    public record StaffBillingSummary(
            Long userId,
            String username,
            String email,
            Set<String> roleLabels,
            long resolvedTicketCount,
            BigDecimal resolvedAmount,
            long closedTicketCount,
            BigDecimal closedAmount,
            String billingStatusLabel
    ) {
    }

    public record StaffBillingTicketLine(
            Long ticketId,
            String title,
            String status,
            BigDecimal amount,
            String billingStatus,
            LocalDateTime updatedAt
    ) {
    }

    public record StaffBillingDetails(
            Long userId,
            String username,
            String email,
            String phone,
            Set<String> roleLabels,
            long resolvedTicketCount,
            BigDecimal resolvedAmount,
            long closedTicketCount,
            BigDecimal closedAmount,
            BigDecimal totalClosedAmount,
            BigDecimal totalPaidAmount,
            BigDecimal totalUnpaidAmount,
            String billingStatusLabel,
            List<StaffBillingTicketLine> tickets
    ) {
    }

    public record StaffBillingStatusUpdateRequest(
            @NotBlank String status
    ) {
    }
}
