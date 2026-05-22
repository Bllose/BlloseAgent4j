package com.bllose.agent.controller;

import com.bllose.agent.auth.AuthService;
import com.bllose.agent.auth.SessionManager;
import com.bllose.agent.model.AuthRequest;
import com.bllose.agent.model.AuthResponse;
import com.bllose.agent.model.GuestRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionManager sessionManager;

    public AuthController(AuthService authService, SessionManager sessionManager) {
        this.authService = authService;
        this.sessionManager = sessionManager;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank() || sessionId.startsWith("guest-")) {
            return ResponseEntity.ok(Map.of("valid", false, "reason", "no session"));
        }
        Integer userNumber = sessionManager.getUserNumber(sessionId);
        if (userNumber == null || userNumber == 0) {
            return ResponseEntity.ok(Map.of("valid", false, "reason", "session expired"));
        }
        Long userId = sessionManager.getUserId(sessionId);
        return ResponseEntity.ok(Map.of("valid", true, "userId", userId, "userNumber", userNumber));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest req) {
        try {
            return ResponseEntity.ok(authService.register(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        try {
            return ResponseEntity.ok(authService.login(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/hello-guest")
    public ResponseEntity<?> helloGuest(HttpServletResponse response) {
        AuthResponse auth = authService.helloGuest();
        response.setHeader("X-Session-Id", auth.sessionId());
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/guest")
    public ResponseEntity<?> guest(@RequestBody GuestRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.guestLogin(req);
        response.setHeader("X-Session-Id", auth.sessionId());
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
        return ResponseEntity.ok(auth);
    }
}
