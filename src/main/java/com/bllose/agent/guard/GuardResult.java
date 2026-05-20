package com.bllose.agent.guard;

public record GuardResult(boolean blocked, String reason) {

    public static GuardResult allow() {
        return new GuardResult(false, "");
    }

    public static GuardResult block(String reason) {
        return new GuardResult(true, reason);
    }
}
