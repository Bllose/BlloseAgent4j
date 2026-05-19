package com.bllose.agent.controller;

import com.bllose.agent.auth.AuthService;
import com.bllose.agent.model.AuthRequest;
import com.bllose.agent.model.AuthResponse;
import com.bllose.agent.model.GuestRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    @PostMapping("/guest")
    public ResponseEntity<?> guest(@RequestBody GuestRequest req, HttpServletResponse response) {
        AuthResponse auth = authService.guestLogin(req);
        response.setHeader("X-Session-Id", auth.sessionId());
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
        return ResponseEntity.ok(auth);
    }
}
