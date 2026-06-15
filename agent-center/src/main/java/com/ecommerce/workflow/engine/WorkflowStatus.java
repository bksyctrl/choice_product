package com.ecommerce.workflow.engine;

public enum WorkflowStatus {
    PENDING("待执行"),
    RUNNING("运行中"),
    PAUSED("已暂停"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELLED("已取消"),
    ROLLED_BACK("已回滚");

    private final String description;

    WorkflowStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(WorkflowStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == RUNNING || newStatus == CANCELLED;
            case RUNNING -> newStatus == SUCCESS || newStatus == FAILED || newStatus == PAUSED || newStatus == CANCELLED;
            case PAUSED -> newStatus == RUNNING || newStatus == CANCELLED;
            case SUCCESS, FAILED, CANCELLED, ROLLED_BACK -> false;
        };
    }
}
