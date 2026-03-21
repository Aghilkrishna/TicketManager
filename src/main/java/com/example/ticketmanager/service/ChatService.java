package com.example.ticketmanager.service;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.entity.AppUser;
import com.example.ticketmanager.entity.ChatConversation;
import com.example.ticketmanager.entity.ChatMessage;
import com.example.ticketmanager.entity.NotificationType;
import com.example.ticketmanager.entity.Ticket;
import com.example.ticketmanager.exception.AppException;
import com.example.ticketmanager.repository.ChatConversationRepository;
import com.example.ticketmanager.repository.ChatMessageRepository;
import com.example.ticketmanager.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserService userService;
    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public AuthDtos.ChatMessageResponse send(String senderUsername, AuthDtos.ChatMessageRequest request) {
        AppUser sender = userService.getByUsername(senderUsername);
        ChatConversation conversation = request.conversationId() != null
                ? getConversation(request.conversationId(), sender)
                : createConversation(sender, userService.getById(request.recipientId()));
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        if (request.ticketId() != null) {
            Ticket ticket = ticketRepository.findById(request.ticketId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Ticket not found"));
            message.setRelatedTicket(ticket);
        }
        message.setDeliveredAt(java.time.LocalDateTime.now());
        ChatMessage saved = messageRepository.save(message);
        AuthDtos.ChatMessageResponse response = toResponse(saved);
        conversation.getParticipants().stream()
                .filter(user -> !user.getId().equals(sender.getId()))
                .forEach(user -> {
                    messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/chat", response);
                    messagingTemplate.convertAndSendToUser(sender.getUsername(), "/queue/chat-status",
                            new AuthDtos.ChatStatusResponse(conversation.getId(), saved.getId(), "DELIVERED", user.getUsername()));
                    notificationService.notify(user, NotificationType.CHAT_MESSAGE,
                            "New chat message from " + sender.getUsername(), "CHAT", conversation.getId());
                });
        return response;
    }

    @Transactional
    public List<AuthDtos.ChatMessageResponse> history(Long conversationId, String username) {
        ChatConversation conversation = getConversation(conversationId, userService.getByUsername(username));
        AppUser currentUser = userService.getByUsername(username);
        List<ChatMessage> unread = messageRepository.findByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId, currentUser.getId());
        unread.forEach(message -> {
            message.setReadAt(java.time.LocalDateTime.now());
            messagingTemplate.convertAndSendToUser(message.getSender().getUsername(), "/queue/chat-status",
                    new AuthDtos.ChatStatusResponse(conversationId, message.getId(), "READ", currentUser.getUsername()));
        });
        return messageRepository.findTop100ByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.ChatConversationResponse> conversations(String username, String query) {
        AppUser user = userService.getByUsername(username);
        return new java.util.ArrayList<>(conversationRepository.findAll().stream()
                .filter(conversation -> conversation.getParticipants().stream().anyMatch(participant -> participant.getId().equals(user.getId())))
                .map(conversation -> toConversationSummary(conversation, user))
                .filter(conversation -> {
                    if (query == null || query.isBlank()) {
                        return true;
                    }
                    String lower = query.toLowerCase();
                    return conversation.otherUsername().toLowerCase().contains(lower)
                            || (conversation.otherEmail() != null && conversation.otherEmail().toLowerCase().contains(lower))
                            || (conversation.lastMessage() != null && conversation.lastMessage().toLowerCase().contains(lower));
                })
                .sorted(Comparator.comparing(AuthDtos.ChatConversationResponse::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(java.util.stream.Collectors.toMap(
                        AuthDtos.ChatConversationResponse::otherUserId,
                        conversation -> conversation,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values());
    }

    @Transactional(readOnly = true)
    public void typing(String senderUsername, AuthDtos.ChatTypingEvent event) {
        AppUser sender = userService.getByUsername(senderUsername);
        if (event.conversationId() != null) {
            ChatConversation conversation = getConversation(event.conversationId(), sender);
            conversation.getParticipants().stream()
                    .filter(user -> !user.getId().equals(sender.getId()))
                    .forEach(user -> messagingTemplate.convertAndSendToUser(
                            user.getUsername(),
                            "/queue/chat-typing",
                            new AuthDtos.ChatTypingEvent(conversation.getId(), sender.getId(), sender.getUsername(), event.typing())
                    ));
            return;
        }
        if (event.recipientId() != null) {
            AppUser recipient = userService.getById(event.recipientId());
            messagingTemplate.convertAndSendToUser(
                    recipient.getUsername(),
                    "/queue/chat-typing",
                    new AuthDtos.ChatTypingEvent(null, recipient.getId(), sender.getUsername(), event.typing())
            );
        }
    }

    private ChatConversation createConversation(AppUser sender, AppUser recipient) {
        return conversationRepository.findDirectConversation(sender.getId(), recipient.getId())
                .orElseGet(() -> createNewConversation(sender, recipient));
    }

    private ChatConversation createNewConversation(AppUser sender, AppUser recipient) {
        ChatConversation conversation = new ChatConversation();
        Set<AppUser> participants = new HashSet<>();
        participants.add(sender);
        participants.add(recipient);
        conversation.setParticipants(participants);
        return conversationRepository.save(conversation);
    }

    private ChatConversation getConversation(Long id, AppUser user) {
        ChatConversation conversation = conversationRepository.findByIdWithParticipants(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Conversation not found"));
        boolean member = conversation.getParticipants().stream().anyMatch(participant -> participant.getId().equals(user.getId()));
        if (!member) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not a conversation participant");
        }
        return conversation;
    }

    private AuthDtos.ChatMessageResponse toResponse(ChatMessage message) {
        return new AuthDtos.ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getUsername(),
                message.getContent(),
                message.getRelatedTicket() == null ? null : message.getRelatedTicket().getId(),
                message.getReadAt() != null ? "READ" : (message.getDeliveredAt() != null ? "DELIVERED" : "SENT"),
                message.getCreatedAt()
        );
    }

    private AuthDtos.ChatConversationResponse toConversationSummary(ChatConversation conversation, AppUser currentUser) {
        AppUser otherUser = conversation.getParticipants().stream()
                .filter(participant -> !participant.getId().equals(currentUser.getId()))
                .findFirst()
                .orElse(currentUser);
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversation.getId());
        ChatMessage lastMessage = messages.isEmpty() ? null : messages.getFirst();
        long unreadCount = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), currentUser.getId());
        return new AuthDtos.ChatConversationResponse(
                conversation.getId(),
                otherUser.getId(),
                otherUser.getUsername(),
                otherUser.getEmail(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                unreadCount
        );
    }
}
