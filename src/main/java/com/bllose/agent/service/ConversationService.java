package com.bllose.agent.service;

import com.bllose.agent.model.ChatMessage;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.repository.ChatMessageRepository;
import com.bllose.agent.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<Conversation> listRecent(int userNumber, int limit) {
        return conversationRepository.findByUserNumber(userNumber, limit);
    }

    public Conversation getOrCreate(String chatId, int userNumber, String firstMessage) {
        if (chatId != null && !chatId.isBlank() && conversationRepository.existsByChatId(chatId)) {
            return conversationRepository.findByChatId(chatId).orElse(null);
        }
        String newChatId = UUID.randomUUID().toString();
        String title = firstMessage != null && firstMessage.length() > 30
                ? firstMessage.substring(0, 30) : firstMessage;
        Conversation conv = new Conversation();
        conv.setChatId(newChatId);
        conv.setUserNumber(userNumber);
        conv.setTitle(title != null ? title : "");
        conversationRepository.save(conv);
        return conversationRepository.findByChatId(newChatId).orElse(null);
    }

    public List<ChatMessage> loadHistory(String chatId, int lastNTurns) {
        return chatMessageRepository.findByChatId(chatId, lastNTurns);
    }

    public int getNextTurnNum(String chatId) {
        return chatMessageRepository.getNextTurnNum(chatId);
    }

    public void updateTitle(String chatId, String title) {
        conversationRepository.updateTitle(chatId, title);
    }
}
