package com.bllose.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.bllose.agent.config.McpProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpProperties.class)
public class BlloseAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlloseAgentApplication.class, args);
    }
}
