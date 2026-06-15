package com.ecommerce.workflow.service.vector;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MilvusService {
    private static final Logger log = LoggerFactory.getLogger(MilvusService.class);
    
    private MilvusServiceClient milvusClient;
    
    @Value("${milvus.enabled:true}")
    private boolean enabled;
    
    @Value("${milvus.host:192.168.88.222}")
    private String host;
    
    @Value("${milvus.port:19530}")
    private int port;
    
    @Value("${milvus.connect-timeout:10000}")
    private long connectTimeout;
    
    @Value("${milvus.keep-alive-time:30000}")
    private long keepAliveTime;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Milvus 服务已禁用");
            return;
        }
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(connectTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .withKeepAliveTime(keepAliveTime, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();
            
            milvusClient = new MilvusServiceClient(connectParam);
            log.info("Milvus 客户端连接成功: {}:{}", host, port);
            
            testConnection();
        } catch (Exception e) {
            log.error("Milvus 客户端连接失败", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (milvusClient != null) {
            try {
                milvusClient.close();
                log.info("Milvus 客户端已关闭");
            } catch (Exception e) {
                log.error("关闭 Milvus 客户端失败", e);
            }
        }
    }
    
    public boolean testConnection() {
        try {
            if (milvusClient == null) {
                log.error("Milvus 客户端未初始化");
                return false;
            }
            
            long startTime = System.currentTimeMillis();
            R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName("test_connection")
                    .build());
            long duration = System.currentTimeMillis() - startTime;
            
            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("Milvus 连接测试成功，响应时间: {}ms", duration);
                return true;
            } else {
                log.error("Milvus 连接测试失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Milvus 连接测试失败", e);
            return false;
        }
    }
    
    public boolean hasCollection(String collectionName) {
        try {
            R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            return response.getData() != null && response.getData();
        } catch (Exception e) {
            log.error("检查集合是否存在失败: {}", collectionName, e);
            return false;
        }
    }
}
