package com.bllose.agent.repository;

import com.bllose.agent.model.GuestSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class GuestSessionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GuestSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private final RowMapper<GuestSession> rowMapper = (rs, rowNum) -> {
        GuestSession gs = new GuestSession();
        gs.setId(rs.getLong("id"));
        gs.setFingerprint(rs.getString("fingerprint"));
        gs.setFingerprintHash(rs.getString("fingerprint_hash"));
        gs.setGuestName(rs.getString("guest_name"));
        gs.setFirstLogin(parseDateTime(rs.getString("first_login")));
        gs.setLastLogin(parseDateTime(rs.getString("last_login")));
        gs.setAuthExpiry(parseDateTime(rs.getString("auth_expiry")));
        gs.setLastSession(rs.getString("last_session"));
        gs.setRequestCount(rs.getInt("request_count"));
        gs.setLastVisits(parseVisits(rs.getString("last_visits")));
        gs.setCreatedAt(parseDateTime(rs.getString("created_at")));
        gs.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
        return gs;
    };

    private LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDateTime.parse(s.replace(" ", "T"));
    }

    private List<LocalDateTime> parseVisits(String json) {
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list.stream().map(this::parseDateTime).toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toVisitsJson(List<LocalDateTime> visits) {
        try {
            List<String> list = visits.stream().map(LocalDateTime::toString).toList();
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public Optional<GuestSession> findByFingerprint(String fingerprint) {
        var list = jdbc.query("SELECT * FROM guest_sessions WHERE fingerprint = ?", rowMapper, fingerprint);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<GuestSession> findByFingerprintHash(String hash) {
        var list = jdbc.query("SELECT * FROM guest_sessions WHERE fingerprint_hash = ?", rowMapper, hash);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<GuestSession> findById(Long id) {
        var list = jdbc.query("SELECT * FROM guest_sessions WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<GuestSession> findByLastSession(String lastSession) {
        var list = jdbc.query("SELECT * FROM guest_sessions WHERE last_session = ?", rowMapper, lastSession);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public GuestSession save(GuestSession gs) {
        jdbc.update(
            "INSERT INTO guest_sessions (fingerprint, fingerprint_hash, guest_name, first_login, last_login, auth_expiry, last_session, request_count, last_visits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            gs.getFingerprint(), gs.getFingerprintHash(), gs.getGuestName(),
            gs.getFirstLogin().toString(), gs.getLastLogin().toString(),
            gs.getAuthExpiry().toString(), gs.getLastSession(),
            gs.getRequestCount(), toVisitsJson(gs.getLastVisits())
        );
        return findByFingerprint(gs.getFingerprint()).orElseThrow();
    }

    public void update(GuestSession gs) {
        jdbc.update(
            "UPDATE guest_sessions SET last_login=?, auth_expiry=?, last_session=?, request_count=?, last_visits=?, updated_at=datetime('now') WHERE id=?",
            gs.getLastLogin().toString(), gs.getAuthExpiry().toString(),
            gs.getLastSession(),
            gs.getRequestCount(), toVisitsJson(gs.getLastVisits()),
            gs.getId()
        );
    }
}
