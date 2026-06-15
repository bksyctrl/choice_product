package com.ecommerce.workflow.agent.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.IntentConfig;
import com.ecommerce.workflow.mapper.IntentConfigMapper;

import jakarta.annotation.PostConstruct;

@Component
public class IntentHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(IntentHandlerFactory.class);

    private final IntentConfigMapper intentConfigMapper;
    private final ApplicationContext applicationContext;
    private final Map<String, IntentHandler> handlerCache = new ConcurrentHashMap<>();
    private final Map<String, IntentConfig> configCache = new ConcurrentHashMap<>();

    public IntentHandlerFactory(IntentConfigMapper intentConfigMapper, ApplicationContext applicationContext) {
        this.intentConfigMapper = intentConfigMapper;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        loadIntentConfigs();
    }

    public void loadIntentConfigs() {
        log.info("开始加载意图配置...");

        List<IntentConfig> configs = intentConfigMapper.selectList(
                new QueryWrapper<IntentConfig>()
                        .eq("status", "ACTIVE")
                        .eq("deleted", 0));

        configCache.clear();
        for (IntentConfig config : configs) {
            configCache.put(config.getIntentCode(), config);
            log.info("加载意图配置: {} -> {}", config.getIntentCode(), config.getHandlerClass());
        }

        Map<String, IntentHandler> springHandlers = applicationContext.getBeansOfType(IntentHandler.class);
        for (IntentHandler handler : springHandlers.values()) {
            String intentCode = handler.getIntentCode();
            handlerCache.put(intentCode, handler);
            if (!configCache.containsKey(intentCode)) {
                IntentConfig synthetic = new IntentConfig();
                synthetic.setIntentCode(intentCode);
                synthetic.setIntentName(intentCode);
                synthetic.setDescription("Spring自动发现的意图处理器");
                synthetic.setHandlerClass(handler.getClass().getName());
                synthetic.setStatus("ACTIVE");
                synthetic.setDeleted(0);
                configCache.put(intentCode, synthetic);
                log.info("自动注册Spring意图处理器: {} -> {}", intentCode, handler.getClass().getName());
            }
        }

        log.info("意图配置加载完成，共{}个", configs.size());
    }

    public IntentHandler getHandler(String intentCode) {
        IntentHandler handler = handlerCache.get(intentCode);
        if (handler != null) {
            return handler;
        }

        IntentConfig config = configCache.get(intentCode);
        if (config == null) {
            log.warn("未找到意图配置: {}, 使用默认处理器", intentCode);
            if (!"general_chat".equals(intentCode)) {
                return getDefaultHandler();
            }
            return null;
        }

        try {
            Class<?> handlerClass = Class.forName(config.getHandlerClass());

            try {
                handler = (IntentHandler) applicationContext.getBean(handlerClass);
                log.debug("从Spring容器获取意图处理器: {} -> {}", intentCode, config.getHandlerClass());
            } catch (Exception e) {
                log.debug("Spring容器中未找到Handler，使用反射创建: {}", config.getHandlerClass());
                handler = (IntentHandler) handlerClass.getDeclaredConstructor().newInstance();
            }

            handlerCache.put(intentCode, handler);
            return handler;
        } catch (Exception e) {
            log.error("创建意图处理器失败: {} -> {}", intentCode, config.getHandlerClass(), e);
            if (!"general_chat".equals(intentCode)) {
                return getDefaultHandler();
            }
            return null;
        }
    }

    private IntentHandler getDefaultHandler() {
        IntentHandler handler = handlerCache.get("general_chat");
        if (handler != null) {
            return handler;
        }

        IntentConfig config = configCache.get("general_chat");
        if (config != null) {
            try {
                Class<?> handlerClass = Class.forName(config.getHandlerClass());
                handler = (IntentHandler) applicationContext.getBean(handlerClass);
                handlerCache.put("general_chat", handler);
                return handler;
            } catch (Exception e) {
                log.error("创建默认处理器失败", e);
            }
        }

        return null;
    }

    public IntentConfig getIntentConfig(String intentCode) {
        return configCache.get(intentCode);
    }

    public Map<String, IntentConfig> getAllIntentConfigs() {
        return new ConcurrentHashMap<>(configCache);
    }

    public void registerHandler(String intentCode, IntentHandler handler) {
        handlerCache.put(intentCode, handler);
        log.info("注册意图处理器: {}", intentCode);
    }

    public void reloadConfigs() {
        log.info("重新加载意图配置...");
        handlerCache.clear();
        loadIntentConfigs();
    }
}
