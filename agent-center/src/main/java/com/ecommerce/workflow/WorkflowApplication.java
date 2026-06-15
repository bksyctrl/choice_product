package com.ecommerce.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
@EnableScheduling
@EnableCaching
@EnableAsync
@MapperScan({"com.ecommerce.workflow.mapper", "com.ecommerce.workflow.delegate.mapper", "com.ecommerce.workflow.mcp.mapper", "com.ecommerce.workflow.skill.mapper", "com.ecommerce.workflow.scheduler.mapper", "com.ecommerce.workflow.checkpoint.mapper"})
public class WorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
