package com.bllose.agent.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    private ServerConfig paperMetadata;
    private ServerConfig paperDownload;
    private ServerConfig minimax;

    public ServerConfig getPaperMetadata() { return paperMetadata; }
    public void setPaperMetadata(ServerConfig paperMetadata) { this.paperMetadata = paperMetadata; }

    public ServerConfig getPaperDownload() { return paperDownload; }
    public void setPaperDownload(ServerConfig paperDownload) { this.paperDownload = paperDownload; }

    public ServerConfig getMinimax() { return minimax; }
    public void setMinimax(ServerConfig minimax) { this.minimax = minimax; }

    public static class ServerConfig {
        private List<String> command;
        private Map<String, String> env;

        public List<String> getCommand() { return command; }
        public void setCommand(List<String> command) { this.command = command; }

        public Map<String, String> getEnv() { return env; }
        public void setEnv(Map<String, String> env) { this.env = env; }
    }
}
