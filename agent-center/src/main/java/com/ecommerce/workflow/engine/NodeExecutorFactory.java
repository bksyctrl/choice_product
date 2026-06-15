package com.ecommerce.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.WorkflowNode;
import com.ecommerce.workflow.mapper.WorkflowNodeMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodeExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(NodeExecutorFactory.class);
    
    @Autowired
    private ApplicationContext applicationContext;

    private final WorkflowNodeMapper workflowNodeMapper;
    private final Map<String, NodeExecutor> executorCache = new ConcurrentHashMap<>();

    public NodeExecutorFactory(WorkflowNodeMapper workflowNodeMapper) {
        this.workflowNodeMapper = workflowNodeMapper;
    }

    @PostConstruct
    public void init() {
        log.info("节点执行器工厂初始化...");
        loadAllExecutors();
    }

    public NodeExecutor getExecutor(String nodeCode) {
        NodeExecutor executor = executorCache.get(nodeCode);

        if (executor == null) {
            WorkflowNode nodeDef = workflowNodeMapper.selectOne(
                    new QueryWrapper<WorkflowNode>().eq("node_code", nodeCode).eq("status", 1)
            );

            if (nodeDef == null) {
                throw new RuntimeException("节点定义不存在: " + nodeCode);
            }

            if (nodeDef.getExecuteClass() != null && !nodeDef.getExecuteClass().isEmpty()) {
                try {
                    Class<?> clazz = Class.forName(nodeDef.getExecuteClass());
                    executor = (NodeExecutor) applicationContext.getBean(clazz);
                    executorCache.put(nodeCode, executor);
                } catch (Exception e) {
                    log.error("节点执行器加载失败 class={}", nodeDef.getExecuteClass(), e);
                    throw new RuntimeException("节点执行器初始化失败: " + nodeCode, e);
                }
            } else {
                throw new RuntimeException("节点执行类为空: " + nodeCode);
            }
        }

        return executor;
    }

    private void loadAllExecutors() {
        Map<String, NodeExecutor> executors = applicationContext.getBeansOfType(NodeExecutor.class);

        for (Map.Entry<String, NodeExecutor> entry : executors.entrySet()) {
            NodeExecutor executor = entry.getValue();
            executorCache.put(executor.getNodeCode(), executor);
            log.info("加载节点执行器: code={}, class={}", executor.getNodeCode(), executor.getClass().getName());
        }

        log.info("加载完成:{} 个节点执行器", executorCache.size());
    }
}
