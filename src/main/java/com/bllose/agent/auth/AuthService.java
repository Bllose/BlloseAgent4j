package com.bllose.agent.auth;

import com.bllose.agent.model.AuthRequest;
import com.bllose.agent.model.AuthResponse;
import com.bllose.agent.model.GuestRequest;
import com.bllose.agent.model.GuestSession;
import com.bllose.agent.model.User;
import com.bllose.agent.repository.GuestSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;
    private final GuestSessionRepository guestSessionRepository;
    private final HttpServletRequest httpRequest;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       SessionManager sessionManager, GuestSessionRepository guestSessionRepository,
                       HttpServletRequest httpRequest) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
        this.guestSessionRepository = guestSessionRepository;
        this.httpRequest = httpRequest;
    }

    public AuthResponse register(AuthRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user = userRepository.save(user);
        // bind fingerprint to the new user
        userRepository.bindFingerprint(user.getId(), req.fingerprint());
        String sessionId = sessionManager.createSession(user.getId(), user.getUserNumber());
        return new AuthResponse(sessionId, user.getUsername(), user.getUserNumber());
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByUsername(req.username())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        // bind fingerprint if provided (new device/browser)
        userRepository.bindFingerprint(user.getId(), req.fingerprint());
        String sessionId = sessionManager.createSession(user.getId(), user.getUserNumber());
        return new AuthResponse(sessionId, user.getUsername(), user.getUserNumber());
    }

    public AuthResponse guestLogin(GuestRequest req) {
        String fingerprint = buildFingerprint(req);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(24);

        GuestSession guest = guestSessionRepository.findByFingerprint(fingerprint).orElse(null);

        if (guest != null) {
            // existing fingerprint — check if current session is still valid
            if (guest.getLastLogin() != null && guest.getLastLogin().plusHours(24).isAfter(now)
                && guest.getLastSession() != null && !guest.getLastSession().isBlank()) {
                // session still valid, reuse it
                return new AuthResponse(guest.getLastSession(), guest.getGuestName(), null);
            }
            // expired — generate new session
            String newSessionId = "guest-" + UUID.randomUUID();
            guest.setLastLogin(now);
            guest.setAuthExpiry(expiry);
            guest.setLastSession(newSessionId);
            guestSessionRepository.update(guest);
            return new AuthResponse(newSessionId, guest.getGuestName(), null);
        }

        // new guest
        String newSessionId = "guest-" + UUID.randomUUID();
        guest = new GuestSession();
        guest.setFingerprint(fingerprint);
        guest.setFingerprintHash(req.fingerprint() != null ? req.fingerprint() : "");
        guest.setGuestName("Guest");
        guest.setFirstLogin(now);
        guest.setLastLogin(now);
        guest.setAuthExpiry(expiry);
        guest.setLastSession(newSessionId);
        guest.setRequestCount(0);
        guest.setLastVisits(List.of());
        guestSessionRepository.save(guest);
        return new AuthResponse(newSessionId, guest.getGuestName(), null);
    }

    public AuthResponse helloGuest() {
        String fingerprint = "anon-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(24);
        String newSessionId = "guest-" + UUID.randomUUID();

        GuestSession guest = new GuestSession();
        guest.setFingerprint(fingerprint);
        guest.setFingerprintHash("");
        guest.setGuestName("Guest");
        guest.setFirstLogin(now);
        guest.setLastLogin(now);
        guest.setAuthExpiry(expiry);
        guest.setLastSession(newSessionId);
        guest.setRequestCount(0);
        guest.setLastVisits(List.of());
        guestSessionRepository.save(guest);
        return new AuthResponse(newSessionId, guest.getGuestName(), null);
    }

    private String buildFingerprint(GuestRequest req) {
        String ip = httpRequest.getRemoteAddr();
        return String.join("|",
            req.fingerprint() != null ? req.fingerprint() : "",
            ip,
            req.userAgent() != null ? req.userAgent() : "",
            req.platform() != null ? req.platform() : "",
            req.screenInfo() != null ? req.screenInfo() : "",
            req.language() != null ? req.language() : "",
            req.timezone() != null ? req.timezone() : ""
        );
    }
}
