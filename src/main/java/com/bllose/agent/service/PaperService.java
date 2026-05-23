package com.bllose.agent.service;

import com.bllose.agent.model.ChatMessage;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.model.MessageFeedback;
import com.bllose.agent.repository.ChatMessageRepository;
import com.bllose.agent.repository.MessageFeedbackRepository;
import com.bllose.agent.config.TrackedChatMemoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PaperService {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);

    private final PaperAssistant registeredAssistant;
    private final PaperAssistant guestAssistant;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageFeedbackRepository messageFeedbackRepository;
    private final TrackedChatMemoryProvider trackedProvider;

    @Value("${app.download.dir:./downloads}")
    private String downloadDir;

    public PaperService(PaperAssistant registeredAssistant,
                        @Qualifier("guest") PaperAssistant guestAssistant,
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

    public Map<String, String> invokeChat(String sessionId, String chatId, String userMessage, Integer userNumber) {
        boolean isGuest = sessionId != null && sessionId.startsWith("guest-");
        PaperAssistant active = isGuest ? guestAssistant : registeredAssistant;

        String memoryId;
        final String resolvedChatId;
        final int turnNum;
        if (!isGuest && userNumber != null) {
            Conversation conv = conversationService.getOrCreate(chatId, userNumber, userMessage);
            resolvedChatId = conv.getChatId();
            turnNum = conversationService.getNextTurnNum(resolvedChatId);
            memoryId = resolvedChatId;

            ChatMessage userMsg = new ChatMessage();
            userMsg.setChatId(resolvedChatId);
            userMsg.setTurnNum(turnNum);
            userMsg.setType("user");
            userMsg.setMessage(userMessage);
            chatMessageRepository.save(userMsg);
        } else {
            resolvedChatId = null;
            turnNum = 1;
            memoryId = sessionId;
        }

        String response = active.chatInvoke(memoryId, userMessage);

        if (!isGuest && userNumber != null && resolvedChatId != null) {
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setChatId(resolvedChatId);
            aiMsg.setTurnNum(turnNum);
            aiMsg.setType("ai");
            aiMsg.setMessage(response);
            chatMessageRepository.save(aiMsg);

            saveMessageHistory(resolvedChatId, turnNum, memoryId, isGuest, sessionId);
        }

        return Map.of("content", response, "chatId", resolvedChatId != null ? resolvedChatId : "",
                      "turnNum", String.valueOf(turnNum));
    }
    public SseEmitter streamChat(String sessionId, String chatId, String userMessage, Integer userNumber) {
        SseEmitter emitter = new SseEmitter(0L);

        boolean isGuest = sessionId != null && sessionId.startsWith("guest-");
        PaperAssistant active = isGuest ? guestAssistant : registeredAssistant;

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

        // Snapshot existing files so we can detect new downloads
        File downloadDirFile = Path.of(downloadDir).toAbsolutePath().normalize().toFile();
        Set<String> beforeFiles = new HashSet<>();
        File[] existingFiles = downloadDirFile.listFiles();
        if (existingFiles != null) {
            for (File f : existingFiles) {
                if (f.isFile()) beforeFiles.add(f.getName());
            }
        }

        active.chat(memoryId, userMessage)
                .onPartialThinking(thinking -> {
                    try {
                        thinkingBuf.append(thinking.text());
                        log.debug("thinking(len={}): >>>{}<<<",
                                thinking.text().length(), thinking.text());
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
                        log.debug("toolCall: {}", toolCall.name());
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
                        log.debug("token(len={}): >>>{}<<<",
                                token.length(), token);
                        emitter.send(
                            SseEmitter.event()
                                .name("message")
                                .data(Map.of("t", token))
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

                            saveMessageHistory(resolvedChatId, turnNum, memoryId, false, null);
                        } else if (isGuest && resolvedChatId != null) {
                            saveMessageHistory(resolvedChatId, turnNum, memoryId, true, sessionId);
                        }
                        // Emit download links for any new files
                        File[] afterFiles = downloadDirFile.listFiles();
                        if (afterFiles != null) {
                            for (File f : afterFiles) {
                                if (f.isFile() && !beforeFiles.contains(f.getName())) {
                                    log.info("New download detected: {}", f.getName());
                                    emitter.send(
                                        SseEmitter.event()
                                            .name("download")
                                            .data(Map.of(
                                                "filename", f.getName(),
                                                "url", "/api/downloads/" + f.getName()
                                            ))
                                    );
                                }
                            }
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
