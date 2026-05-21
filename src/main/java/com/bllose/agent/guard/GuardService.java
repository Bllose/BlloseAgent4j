package com.bllose.agent.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GuardService {

    private static final Logger log = LoggerFactory.getLogger(GuardService.class);

    private final RuleEngine ruleEngine;
    private final OllamaClassifier ollamaClassifier;

    public GuardService(RuleEngine ruleEngine, OllamaClassifier ollamaClassifier) {
        this.ruleEngine = ruleEngine;
        this.ollamaClassifier = ollamaClassifier;
    }

    /**
     * 三层安全漏斗：规则引擎(1ms) → 小模型分类(100-300ms)。
     * blocked 时抛出 GuardBlockedException，由全局异常处理器返回 403。
     */
    public void check(String message, String sessionId) {
        // Layer 1: 规则引擎 — 长度/注入/技术攻击（纳秒~毫秒级）
        GuardResult result = ruleEngine.check(message, sessionId);
        if (result.blocked()) {
            throw new GuardBlockedException(result.reason());
        }

        // Layer 2: 小模型分类 — paper/general/harmful 语义判断
        boolean isGuest = sessionId != null && sessionId.startsWith("guest-");
        String category = ollamaClassifier.classify(message);

        if (category == null) {
            // 模型调用失败，降级放行（避免误伤）
            log.warn("Ollama unavailable, allowing message: session={}", sessionId);
            return;
        }

        if ("harmful".equals(category)) {
            log.warn("Model blocked harmful: session={}", sessionId);
            throw new GuardBlockedException("检测到不安全内容，请求已被拒绝");
        }

        if (isGuest && !"paper".equals(category)) {
            log.info("Guest non-paper blocked: category={}, session={}", category, sessionId);
            throw new GuardBlockedException("Guest 用户仅支持论文检索和学术研究相关问题，请重新描述您的需求");
        }

        log.debug("Guard passed: category={}, guest={}, session={}", category, isGuest, sessionId);
    }
}
