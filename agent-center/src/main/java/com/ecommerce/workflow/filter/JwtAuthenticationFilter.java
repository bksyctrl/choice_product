package com.ecommerce.workflow.filter;

import com.ecommerce.workflow.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    private final List<String> excludedPaths = Arrays.asList(
        "/actuator/**",
        "/actuator",
        "/api/auth/**",
        "/ws/**",
        "/uploads/**",
        "/api/ai-chat/**",
        "/api/chat/**",
        "/api/ai/**",
        "/api/ai-video/**",
        "/api/config/**",
        "/api/knowledge/**",
        "/api/case-memory/**",
        "/api/viral-patterns/**",
        "/api/avoidance-rules/**",
        "/api/workflow/**",
        "/api/skills/**",
        "/api/analytics/**",
        "/api/dashboard/**",
        "/api/agent/**",
        "/api/evolution/**",
        "/api/milvus/**",
        "/api/system/**",
        "/api/memory/**",
        "/api/session/**",
        "/api/learning/**",
        "/api/smart-router/**",
        "/api/delegate/**",
        "/api/mcp/**",
        "/api/dimension/**",
        "/api/database/**",
        "/api/database-index/**",
        "/api/checkpoint/**",
        "/api/scheduler/**",
        "/api/scheduled-tasks/**",
        "/api/filesystem/**",
        "/h2-console/**"
    );
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsername(token);
            String role = jwtUtil.getRole(token);

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
