package com.bllose.agent.service;

import com.bllose.agent.model.ChatMessage;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.repository.ChatMessageRepository;
import dev.langchain4j.model.chat.response.PartialThinking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class ChatV1Service {

    private static final Logger log = LoggerFactory.getLogger(ChatV1Service.class);

    private final StreamingAssistant registeredAssistant;
    private final StreamingAssistant guestAssistant;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;

    public ChatV1Service(StreamingAssistant registeredAssistant,
                         @Qualifier("guest") StreamingAssistant guestAssistant,
                         ConversationService conversationService,
                         ChatMessageRepository chatMessageRepository) {
        this.registeredAssistant = registeredAssistant;
        this.guestAssistant = guestAssistant;
        this.conversationService = conversationService;
        this.chatMessageRepository = chatMessageRepository;
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
            resolvedChatId = null;
            turnNum = 1;
            memoryId = sessionId;
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
                        // Persist AI message for registered users
                        if (!isGuest && userNumber != null && resolvedChatId != null) {
                            ChatMessage aiMsg = new ChatMessage();
                            aiMsg.setChatId(resolvedChatId);
                            aiMsg.setTurnNum(turnNum);
                            aiMsg.setType("ai");
                            aiMsg.setThinking(thinkingBuf.isEmpty() ? null : thinkingBuf.toString());
                            aiMsg.setMessage(messageBuf.toString());
                            chatMessageRepository.save(aiMsg);
                        }
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
}
