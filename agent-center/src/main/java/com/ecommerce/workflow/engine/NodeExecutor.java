package com.ecommerce.workflow.engine;

public interface NodeExecutor {
    String getNodeCode();

    NodeResult execute(ExecutionContext context) throws Exception;

    default void onSuccess(ExecutionContext context, NodeResult result) {}

    default void onFailure(ExecutionContext context, Exception e) {}
}
