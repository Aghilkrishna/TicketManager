package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final ChatService chatService;

    @MessageMapping("/chat.send")
    @PreAuthorize("hasAuthority('FEATURE_CHAT_ACCESS')")
    public void send(Principal principal, @Valid @Payload AuthDtos.ChatMessageRequest request) {
        chatService.send(principal.getName(), request);
    }

    @MessageMapping("/chat.typing")
    @PreAuthorize("hasAuthority('FEATURE_CHAT_ACCESS')")
    public void typing(Principal principal, @Payload AuthDtos.ChatTypingEvent event) {
        chatService.typing(principal.getName(), event);
    }
}
