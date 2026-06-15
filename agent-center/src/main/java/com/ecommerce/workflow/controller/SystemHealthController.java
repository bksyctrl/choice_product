package com.ecommerce.workflow.controller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${xxl.job.admin.addresses:http://localhost:8080/xxl-job-admin}")
    private String xxlJobAdminAddresses;

    @GetMapping("/health")
    public ApiResponse<List<Map<String, Object>>> getSystemHealth() {
        List<Map<String, Object>> services = new ArrayList<>();

        services.add(checkMySQL());
        services.add(checkRedis());
        services.add(checkMilvus());
        services.add(checkRabbitMQ());
        services.add(checkMinIO());
        services.add(checkXXLJob());

        return ApiResponse.success(services);
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("application", "Video Product Workflow System");
        info.put("version", "1.0.0");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osVersion", System.getProperty("os.version"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("maxMemory", Runtime.getRuntime().maxMemory() / 1024 / 1024 + " MB");
        info.put("totalMemory", Runtime.getRuntime().totalMemory() / 1024 / 1024 + " MB");
        info.put("freeMemory", Runtime.getRuntime().freeMemory() / 1024 / 1024 + " MB");
        info.put("timestamp", new java.util.Date());
        
        return ApiResponse.success(info);
    }

    private Map<String, Object> checkMySQL() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "mysql");
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            service.put("status", valid ? "healthy" : "error");
            service.put("latency", System.currentTimeMillis() - start);
        } catch (Exception e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", e.getMessage());
        }
        return service;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "redis");
        long start = System.currentTimeMillis();
        try {
            if (redisConnectionFactory != null) {
                redisConnectionFactory.getConnection().ping();
                service.put("status", "healthy");
            } else {
                service.put("status", "warning");
                service.put("error", "Redis connection factory not configured");
            }
            service.put("latency", System.currentTimeMillis() - start);
        } catch (Exception e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", e.getMessage());
        }
        return service;
    }

    private Map<String, Object> checkMilvus() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "milvus");
        long start = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(milvusHost, milvusPort), 3000);
            service.put("status", "healthy");
            service.put("latency", System.currentTimeMillis() - start);
        } catch (IOException e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", "Cannot connect to Milvus at " + milvusHost + ":" + milvusPort + " - " + e.getMessage());
        }
        return service;
    }

    private Map<String, Object> checkRabbitMQ() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "rabbitmq");
        long start = System.currentTimeMillis();
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(rabbitmqHost, rabbitmqPort), 3000);
            service.put("status", "healthy");
            service.put("latency", System.currentTimeMillis() - start);
        } catch (IOException e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", "Cannot connect to RabbitMQ at " + rabbitmqHost + ":" + rabbitmqPort + " - " + e.getMessage());
        }
        return service;
    }

    private Map<String, Object> checkMinIO() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "minio");
        long start = System.currentTimeMillis();
        
        try {
            URL url = new URL(minioEndpoint + "/minio/health/live");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                service.put("status", "healthy");
            } else {
                service.put("status", "warning");
                service.put("error", "MinIO returned status " + responseCode);
            }
            service.put("latency", System.currentTimeMillis() - start);
            connection.disconnect();
        } catch (Exception e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", "Cannot connect to MinIO at " + minioEndpoint + " - " + e.getMessage());
        }
        return service;
    }

    private Map<String, Object> checkXXLJob() {
        Map<String, Object> service = new HashMap<>();
        service.put("name", "xxljob");
        long start = System.currentTimeMillis();
        
        try {
            URL url = new URL(xxlJobAdminAddresses);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 400) {
                service.put("status", "healthy");
            } else {
                service.put("status", "warning");
                service.put("error", "XXL-JOB returned status " + responseCode);
            }
            service.put("latency", System.currentTimeMillis() - start);
            connection.disconnect();
        } catch (Exception e) {
            service.put("status", "error");
            service.put("latency", System.currentTimeMillis() - start);
            service.put("error", "Cannot connect to XXL-JOB at " + xxlJobAdminAddresses + " - " + e.getMessage());
        }
        return service;
    }
}
