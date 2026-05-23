package com.bllose.agent.repository;

import com.bllose.agent.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbc;

    public ChatMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> new ChatMessage(
        rs.getLong("id"),
        rs.getString("chat_id"),
        rs.getInt("turn_num"),
        rs.getString("type"),
        rs.getString("thinking"),
        rs.getString("message"),
        rs.getTimestamp("created_at").toLocalDateTime()
    );

    public long save(ChatMessage msg) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO chat_messages (chat_id, turn_num, type, thinking, message) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, msg.getChatId());
            ps.setInt(2, msg.getTurnNum());
            ps.setString(3, msg.getType());
            ps.setString(4, msg.getThinking());
            ps.setString(5, msg.getMessage());
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public List<ChatMessage> findByChatId(String chatId, int lastNTurns) {
        return jdbc.query(
            "SELECT * FROM (SELECT * FROM chat_messages WHERE chat_id = ? ORDER BY turn_num DESC, id DESC LIMIT ?) sub ORDER BY turn_num ASC, id ASC",
            rowMapper, chatId, lastNTurns * 2
        );
    }

    public int getNextTurnNum(String chatId) {
        Integer max = jdbc.queryForObject(
            "SELECT COALESCE(MAX(turn_num), 0) FROM chat_messages WHERE chat_id = ?",
            Integer.class, chatId);
        return (max != null ? max : 0) + 1;
    }

    public int countByChatId(String chatId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chat_messages WHERE chat_id = ?", Integer.class, chatId);
        return count != null ? count : 0;
    }
}
