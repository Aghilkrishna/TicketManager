package com.example.ticketmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Pattern(regexp = "^$|^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone
    ) {
    }

    public record VendorRegisterRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Size(max = 120) String contactPerson,
            @NotBlank @Pattern(regexp = "^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone,
            @NotBlank @Size(max = 30) String gstNumber,
            @Size(max = 120) String flat,
            @Size(max = 120) String building,
            @Size(max = 120) String area,
            @Size(max = 80) String city,
            @Size(max = 80) String state,
            @Size(max = 80) String country,
            @Size(max = 20) String pincode
    ) {
    }

    public record AuthResponse(
            Long id,
            String username,
            String email,
            Set<String> roles,
            String message
    ) {
    }

    public record ProfileResponse(
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
            Set<String> roles,
            boolean profileImageUploaded
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Email String email,
            @Pattern(regexp = "^$|^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone,
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 120) String flat,
            @Size(max = 120) String building,
            @Size(max = 120) String area,
            @Size(max = 80) String city,
            @Size(max = 80) String state,
            @Size(max = 80) String country,
            @Size(max = 20) String pincode
    ) {
    }

    public record ProfilePasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword,
            @NotBlank @Size(min = 8, max = 100) String confirmPassword
    ) {
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }

    public record TicketRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 500) String address,
            String serviceType,
            @Size(max = 1000) String locationLink,
            Integer siteVisits,
            Long parentTicketId,
            Long vendorUserId,
            @Size(max = 500) String vendorNotes,
            @Size(max = 150) String customerName,
            @Size(max = 100) String customerEmail,
            @Size(max = 20) String customerPhone,
            @Size(max = 120) String customerFlat,
            @Size(max = 150) String customerStreet,
            @Size(max = 80) String customerCity,
            @Size(max = 80) String customerState,
            @Size(max = 20) String customerPincode,
            @Size(max = 1000) String customerLocationLink,
            String pricingModel,
            BigDecimal estimatedCost,
            BigDecimal actualCost,
            @Size(max = 3000) String additionalNotes,
            @Size(max = 2000) String initialComment,
            LocalDate scheduleDate,
            String priority,
            String status,
            Long assignedToId,
            Set<Long> serviceUserIds
    ) {
    }

    public record TicketSummary(
            Long id,
            String title,
            String description,
            String address,
            String serviceType,
            String serviceTypeLabel,
            String locationLink,
            Integer siteVisits,
            Long parentTicketId,
            String parentTicketTitle,
            Long vendorUserId,
            String vendorName,
            String vendorEmail,
            String vendorPhone,
            String vendorNotes,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerFlat,
            String customerStreet,
            String customerCity,
            String customerState,
            String customerPincode,
            String customerLocationLink,
            String pricingModel,
            String pricingModelLabel,
            BigDecimal estimatedCost,
            BigDecimal actualCost,
            String additionalNotes,
            String status,
            String priority,
            LocalDate scheduleDate,
            String createdBy,
            String updatedBy,
            Long assignedToId,
            String assignedTo,
            Set<Long> serviceUserIds,
            Set<String> serviceUsers,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<String> attachmentNames
    ) {
    }

    public record TicketCommentRequest(
            Long parentId,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record TicketCommentResponse(
            Long id,
            Long parentId,
            Long authorId,
            String author,
            String content,
            boolean canEdit,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<TicketCommentResponse> replies
    ) {
    }

    public record TicketCommentUpdateRequest(
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record TicketCommentEvent(
            Long ticketId,
            String action,
            Long commentId
    ) {
    }

    public record TicketSiteVisitRequest(
            java.time.LocalDateTime visitedAt,
            Double latitude,
            Double longitude,
            @Size(max = 2000) String notes
    ) {
    }

    public record TicketSiteVisitResponse(
            Long id,
            Long agentId,
            String agentName,
            LocalDateTime visitedAt,
            Double latitude,
            Double longitude,
            String notes
    ) {
    }

    public record NotificationResponse(
            Long id,
            String type,
            String message,
            boolean readFlag,
            String referenceType,
            Long referenceId,
            LocalDateTime createdAt
    ) {
    }

    public record ChatConversationResponse(
            Long id,
            Long otherUserId,
            String otherUsername,
            String otherEmail,
            String lastMessage,
            LocalDateTime lastMessageAt,
            long unreadCount
    ) {
    }

    public record ChatMessageRequest(
            Long conversationId,
            Long recipientId,
            Long ticketId,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record ChatMessageResponse(
            Long id,
            Long conversationId,
            Long senderId,
            String sender,
            String content,
            Long ticketId,
            String deliveryStatus,
            LocalDateTime createdAt
    ) {
    }

    public record ChatStatusResponse(
            Long conversationId,
            Long messageId,
            String status,
            String username
    ) {
    }

    public record ChatTypingEvent(
            Long conversationId,
            Long recipientId,
            String username,
            boolean typing
    ) {
    }
}
