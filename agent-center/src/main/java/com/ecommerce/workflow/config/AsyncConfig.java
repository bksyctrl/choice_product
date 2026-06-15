package com.ecommerce.workflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "ecommerceTaskExecutor")
    public Executor ecommerceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ecommerce-async-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("电商异步任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("电商异步任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Bean(name = "learningTaskExecutor")
    public Executor learningTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("learning-async-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("学习任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("学习任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Bean(name = "expertAnalysisExecutor")
    public Executor expertAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("expert-analysis-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("专家分析任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("专家分析任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Bean(name = "sceneImageExecutor")
    public Executor sceneImageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("scene-image-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("场景图生成任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("场景图生成任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Bean(name = "mcpInspectionExecutor")
    public Executor mcpInspectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("mcp-inspection-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("MCP质检任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("MCP质检任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    @Bean(name = "videoGenerationExecutor")
    public Executor videoGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("video-gen-");
        
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("视频生成任务队列已满，任务被拒绝");
        });
        
        executor.initialize();
        
        log.info("视频生成任务执行器已初始化: corePoolSize={}, maxPoolSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}
