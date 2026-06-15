package com.ecommerce.workflow.controller;

import com.ecommerce.workflow.service.AuthService;
import com.ecommerce.workflow.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        try {
            Map<String, Object> result = authService.login(username, password);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");
        String email = request.get("email");
        try {
            authService.register(username, password, nickname, email);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "注册成功");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (!jwtUtil.validateToken(token)) {
                return ApiResponse.error("Token已过期或无效");
            }
            Map<String, Object> info = new HashMap<>();
            info.put("userId", jwtUtil.getUserId(token));
            info.put("username", jwtUtil.getUsername(token));
            info.put("role", jwtUtil.getRole(token));
            return ApiResponse.success(info);
        }
        return ApiResponse.error("未授权访问");
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                          @RequestBody(required = false) Map<String, String> body) {
        try {
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else if (body != null && body.containsKey("token")) {
                token = body.get("token");
            }

            if (token == null || token.isEmpty()) {
                return ApiResponse.error("请求中缺少token");
            }

            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String role = jwtUtil.getRole(token);

                String newToken = jwtUtil.generateToken(userId, username, role);

                Map<String, Object> result = new HashMap<>();
                result.put("token", newToken);
                result.put("userId", userId);
                result.put("username", username);
                result.put("role", role);
                return ApiResponse.success(result);
            } else {
                return ApiResponse.error("Token验证失败");
            }
        } catch (Exception e) {
            return ApiResponse.error("Token验证失败: " + e.getMessage());
        }
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            String code = authService.generateResetCode(email);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "重置密码邮件已发送，请查收邮箱");
            result.put("code", code);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        String newPassword = request.get("newPassword");
        try {
            authService.resetPassword(email, code, newPassword);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "密码重置成功");
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
