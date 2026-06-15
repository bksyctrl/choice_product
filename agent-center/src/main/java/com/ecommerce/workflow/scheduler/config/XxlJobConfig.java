package com.ecommerce.workflow.scheduler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true", matchIfMissing = false)
public class XxlJobConfig {
    
    private static final Logger log = LoggerFactory.getLogger(XxlJobConfig.class);
    
    @Value("${xxl.job.admin.addresses:http://localhost:8080/xxl-job-admin}")
    private String adminAddresses;
    
    @Value("${xxl.job.executor.appname:ecommerce-workflow-executor}")
    private String appname;
    
    @Value("${xxl.job.executor.ip:}")
    private String ip;
    
    @Value("${xxl.job.executor.port:9999}")
    private int port;
    
    @Value("${xxl.job.executor.logpath:/data/applogs/xxl-job/jobhandler}")
    private String logPath;
    
    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logretentiondays;
    
    @Value("${xxl.job.accessToken:}")
    private String accessToken;
    
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        
        log.info("XXL-JOB执行器初始化完成: appname={}, port={}", appname, port);
        
        return xxlJobSpringExecutor;
    }
}
