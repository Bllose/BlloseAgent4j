package com.bllose.agent.auth;

import com.bllose.agent.model.GuestSession;
import com.bllose.agent.repository.GuestSessionRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class GuestActivityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GuestActivityInterceptor.class);

    private final GuestSessionRepository guestSessionRepository;

    public GuestActivityInterceptor(GuestSessionRepository guestSessionRepository) {
        this.guestSessionRepository = guestSessionRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        String sessionId = request.getHeader("X-Session-Id");
        String fingerprint = request.getHeader("X-Fingerprint");
        String uri = request.getRequestURI();

        if (sessionId == null || sessionId.isBlank()) {
            log.debug("No X-Session-Id for {}", uri);
            return true;
        }

        // skip if not a guest session (guest sessionIds start with "guest-")
        if (!sessionId.startsWith("guest-")) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1) lookup by last_session
        GuestSession guest = guestSessionRepository.findByLastSession(sessionId).orElse(null);

        if (guest != null) {
            // backfill fingerprint_hash if missing
            if ((guest.getFingerprintHash() == null || guest.getFingerprintHash().isBlank())
                && fingerprint != null && !fingerprint.isBlank()) {
                guest.setFingerprintHash(fingerprint);
            }
            // check expiry (24h since last_login)
            if (guest.getLastLogin() != null && guest.getLastLogin().plusHours(24).isAfter(now)) {
                // valid — count the request
                recordActivity(guest, now);
                return true;
            }
            // expired — regenerate sessionId, notify frontend, count
            String newSessionId = "guest-" + UUID.randomUUID();
            guest.setLastSession(newSessionId);
            guest.setLastLogin(now);
            recordActivity(guest, now);
            response.setHeader("X-Session-Id", newSessionId);
            response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
            log.info("Guest session renewed: recordId={}, newSession={}, uri={}", guest.getId(), newSessionId, uri);
            return true;
        }

        // 2) sessionId not found in DB — fallback to fingerprint hash
        if (fingerprint != null && !fingerprint.isBlank()) {
            guest = guestSessionRepository.findByFingerprintHash(fingerprint).orElse(null);
        }

        if (guest != null) {
            // fingerprint found — regenerate sessionId, notify frontend, count
            String newSessionId = "guest-" + UUID.randomUUID();
            guest.setLastSession(newSessionId);
            guest.setLastLogin(now);
            recordActivity(guest, now);
            response.setHeader("X-Session-Id", newSessionId);
            response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
            log.info("Guest session recovered via fingerprint: recordId={}, newSession={}, uri={}", guest.getId(), newSessionId, uri);
            return true;
        }

        // 3) both sessionId and fingerprint unknown — create new guest, count
        String newFingerprint = fingerprint != null && !fingerprint.isBlank()
            ? fingerprint
            : "anon-" + UUID.randomUUID();
        String newSessionId = "guest-" + UUID.randomUUID();
        guest = new GuestSession();
        guest.setFingerprint(newFingerprint);
        guest.setFingerprintHash(fingerprint != null && !fingerprint.isBlank() ? fingerprint : "");
        guest.setGuestName("Guest");
        guest.setFirstLogin(now);
        guest.setLastLogin(now);
        guest.setAuthExpiry(now.plusHours(24));
        guest.setLastSession(newSessionId);
        guest.setRequestCount(1);
        guest.setLastVisits(List.of(now));
        guestSessionRepository.save(guest);
        response.setHeader("X-Session-Id", newSessionId);
        response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
        log.info("New guest created via chat: recordId={}, session={}, uri={}", guest.getId(), newSessionId, uri);
        return true;
    }

    private void recordActivity(GuestSession guest, LocalDateTime now) {
        guest.setRequestCount(guest.getRequestCount() + 1);
        List<LocalDateTime> visits = new ArrayList<>(guest.getLastVisits());
        visits.add(0, now);
        if (visits.size() > 5) {
            visits = visits.subList(0, 5);
        }
        guest.setLastVisits(visits);
        guestSessionRepository.update(guest);
        log.info("Guest activity recorded: recordId={}, requestCount={}", guest.getId(), guest.getRequestCount());
    }
}
