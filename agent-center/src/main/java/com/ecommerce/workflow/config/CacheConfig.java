package com.ecommerce.workflow.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CacheConfig implements CachingConfigurer {

    @Bean
    @Primary
    @Override
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
            "skillConfig",
            "dimensionDefinition", 
            "userInfo",
            "successPattern",
            "dimensionWeight"
        );
    }
}
