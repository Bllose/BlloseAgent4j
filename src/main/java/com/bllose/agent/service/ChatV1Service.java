package com.bllose.agent.service;

import dev.langchain4j.model.chat.response.PartialThinking;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class ChatV1Service {

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
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }
}
