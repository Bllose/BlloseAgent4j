package com.bllose.agent.guard;

/**
 * 被 GuardService 拦截时抛出，由全局异常处理器返回 403。
 */
public class GuardBlockedException extends RuntimeException {
    public GuardBlockedException(String reason) {
        super(reason);
    }
}
