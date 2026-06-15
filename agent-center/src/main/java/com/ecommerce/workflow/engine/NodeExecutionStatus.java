package com.ecommerce.workflow.engine;

public enum NodeExecutionStatus {
    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败"),
    SKIPPED("已跳过"),
    CANCELLED("已取消"),
    WAITING_REVIEW("等待审核");

    private final String description;

    NodeExecutionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}
