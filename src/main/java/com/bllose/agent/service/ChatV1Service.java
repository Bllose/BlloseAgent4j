package com.bllose.agent.service;

import dev.langchain4j.model.chat.response.PartialThinking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class ChatV1Service {

    private static final Logger log = LoggerFactory.getLogger(ChatV1Service.class);

    private final StreamingAssistant assistant;

    public ChatV1Service(StreamingAssistant assistant) {
        this.assistant = assistant;
    }

    public SseEmitter streamChat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

        assistant.chat(sessionId, userMessage)
                .onPartialThinking(thinking -> {
                    try {
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
