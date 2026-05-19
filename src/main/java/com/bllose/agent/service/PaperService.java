package com.bllose.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Service
public class PaperService {

    private static final Logger log = LoggerFactory.getLogger(PaperService.class);

    private final PaperAssistant assistant;

    public PaperService(PaperAssistant assistant) {
        this.assistant = assistant;
    }

    public String invokeChat(String sessionId, String userMessage) {
        return assistant.chatInvoke(sessionId, userMessage);
    }

    public SseEmitter streamChat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

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
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }
}
