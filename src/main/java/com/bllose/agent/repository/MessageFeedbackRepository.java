package com.bllose.agent.repository;

import com.bllose.agent.model.MessageFeedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageFeedbackRepository {

    private final JdbcTemplate jdbc;

    public MessageFeedbackRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<MessageFeedback> rowMapper = (rs, rowNum) -> new MessageFeedback(
        rs.getLong("id"),
        rs.getString("chat_id"),
        rs.getInt("turn_num"),
        rs.getString("user_identifier"),
        rs.getString("user_type"),
        rs.getString("rating"),
        rs.getString("feedback_text"),
        rs.getString("message_history"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at").toLocalDateTime()
    );

    public void upsert(MessageFeedback fb) {
        jdbc.update(
            "INSERT INTO message_feedback (chat_id, turn_num, user_identifier, user_type, rating, feedback_text, message_history) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(chat_id, turn_num) DO UPDATE SET " +
            "user_identifier = excluded.user_identifier, user_type = excluded.user_type, " +
            "rating = COALESCE(excluded.rating, message_feedback.rating), " +
            "feedback_text = COALESCE(excluded.feedback_text, message_feedback.feedback_text), " +
            "message_history = COALESCE(excluded.message_history, message_feedback.message_history), " +
            "updated_at = datetime('now')",
            fb.getChatId(), fb.getTurnNum(), fb.getUserIdentifier(), fb.getUserType(),
            fb.getRating(), fb.getFeedbackText(), fb.getMessageHistory()
        );
    }

    public void updateRating(String chatId, int turnNum, String rating) {
        jdbc.update(
            "INSERT INTO message_feedback (chat_id, turn_num, user_identifier, user_type, rating) " +
            "VALUES (?, ?, '', 'guest', ?) " +
            "ON CONFLICT(chat_id, turn_num) DO UPDATE SET rating = excluded.rating, updated_at = datetime('now')",
            chatId, turnNum, rating
        );
    }

    public void clearRating(String chatId, int turnNum) {
        jdbc.update(
            "UPDATE message_feedback SET rating = NULL, updated_at = datetime('now') " +
            "WHERE chat_id = ? AND turn_num = ?", chatId, turnNum);
    }

    public void updateFeedbackText(String chatId, int turnNum, String feedbackText) {
        jdbc.update(
            "INSERT INTO message_feedback (chat_id, turn_num, user_identifier, user_type, feedback_text) " +
            "VALUES (?, ?, '', 'guest', ?) " +
            "ON CONFLICT(chat_id, turn_num) DO UPDATE SET feedback_text = excluded.feedback_text, updated_at = datetime('now')",
            chatId, turnNum, feedbackText
        );
    }

    public MessageFeedback findByChatIdAndTurnNum(String chatId, int turnNum) {
        var list = jdbc.query(
            "SELECT * FROM message_feedback WHERE chat_id = ? AND turn_num = ?",
            rowMapper, chatId, turnNum);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<MessageFeedback> findByChatId(String chatId) {
        return jdbc.query(
            "SELECT * FROM message_feedback WHERE chat_id = ? ORDER BY turn_num",
            rowMapper, chatId);
    }
}
