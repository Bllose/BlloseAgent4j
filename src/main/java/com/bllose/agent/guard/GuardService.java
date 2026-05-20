package com.bllose.agent.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GuardService {

    private static final Logger log = LoggerFactory.getLogger(GuardService.class);

    private final RuleEngine ruleEngine;

    public GuardService(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    /**
     * 安全检查。blocked 时抛出 GuardBlockedException，由全局异常处理器返回 403。
     * 后续可扩展向量匹配、小模型分类等层级。
     */
    public void check(String message, String sessionId) {
        GuardResult result = ruleEngine.check(message, sessionId);
        if (result.blocked()) {
            throw new GuardBlockedException(result.reason());
        }
    }
}
