package com.bllose.agent.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TrackedChatMemoryProvider implements ChatMemoryProvider {

    private final ConcurrentHashMap<String, ChatMemory> store = new ConcurrentHashMap<>();
    private final int maxMessages;

    public TrackedChatMemoryProvider(@Value("${langchain4j.memory.max-messages:20}") int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return store.computeIfAbsent(
            memoryId.toString(),
            id -> MessageWindowChatMemory.withMaxMessages(maxMessages)
        );
    }

    public List<ChatMessage> getMessages(String memoryId) {
        ChatMemory m = store.get(memoryId);
        return m != null ? m.messages() : List.of();
    }
}
