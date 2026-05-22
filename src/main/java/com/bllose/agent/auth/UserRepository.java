package com.bllose.agent.auth;

import com.bllose.agent.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        Integer userNumber = null;
        try {
            userNumber = rs.getInt("user_number");
            if (rs.wasNull()) userNumber = null;
        } catch (Exception ignored) {}
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password"),
            userNumber,
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    };

    public Optional<User> findByUsername(String username) {
        var list = jdbc.query("SELECT * FROM users WHERE username = ?", rowMapper, username);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public User save(User user) {
        jdbc.update("INSERT INTO users (username, password) VALUES (?, ?)",
            user.getUsername(), user.getPassword());
        User saved = findByUsername(user.getUsername()).orElseThrow();
        jdbc.update("UPDATE users SET user_number = 100000 + id WHERE id = ?", saved.getId());
        return findByUsername(saved.getUsername()).orElseThrow();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public Optional<User> findById(Long id) {
        var list = jdbc.query("SELECT * FROM users WHERE id = ?", rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void bindFingerprint(Long userId, String fingerprintHash) {
        if (fingerprintHash == null || fingerprintHash.isBlank()) return;
        // ignore if already bound
        jdbc.update(
            "INSERT OR IGNORE INTO user_fingerprints (user_id, fingerprint_hash) VALUES (?, ?)",
            userId, fingerprintHash);
    }
}
