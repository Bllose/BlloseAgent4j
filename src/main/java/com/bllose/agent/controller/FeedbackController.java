package com.bllose.agent.controller;

import com.bllose.agent.auth.SessionManager;
import com.bllose.agent.auth.UserRepository;
import com.bllose.agent.model.MessageFeedback;
import com.bllose.agent.repository.MessageFeedbackRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final MessageFeedbackRepository feedbackRepository;
    private final SessionManager sessionManager;
    private final UserRepository userRepository;

    public FeedbackController(MessageFeedbackRepository feedbackRepository,
                              SessionManager sessionManager,
                              UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.sessionManager = sessionManager;
        this.userRepository = userRepository;
    }

    @GetMapping("/{chatId}/{turnNum}")
    public ResponseEntity<?> getFeedback(
            @PathVariable String chatId,
            @PathVariable int turnNum) {
        MessageFeedback fb = feedbackRepository.findByChatIdAndTurnNum(chatId, turnNum);
        if (fb == null) {
            return ResponseEntity.ok(Map.of("rating", (Object) null, "feedbackText", (Object) null));
        }
        return ResponseEntity.ok(Map.of(
            "rating", fb.getRating() != null ? fb.getRating() : null,
            "feedbackText", fb.getFeedbackText() != null ? fb.getFeedbackText() : null
        ));
    }

    @PutMapping("/{chatId}/{turnNum}/rating")
    public ResponseEntity<?> updateRating(
            @PathVariable String chatId,
            @PathVariable int turnNum,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-Fingerprint", required = false) String fingerprint) {
        String rating = body.get("rating");
        if (rating == null || rating.isEmpty()) {
            feedbackRepository.clearRating(chatId, turnNum);
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        if (!rating.equals("up") && !rating.equals("down")) {
            return ResponseEntity.badRequest().body(Map.of("error", "rating must be 'up' or 'down'"));
        }
        feedbackRepository.updateRating(chatId, turnNum, rating);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PutMapping("/{chatId}/{turnNum}/text")
    public ResponseEntity<?> updateFeedbackText(
            @PathVariable String chatId,
            @PathVariable int turnNum,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-Fingerprint", required = false) String fingerprint) {
        String feedbackText = body.get("feedbackText");
        if (feedbackText == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "feedbackText is required"));
        }
        feedbackRepository.updateFeedbackText(chatId, turnNum, feedbackText);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
