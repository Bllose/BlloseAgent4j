package com.bllose.agent.controller;

import com.bllose.agent.guard.GuardService;
import com.bllose.agent.service.ChatService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final GuardService guardService;

    public ChatController(ChatService chatService, GuardService guardService) {
        this.chatService = chatService;
        this.guardService = guardService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        guardService.check(request.message(), sessionId);
        return chatService.streamChat(request.message());
    }

    public record ChatRequest(String message) {}
}
