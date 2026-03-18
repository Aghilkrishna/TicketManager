package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.Notification;
import com.example.ticketmanager.entity.NotificationType;
import com.example.ticketmanager.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void notify(AppUser user, NotificationType type, String message, String referenceType, Long referenceId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        Notification saved = notificationRepository.save(notification);
        messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/notifications", toResponse(saved));
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
