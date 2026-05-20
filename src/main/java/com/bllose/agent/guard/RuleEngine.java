package com.bllose.agent.guard;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        log.info("RuleEngine loaded: {} injection patterns, {} technical patterns, {} EN/{} CN whitelist keywords, maxInputLength={}",
            injectionPatterns.size(), technicalAttackPatterns.size(),
            config.getGuest().getPaperWhitelistEn().size(),
            config.getGuest().getPaperWhitelistCn().size(),
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

        // ── 4. Guest 用户领域限制 ──
        if (sessionId != null && sessionId.startsWith("guest-")) {
            if (!isPaperRelated(trimmed)) {
                log.info("Guest non-paper request blocked: session={}, message={}",
                    sessionId, trimmed.substring(0, Math.min(100, trimmed.length())));
                return GuardResult.block("Guest 用户仅限于论文检索、下载和学术研究相关问题");
            }
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

    /**
     * 判断用户输入是否与论文/学术相关。
     * 规则层采用宽松策略：命中任一关键词即放行，未命中则拦截。
     */
    private boolean isPaperRelated(String input) {
        String lower = input.toLowerCase();

        for (String kw : config.getGuest().getPaperWhitelistEn()) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        for (String kw : config.getGuest().getPaperWhitelistCn()) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        // DOI pattern
        if (lower.matches(".*\\b10\\.\\d{4,}/.{3,}\\b.*")) {
            return true;
        }
        return false;
    }
}
