package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.EmailNotificationAction;
import com.example.ticketmanager.entity.Notification;
import com.example.ticketmanager.entity.NotificationType;
import com.example.ticketmanager.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;
    private final com.example.ticketmanager.config.AppProperties appProperties;
    private final EmailNotificationSettingsService emailNotificationSettingsService;
    private NotificationService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy NotificationService self) {
        this.self = self;
    }

    public void notify(AppUser user, NotificationType type, String message, String referenceType, Long referenceId) {
        notify(user, type, message, referenceType, referenceId, resolveEmailAction(type));
    }

    public void notify(AppUser user, NotificationType type, String message, String referenceType, Long referenceId, EmailNotificationAction emailAction) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        Notification saved = notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications", toResponse(saved));
        self.sendEmailNotification(user, type, message, referenceType, referenceId, emailAction);
    }

    @Async
    public void sendEmailNotification(AppUser user, NotificationType type, String message, String referenceType, Long referenceId, EmailNotificationAction emailAction) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        if (emailAction != null && !emailNotificationSettingsService.isEnabled(emailAction)) {
            return;
        }
        try {
            String actionUrl = null;
            String actionLabel = null;
            if ("TICKET".equalsIgnoreCase(referenceType) && referenceId != null) {
                actionUrl = appProperties.baseUrl() + "/tickets/view?id=" + referenceId;
                actionLabel = "Open Ticket";
            }
            String subject = switch (type) {
                case COMMENT_ADDED -> "New ticket comment";
                case TICKET_UPDATED -> "Ticket updated";
                case CHAT_MESSAGE -> "New chat message";
                case ACCOUNT_EVENT -> "Account notification";
            };
            emailService.sendTicketNotificationEmail(
                    user,
                    subject,
                    subject,
                    message,
                    actionUrl,
                    actionLabel == null ? "Open App" : actionLabel
            );
        } catch (Exception ex) {
            log.warn("Failed to send notification email to {}", user.getEmail(), ex);
        }
    }

    private EmailNotificationAction resolveEmailAction(NotificationType type) {
        return switch (type) {
            case COMMENT_ADDED -> EmailNotificationAction.COMMENT_ADDED;
            case TICKET_UPDATED -> EmailNotificationAction.TICKET_UPDATED;
            case CHAT_MESSAGE -> EmailNotificationAction.CHAT_MESSAGE;
            case ACCOUNT_EVENT -> null;
        };
    }

    public List<AuthDtos.NotificationResponse> listForUser(Long userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<AuthDtos.NotificationResponse> viewUnreadForUser(Long userId) {
        List<AuthDtos.NotificationResponse> items = notificationRepository.findTop20ByUserIdAndReadFlagFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
        notificationRepository.markAllUnreadAsRead(userId);
        return items;
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFlagFalse(userId);
    }

    private AuthDtos.NotificationResponse toResponse(Notification notification) {
        return new AuthDtos.NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.isReadFlag(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getCreatedAt()
        );
    }
}
