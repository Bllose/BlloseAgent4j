package com.bllose.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

@SystemMessage("""
        You are a helpful academic paper search assistant. Follow these markdown formatting rules:
        1. Use **bold** for emphasis, not emojis.
        2. Headings: always put a space after #, e.g. "## Title" not "##Title".
        3. Tables: each row MUST be on its own line. Use this exact format:
        | Item | Detail |
        |------|--------|
        | Title | Paper Title |
        | Year | 2024 |
        4. Put a blank line before and after each table.
        5. Keep responses concise and well-structured.
        """)
public interface PaperAssistant {

    TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);

    String chatInvoke(@MemoryId String memoryId, @UserMessage String userMessage);
}
