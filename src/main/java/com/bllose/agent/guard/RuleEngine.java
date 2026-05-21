package com.bllose.agent.guard;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    private final GuardRulesConfig config;

    private List<Pattern> injectionPatterns = List.of();
    private List<Pattern> technicalAttackPatterns = List.of();

    public RuleEngine(GuardRulesConfig config) {
        this.config = config;
    }

    @PostConstruct
    void compilePatterns() {
        injectionPatterns = compile(config.getInjectionPatterns());
        technicalAttackPatterns = compile(config.getTechnicalAttackPatterns());
        log.info("RuleEngine loaded: {} injection patterns, {} technical patterns, maxInputLength={}",
            injectionPatterns.size(), technicalAttackPatterns.size(),
            config.getMaxInputLength());
    }

    private List<Pattern> compile(List<String> regexes) {
        List<Pattern> result = new ArrayList<>();
        for (String r : regexes) {
            try {
                result.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS));
            } catch (Exception e) {
                log.error("Failed to compile guard regex: {}", r, e);
            }
        }
        return result;
    }

    /**
     * 规则引擎检查，纳秒/微秒级返回。
     */
    public GuardResult check(String message, String sessionId) {
        if (message == null || message.isBlank()) {
            return GuardResult.block("输入为空");
        }

        String trimmed = message.trim();
        int maxLen = config.getMaxInputLength();

        // ── 1. 长度限制 ──
        if (maxLen > 0 && trimmed.length() > maxLen) {
            log.warn("Input too long: {} chars, session={}", trimmed.length(), sessionId);
            return GuardResult.block("输入过长，请限制在 " + maxLen + " 字符以内");
        }

        // ── 2. 注入攻击检测 ──
        GuardResult injectionResult = checkPatterns(trimmed, injectionPatterns, "检测到提示词注入/越狱行为");
        if (injectionResult.blocked()) {
            log.warn("Injection blocked: session={}, reason={}", sessionId, injectionResult.reason());
            return injectionResult;
        }

        // ── 3. 技术攻击检测 ──
        GuardResult techResult = checkPatterns(trimmed, technicalAttackPatterns, "检测到技术攻击行为");
        if (techResult.blocked()) {
            log.warn("Technical attack blocked: session={}, reason={}", sessionId, techResult.reason());
            return techResult;
        }

        return GuardResult.allow();
    }

    private GuardResult checkPatterns(String input, List<Pattern> patterns, String baseReason) {
        for (Pattern p : patterns) {
            if (p.matcher(input).find()) {
                return GuardResult.block(baseReason + " (匹配: " + p.pattern().substring(0, Math.min(40, p.pattern().length())) + ")");
            }
        }
        return GuardResult.allow();
    }
}
