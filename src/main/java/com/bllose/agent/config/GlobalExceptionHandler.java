package com.bllose.agent.config;

import com.bllose.agent.guard.GuardBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GuardBlockedException.class)
    public ResponseEntity<Map<String, String>> handleGuardBlocked(GuardBlockedException e) {
        log.info("Request blocked by guard: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "BLOCKED", "message", e.getMessage()));
    }
}
