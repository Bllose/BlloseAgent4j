package com.bllose.agent.service;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

@Service
public class ChatService {

    private final OpenAiStreamingChatModel streamingModel;

    public ChatService(OpenAiStreamingChatModel streamingModel) {
        this.streamingModel = streamingModel;
    }

    public SseEmitter streamChat(String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);

        streamingModel.chat(userMessage, new StreamingChatResponseHandler() {

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("thinking")
                            .data(partialThinking.text())
                    );
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onPartialResponse(String partialResponse) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("message")
                            .data(partialResponse)
                    );
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
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
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }
}
