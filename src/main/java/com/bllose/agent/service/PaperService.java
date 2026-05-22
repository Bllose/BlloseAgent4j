package com.bllose.agent.service;

import com.bllose.agent.model.ChatMessage;
import com.bllose.agent.model.Conversation;
import com.bllose.agent.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class PaperService {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);

    private final PaperAssistant registeredAssistant;
    private final PaperAssistant guestAssistant;
    private final ConversationService conversationService;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${app.download.dir:./downloads}")
    private String downloadDir;

    public PaperService(PaperAssistant registeredAssistant,
                        @Qualifier("guest") PaperAssistant guestAssistant,
                        ConversationService conversationService,
                        ChatMessageRepository chatMessageRepository) {
        this.registeredAssistant = registeredAssistant;
        this.guestAssistant = guestAssistant;
        this.conversationService = conversationService;
        this.chatMessageRepository = chatMessageRepository;
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
        }

        return Map.of("content", response, "chatId", resolvedChatId != null ? resolvedChatId : "");
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
            resolvedChatId = null;
            turnNum = 1;
            memoryId = sessionId;
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
