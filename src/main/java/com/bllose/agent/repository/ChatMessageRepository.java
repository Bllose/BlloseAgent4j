package com.bllose.agent.repository;

import com.bllose.agent.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    public void save(ChatMessage msg) {
        jdbc.update(
            "INSERT INTO chat_messages (chat_id, turn_num, type, thinking, message) VALUES (?, ?, ?, ?, ?)",
            msg.getChatId(), msg.getTurnNum(), msg.getType(), msg.getThinking(), msg.getMessage()
        );
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
