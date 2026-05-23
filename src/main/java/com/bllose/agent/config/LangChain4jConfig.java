package com.bllose.agent.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.bllose.agent.debug.OpenAiDebugListener;
import com.bllose.agent.service.PaperAssistant;
import com.bllose.agent.service.StreamingAssistant;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.openai.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.model-name:deepseek-v4-flash}")
    private String modelName;

    @Value("${langchain4j.openai.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.openai.temperature:0.7}")
    private Double temperature;

    @Value("${langchain4j.openai.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${langchain4j.openai.thinking.enabled:false}")
    private boolean thinkingEnabled;

    @Value("${langchain4j.openai.thinking.effort:high}")
    private String thinkingEffort;

    @Value("${langchain4j.memory.max-messages:20}")
    private int maxMessages;

    @Value("${langchain4j.openai.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.openai.log-responses:false}")
    private boolean logResponses;

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    private final McpProperties mcpProperties;

    public LangChain4jConfig(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    @Bean
    public OpenAiStreamingChatModel streamingChatModel() {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .returnThinking(true)
                .sendThinking(true);

        if (thinkingEnabled) {
            builder.customParameters(Map.of(
                "thinking", Map.of("type", "enabled"),
                "reasoning_effort", thinkingEffort
            ));
        }

        builder.logRequests(logRequests)
               .logResponses(logResponses);

        if (logRequests || logResponses) {
            builder.listeners(new OpenAiDebugListener());
        }

        return builder.build();
    }

    @Bean
    public OpenAiChatModel chatModel() {
        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(logRequests)
                .logResponses(logResponses);

        if (logRequests || logResponses) {
            builder.listeners(new OpenAiDebugListener());
        }

        return builder.build();
    }

    @Bean
    TrackedChatMemoryProvider chatMemoryProvider() {
        return new TrackedChatMemoryProvider(maxMessages);
    }

    @Primary
    @Bean
    StreamingAssistant streamingAssistant(
            OpenAiStreamingChatModel model,
            TrackedChatMemoryProvider chatMemoryProvider,
            ToolProvider allMcpToolProvider) {
        return AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(allMcpToolProvider)
                .build();
    }

    // ── MCP Clients ────────────────────────────────────────

    @Bean
    McpClient paperMetadataMcpClient() {
        var transport = new StdioMcpTransport.Builder()
                .command(mcpProperties.getPaperMetadata().getCommand())
                .build();
        return new DefaultMcpClient.Builder()
                .transport(transport)
                .key("paper-metadata")
                .build();
    }

    @Bean
    McpClient paperDownloadMcpClient() {
        var transportBuilder = new StdioMcpTransport.Builder()
                .command(mcpProperties.getPaperDownload().getCommand());
        if (mcpProperties.getPaperDownload().getEnv() != null) {
            transportBuilder.environment(mcpProperties.getPaperDownload().getEnv());
        }
        return new DefaultMcpClient.Builder()
                .transport(transportBuilder.build())
                .key("paper-download")
                .build();
    }

    private McpClient createMinimaxClient() {
        var transportBuilder = new StdioMcpTransport.Builder()
                .command(mcpProperties.getMinimax().getCommand());
        if (mcpProperties.getMinimax().getEnv() != null) {
            transportBuilder.environment(mcpProperties.getMinimax().getEnv());
        }
        return new DefaultMcpClient.Builder()
                .transport(transportBuilder.build())
                .key("minimax")
                .build();
    }

    @Bean
    ToolProvider allMcpToolProvider(
            McpClient paperMetadataMcpClient,
            McpClient paperDownloadMcpClient) {
        List<McpClient> clients = new ArrayList<>();
        clients.add(paperMetadataMcpClient);
        clients.add(paperDownloadMcpClient);

        try {
            clients.add(createMinimaxClient());
            log.info("MiniMax MCP client initialized");
        } catch (Exception e) {
            log.warn("MiniMax MCP 不可用，将以无网络搜索模式启动: {}", e.getMessage());
        }

        return McpToolProvider.builder()
                .mcpClients(clients)
                .build();
    }

    @Bean
    ToolProvider paperOnlyMcpToolProvider(
            McpClient paperMetadataMcpClient,
            McpClient paperDownloadMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(List.of(paperMetadataMcpClient, paperDownloadMcpClient))
                .build();
    }

    @Bean
    @Qualifier("guest")
    StreamingAssistant guestStreamingAssistant(
            OpenAiStreamingChatModel model,
            TrackedChatMemoryProvider chatMemoryProvider,
            ToolProvider paperOnlyMcpToolProvider) {
        return AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(paperOnlyMcpToolProvider)
                .systemMessage("""
                        You are a helpful assistant. Follow these markdown formatting rules:
                        1. Use **bold** for emphasis, not emojis.
                        2. Headings: always put a space after #, e.g. "## Title" not "##Title".
                        3. Tables: each row MUST be on its own line. Use this exact format:
                        | Item | Detail |
                        |------|--------|
                        | Title | Paper Title |
                        | Year | 2024 |
                        4. Put a blank line before and after each table.
                        5. Keep responses concise and well-structured.

                        ## Guest Notice
                        You are serving a guest (unregistered) user. Web search and image
                        recognition are NOT available. If you cannot provide an accurate
                        answer without these capabilities, kindly suggest the user to
                        register an account (注册账号) to unlock full internet search and
                        image analysis features.
                        """)
                .build();
    }

    @Primary
    @Bean
    PaperAssistant paperAssistant(
            OpenAiStreamingChatModel model,
            OpenAiChatModel chatModel,
            TrackedChatMemoryProvider chatMemoryProvider,
            ToolProvider allMcpToolProvider,
            com.bllose.agent.service.PaperToolService paperToolService) {
        return AiServices.builder(PaperAssistant.class)
                .streamingChatModel(model)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(allMcpToolProvider)
                .tools(paperToolService)
                .build();
    }

    @Bean
    @Qualifier("guest")
    PaperAssistant guestPaperAssistant(
            OpenAiStreamingChatModel model,
            OpenAiChatModel chatModel,
            TrackedChatMemoryProvider chatMemoryProvider,
            ToolProvider paperOnlyMcpToolProvider,
            com.bllose.agent.service.PaperToolService paperToolService) {
        return AiServices.builder(PaperAssistant.class)
                .streamingChatModel(model)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .toolProvider(paperOnlyMcpToolProvider)
                .tools(paperToolService)
                .systemMessage("""
                        You are an academic paper search assistant.

                        ## CRITICAL RULE — READ FIRST
                        You MUST NOT call any search tool (search_papers, download, etc.) until you
                        have confirmed the user's request is specific enough.

                        A query is TOO VAGUE (do NOT search, ask questions instead) when:
                        - It only mentions a broad concept without a discipline (e.g. "time papers",
                          "papers about energy", "science research", "AI papers", "math papers")
                        - The user asks for "papers" or "discussions" about a single generic word
                        - There is no specific field/technique/time-range mentioned

                        When the query is vague, your ONLY response is to ask clarifying questions:
                        1. Which specific field or discipline? (physics, philosophy, CS, medicine, etc.)
                        2. Any preferred time range?
                        3. Any particular angle or sub-topic?

                        You may search ONLY when the user has provided at least ONE of:
                        - A specific technique/method name (e.g. "Transformer", "CRISPR", "knowledge distillation")
                        - A clear discipline (e.g. "philosophy of time", "quantum physics", "cognitive science")
                        - A concrete topic with modifiers (e.g. "time perception in neuroscience", not just "time")
                        - A time range or paper type (e.g. "last 5 years", "survey papers on GANs")

                        Remember: "时间相关的论文" or "papers about time" → ASK, do NOT search.
                        "时间感知的神经科学研究" or "neuroscience of time perception" → OK to search.

                        ## Workflow (only after confirming specificity)
                        1. Use search_papers to find papers.
                        2. Call savePaperMetadata for each paper found (doi, title, authors, year, journal,
                           citationCount, paperAbstract, pdfUrl, bibtex).
                        3. For saved papers lookup, use searchSavedPapers.

                        ## Markdown formatting
                        1. Use **bold** for emphasis, not emojis.
                        2. Headings: always put a space after #, e.g. "## Title" not "##Title".
                        3. Tables: each row MUST be on its own line. Use this exact format:
                        | Item | Detail |
                        |------|--------|
                        | Title | Paper Title |
                        | Year | 2024 |
                        4. Put a blank line before and after each table.
                        5. Keep responses concise and well-structured.

                        ## Guest Notice
                        You are serving a guest (unregistered) user. Web search and image
                        recognition are NOT available. If you cannot provide an accurate
                        answer without these capabilities, kindly suggest the user to
                        register an account (注册账号) to unlock full internet search and
                        image analysis features.
                        """)
                .build();
    }
}
