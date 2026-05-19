package com.bllose.agent.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.bllose.agent.service.ChatV1Service;
import com.bllose.agent.service.PaperService;

@RestController
@RequestMapping("/api/chat/v1")
public class ChatV1Controller {

    private final ChatV1Service chatV1Service;
    private final PaperService paperService;

    public ChatV1Controller(ChatV1Service chatV1Service, PaperService paperService) {
        this.chatV1Service = chatV1Service;
        this.paperService = paperService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestBody ChatV1Request request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_SESSION_ID");
        }
        return chatV1Service.streamChat(sessionId, request.message());
    }

    @PostMapping(value = "/paper", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter paperChat(
            @RequestBody ChatV1Request request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_SESSION_ID");
        }
        return paperService.streamChat(sessionId, request.message());
    }

    @PostMapping(value = "/paper/invoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> paperChatInvoke(
            @RequestBody ChatV1Request request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_SESSION_ID");
        }
        String content = paperService.invokeChat(sessionId, request.message());
        return ResponseEntity.ok(Map.of("content", content));
    }

    public record ChatV1Request(String message) {}
}
