package com.bllose.agent.service;

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

    @Value("${app.download.dir:./downloads}")
    private String downloadDir;

    public PaperService(PaperAssistant registeredAssistant,
                        @Qualifier("guest") PaperAssistant guestAssistant) {
        this.registeredAssistant = registeredAssistant;
        this.guestAssistant = guestAssistant;
    }

    public String invokeChat(String sessionId, String userMessage) {
        PaperAssistant active = (sessionId != null && sessionId.startsWith("guest-"))
                ? guestAssistant : registeredAssistant;
        return active.chatInvoke(sessionId, userMessage);
    }

    public SseEmitter streamChat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

        PaperAssistant active = (sessionId != null && sessionId.startsWith("guest-"))
                ? guestAssistant : registeredAssistant;

        // Snapshot existing files so we can detect new downloads
        File downloadDirFile = Path.of(downloadDir).toAbsolutePath().normalize().toFile();
        Set<String> beforeFiles = new HashSet<>();
        File[] existingFiles = downloadDirFile.listFiles();
        if (existingFiles != null) {
            for (File f : existingFiles) {
                if (f.isFile()) beforeFiles.add(f.getName());
            }
        }

        active.chat(sessionId, userMessage)
                .onPartialThinking(thinking -> {
                    try {
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
