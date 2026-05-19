package com.bllose.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final PaperAssistant assistant;

    @Value("${app.download.dir:./downloads}")
    private String downloadDir;

    public PaperService(PaperAssistant assistant) {
        this.assistant = assistant;
    }

    public String invokeChat(String sessionId, String userMessage) {
        return assistant.chatInvoke(sessionId, userMessage);
    }

    public SseEmitter streamChat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

        // Snapshot existing files so we can detect new downloads
        File downloadDirFile = Path.of(downloadDir).toAbsolutePath().normalize().toFile();
        Set<String> beforeFiles = new HashSet<>();
        File[] existingFiles = downloadDirFile.listFiles();
        if (existingFiles != null) {
            for (File f : existingFiles) {
                if (f.isFile()) beforeFiles.add(f.getName());
            }
        }

        assistant.chat(sessionId, userMessage)
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
                        String msg = error.getMessage();
                        if (msg != null && msg.contains("maxToolCallingRoundTrips")) {
                            msg = "任务执行轮次超限（>100 轮），已自动终止。请简化问题后重试。";
                        }
                        emitter.send(
                            SseEmitter.event()
                                .name("error")
                                .data(msg != null ? msg : "Unknown error")
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
}
