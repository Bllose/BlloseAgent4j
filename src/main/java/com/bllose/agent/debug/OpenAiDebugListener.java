package com.bllose.agent.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;

/**
 * Logs the raw ChatRequest sent to the LLM and the complete ChatResponse
 * received back, at the LangChain4j framework boundary — before TokenStream
 * callbacks process individual chunks.
 */
public class OpenAiDebugListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(OpenAiDebugListener.class);

    @Override
    public void onRequest(ChatModelRequestContext context) {
        var request = context.chatRequest();
        log.info("══════════ LLM REQUEST ══════════");
        log.info("Model:      {}", "OpenAiChatModel");
        log.info("Parameters: {}", request.parameters());
        log.info("Messages:");
        for (var msg : request.messages()) {
            log.info("  [{}] {}", msg.type(), msg);
        }
        log.info("══════════════════════════════════");
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        var response = context.chatResponse();
        log.info("══════════ LLM RESPONSE ══════════");
        log.info("Model:     {}", "OpenAiChatModel");
        if (response.aiMessage().hasToolExecutionRequests()) {
            log.info("Tool calls:");
            for (var tc : response.aiMessage().toolExecutionRequests()) {
                log.info("  {} → {}", tc.name(), tc.arguments());
            }
        }
        log.info("Content:   {}", response.aiMessage().text());
        log.info("══════════════════════════════════");
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        log.error("══════════ LLM ERROR ════════════");
        log.error("Model: {}", "OpenAiChatModel");
        log.error("Error:  ", context.error());
        log.error("══════════════════════════════════");
    }
}
