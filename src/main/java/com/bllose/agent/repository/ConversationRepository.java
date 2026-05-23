package com.bllose.agent.repository;

import com.bllose.agent.model.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Conversation> rowMapper = (rs, rowNum) -> new Conversation(
        rs.getString("chat_id"),
        rs.getInt("user_number"),
        rs.getString("title"),
        rs.getTimestamp("created_at").toLocalDateTime()
    );

    public void save(Conversation conv) {
        jdbc.update(
            "INSERT INTO conversations (chat_id, user_number, title) VALUES (?, ?, ?)",
            conv.getChatId(), conv.getUserNumber(), conv.getTitle()
        );
    }

    public List<Conversation> findByUserNumber(int userNumber, int limit) {
        return jdbc.query(
            "SELECT * FROM conversations WHERE user_number = ? ORDER BY created_at DESC LIMIT ?",
            rowMapper, userNumber, limit
        );
    }

    public Optional<Conversation> findByChatId(String chatId) {
        var list = jdbc.query(
            "SELECT * FROM conversations WHERE chat_id = ?", rowMapper, chatId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void updateTitle(String chatId, String title) {
        jdbc.update("UPDATE conversations SET title = ? WHERE chat_id = ?", title, chatId);
    }

    public boolean existsByChatId(String chatId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM conversations WHERE chat_id = ?", Integer.class, chatId);
        return count != null && count > 0;
    }
}
