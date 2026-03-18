package com.example.ticketmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Pattern(regexp = "^$|^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone
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
            boolean emailVerified,
            Set<String> roles
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @Pattern(regexp = "^$|^[0-9+\\-() ]{7,20}$", message = "Invalid phone number") String phone,
            @Size(min = 8, max = 100) String password
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
            String status,
            String priority,
            LocalDate scheduleDate,
            String createdBy,
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
