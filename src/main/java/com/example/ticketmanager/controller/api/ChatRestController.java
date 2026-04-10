package com.example.ticketmanager.controller.api;

import com.example.ticketmanager.dto.AuthDtos;
import com.example.ticketmanager.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('FEATURE_CHAT_ACCESS')")
public class ChatRestController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public Object conversations(Principal principal, @RequestParam(required = false) String query) {
        return chatService.conversations(principal.getName(), query);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<AuthDtos.ChatMessageResponse> history(Principal principal, @PathVariable Long conversationId) {
        return chatService.history(conversationId, principal.getName());
    }

    @org.springframework.web.bind.annotation.PostMapping("/messages")
    public AuthDtos.ChatMessageResponse send(Principal principal, @Valid @RequestBody AuthDtos.ChatMessageRequest request) {
        return chatService.send(principal.getName(), request);
    }
}
