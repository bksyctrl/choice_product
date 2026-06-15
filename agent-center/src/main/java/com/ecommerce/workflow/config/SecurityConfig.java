package com.ecommerce.workflow.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ecommerce.workflow.filter.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:3000", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/**",
                    "/actuator",
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/ws/**",
                    "/ws",
                    "/uploads/**",
                    "/h2-console/**",
                    "/api/ai-video/**",
                    "/api/ai-chat/**",
                    "/api/chat/**",
                    "/api/evolution/**",
                    "/api/explosive-rules/**",
                    "/api/config/ai/providers",
                    "/api/config/ai/providers/**",
                    "/api/milvus/**",
                    "/api/database/**",
                    "/api/database-index/**",
                    "/api/system/**",
                    "/api/analytics/**",
                    "/api/checkpoint/**",
                    "/api/scheduler/**",
                    "/api/scheduled-tasks/**",
                    "/api/mcp/**",
                    "/api/delegate/**",
                    "/api/smart-router/**",
                    "/api/filesystem/**",
                    "/api/skills/**",
                    "/api/workflow/**",
                    "/api/learning/**",
                    "/api/memory/**",
                    "/api/session/**",
                    "/api/knowledge/**",
                    "/api/ai-providers/**",
                    "/api/case-memory/**",
                    "/api/viral-patterns/**",
                    "/api/avoidance-rules/**",
                    "/api/dashboard/**",
                    "/api/agent/**",
                    "/api/dimension/**",
                    "/api/ai/**",
                    "/api/config/ai/**"
                ).permitAll()
                .requestMatchers(
                    "/api/config/init",
                    "/api/config/cache/**",
                    "/api/milvus/collections/**",
                    "/api/milvus/create/**",
                    "/api/milvus/drop/**"
                ).hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
