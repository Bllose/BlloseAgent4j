package com.bllose.agent.auth;

import com.bllose.agent.model.AuthRequest;
import com.bllose.agent.model.AuthResponse;
import com.bllose.agent.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionManager sessionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
    }

    public AuthResponse register(AuthRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user = userRepository.save(user);
        String sessionId = sessionManager.createSession(user.getId());
        return new AuthResponse(sessionId, user.getUsername());
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByUsername(req.username())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String sessionId = sessionManager.createSession(user.getId());
        return new AuthResponse(sessionId, user.getUsername());
    }
}
