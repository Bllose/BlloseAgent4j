package com.bllose.agent.service;

import com.bllose.agent.config.TrackedChatMemoryProvider;
import com.bllose.agent.model.ChatMessage;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.model.MessageFeedback;
import com.bllose.agent.repository.ChatMessageRepository;
import com.bllose.agent.repository.MessageFeedbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.PartialThinking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatV1Service {

    private static final Logger log = LoggerFactory.getLogger(ChatV1Service.class);

    private final StreamingAssistant registeredAssistant;
    private final StreamingAssistant guestAssistant;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageFeedbackRepository messageFeedbackRepository;
    private final TrackedChatMemoryProvider trackedProvider;

    public ChatV1Service(StreamingAssistant registeredAssistant,
                         @Qualifier("guest") StreamingAssistant guestAssistant,
                         ConversationService conversationService,
                         ChatMessageRepository chatMessageRepository,
                         MessageFeedbackRepository messageFeedbackRepository,
                         TrackedChatMemoryProvider trackedProvider) {
        this.registeredAssistant = registeredAssistant;
        this.guestAssistant = guestAssistant;
        this.conversationService = conversationService;
        this.chatMessageRepository = chatMessageRepository;
        this.messageFeedbackRepository = messageFeedbackRepository;
        this.trackedProvider = trackedProvider;
    }

    public SseEmitter streamChat(String sessionId, String chatId, String userMessage, Integer userNumber) {
        SseEmitter emitter = new SseEmitter(0L);

        boolean isGuest = sessionId != null && sessionId.startsWith("guest-");
        StreamingAssistant active = isGuest ? guestAssistant : registeredAssistant;

        // Resolve or create conversation for registered users
        String memoryId;
        final String resolvedChatId;
        final int turnNum;
        if (!isGuest && userNumber != null) {
            Conversation conv = conversationService.getOrCreate(chatId, userNumber, userMessage);
            resolvedChatId = conv.getChatId();
            turnNum = conversationService.getNextTurnNum(resolvedChatId);
            memoryId = resolvedChatId;

            // Save user message
            ChatMessage userMsg = new ChatMessage();
            userMsg.setChatId(resolvedChatId);
            userMsg.setTurnNum(turnNum);
            userMsg.setType("user");
            userMsg.setMessage(userMessage);
            chatMessageRepository.save(userMsg);

            // Emit chatId so frontend knows the conversation ID
            try {
                emitter.send(SseEmitter.event().name("chatId").data(resolvedChatId));
            } catch (IOException ignored) {}
        } else {
            resolvedChatId = chatId != null && !chatId.isBlank() ? chatId : java.util.UUID.randomUUID().toString();
            turnNum = 1;
            memoryId = sessionId;
            try {
                emitter.send(SseEmitter.event().name("chatId").data(resolvedChatId));
            } catch (IOException ignored) {}
        }

        // Accumulators for AI response
        StringBuilder thinkingBuf = new StringBuilder();
        StringBuilder messageBuf = new StringBuilder();

        active.chat(memoryId, userMessage)
                .onPartialThinking(thinking -> {
                    try {
                        thinkingBuf.append(thinking.text());
                        emitter.send(
                            SseEmitter.event()
                                .name("thinking")
                                .data(thinking.text())
                        );
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onPartialToolCall(toolCall -> {
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name("tool")
                                .data(toolCall.name())
                        );
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onPartialResponse(token -> {
                    try {
                        messageBuf.append(token);
                        emitter.send(
                            SseEmitter.event()
                                .name("message")
                                .data(token)
                        );
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(response -> {
                    try {
                        // Persist AI message and message_history for registered users
                        if (!isGuest && userNumber != null && resolvedChatId != null) {
                            ChatMessage aiMsg = new ChatMessage();
                            aiMsg.setChatId(resolvedChatId);
                            aiMsg.setTurnNum(turnNum);
                            aiMsg.setType("ai");
                            aiMsg.setThinking(thinkingBuf.isEmpty() ? null : thinkingBuf.toString());
                            aiMsg.setMessage(messageBuf.toString());
                            chatMessageRepository.save(aiMsg);

                            saveMessageHistory(resolvedChatId, turnNum, memoryId);
                        } else if (isGuest && resolvedChatId != null) {
                            saveMessageHistory(resolvedChatId, turnNum, memoryId, true, sessionId);
                        }
                        emitter.send(
                            SseEmitter.event()
                                .name("done")
                                .data(Map.of("message", "[DONE]", "turnNum", turnNum))
                        );
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(error -> {
                    log.error("Chat stream error", error);
                    try {
                        String msg = resolveErrorMessage(error);
                        emitter.send(
                            SseEmitter.event()
                                .name("error")
                                .data(msg)
                        );
                        emitter.send(
                            SseEmitter.event()
                                .name("done")
                                .data("[DONE]")
                        );
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .start();

        return emitter;
    }

    private String resolveErrorMessage(Throwable error) {
        String msg = error.getMessage();
        if (msg != null && msg.contains("maxToolCallingRoundTrips")) {
            return "任务执行轮次超限（>100 轮），已自动终止。请简化问题后重试。";
        }
        if (isNetworkError(error)) {
            return "网络开小差了，请检查网络，稍后再试";
        }
        return msg != null ? msg : "Unknown error";
    }

    private boolean isNetworkError(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            String cn = cause.getClass().getName();
            if (cn.contains("UnresolvedAddress")
                    || cn.contains("ConnectException")
                    || cn.contains("SocketException")
                    || cn.contains("SSLException")
                    || cn.contains("UnknownHost")
                    || cn.contains("HttpTimeout")
                    || cn.contains("ConnectTimeout")) {
                return true;
            }
            String m = cause.getMessage();
            if (m != null && (m.contains("Broken pipe")
                    || m.contains("Connection reset")
                    || m.contains("timeout")
                    || m.contains("Network is unreachable"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void saveMessageHistory(String chatId, int turnNum, String memoryId) {
        saveMessageHistory(chatId, turnNum, memoryId, false, null);
    }

    private void saveMessageHistory(String chatId, int turnNum, String memoryId,
                                     boolean isGuest, String sessionId) {
        try {
            String json = serializeChatMemory(memoryId);
            String userIdentifier;
            String userType;
            if (isGuest && sessionId != null) {
                userIdentifier = "guest-" + sessionId;
                userType = "guest";
            } else {
                userIdentifier = "registered";
                userType = "registered";
            }
            MessageFeedback fb = new MessageFeedback();
            fb.setChatId(chatId);
            fb.setTurnNum(turnNum);
            fb.setUserIdentifier(userIdentifier);
            fb.setUserType(userType);
            fb.setMessageHistory(json);
            messageFeedbackRepository.upsert(fb);
        } catch (Exception e) {
            log.warn("Failed to save message_history: {}", e.getMessage());
        }
    }

    private String serializeChatMemory(String memoryId) {
        List<dev.langchain4j.data.message.ChatMessage> msgs = trackedProvider.getMessages(memoryId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (dev.langchain4j.data.message.ChatMessage msg : msgs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", msg.type().name());
            if (msg instanceof SystemMessage sm) {
                m.put("text", sm.text());
            } else if (msg instanceof UserMessage um) {
                m.put("text", um.singleText());
            } else if (msg instanceof AiMessage am) {
                m.put("text", am.text());
                if (am.toolExecutionRequests() != null && !am.toolExecutionRequests().isEmpty()) {
                    m.put("toolCalls", am.toolExecutionRequests().stream()
                        .map(ter -> Map.of("name", ter.name(), "arguments", ter.arguments()))
                        .toList());
                }
            } else if (msg instanceof ToolExecutionResultMessage tr) {
                m.put("toolName", tr.toolName());
                m.put("text", tr.text());
            }
            list.add(m);
        }
        try {
            return new ObjectMapper().writeValueAsString(list);
        } catch (Exception e) {
            log.warn("Failed to serialize chat memory: {}", e.getMessage());
            return "[]";
        }
    }
}
