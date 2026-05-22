package com.bllose.agent.service;

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

    public ChatV1Service(StreamingAssistant registeredAssistant,
                         @Qualifier("guest") StreamingAssistant guestAssistant) {
        this.registeredAssistant = registeredAssistant;
        this.guestAssistant = guestAssistant;
    }

    public SseEmitter streamChat(String sessionId, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

        StreamingAssistant active = (sessionId != null && sessionId.startsWith("guest-"))
                ? guestAssistant : registeredAssistant;

        active.chat(sessionId, userMessage)
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
