package com.bllose.agent.controller;

import com.bllose.agent.auth.SessionManager;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final SessionManager sessionManager;

    public ConversationController(ConversationService conversationService,
                                  SessionManager sessionManager) {
        this.conversationService = conversationService;
        this.sessionManager = sessionManager;
    }

    private int requireUserNumber(String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.startsWith("guest-")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅注册用户可访问对话历史");
        }
        Integer userNumber = sessionManager.getUserNumber(sessionId);
        if (userNumber == null || userNumber == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法识别用户身份，请重新登录");
        }
        return userNumber;
    }

    @GetMapping
    public ResponseEntity<List<Conversation>> listConversations(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        int userNumber = requireUserNumber(sessionId);
        return ResponseEntity.ok(conversationService.listRecent(userNumber, 10));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "3") int turns,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        requireUserNumber(sessionId);
        var messages = conversationService.loadHistory(chatId, turns);
        return ResponseEntity.ok(messages);
    }

    @PutMapping("/{chatId}")
    public ResponseEntity<?> updateConversation(
            @PathVariable String chatId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        requireUserNumber(sessionId);
        String title = body.get("title");
        if (title != null && !title.isBlank()) {
            conversationService.updateTitle(chatId, title);
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
