package com.ecommerce.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.SysUser;
import com.ecommerce.workflow.mapper.SysUserMapper;
import com.ecommerce.workflow.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    private final Map<String, ResetCodeInfo> resetCodes = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRY_MS = 10 * 60 * 1000;

    private static class ResetCodeInfo {
        String code;
        long expiryTime;
        
        ResetCodeInfo(String code, long expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }

    public Map<String, Object> login(String username, String password) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        SysUser user = sysUserMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("role", user.getRole());
        result.put("avatar", user.getAvatar());
        return result;
    }

    @Transactional
    public void register(String username, String password, String nickname, String email) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        if (sysUserMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole("USER");
        user.setStatus(1);
        sysUserMapper.insert(user);
    }

    public String generateResetCode(String email) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        SysUser user = sysUserMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        
        String code = String.format("%06d", new Random().nextInt(1000000));
        resetCodes.put(email, new ResetCodeInfo(code, System.currentTimeMillis() + CODE_EXPIRY_MS));
        
        return code;
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        ResetCodeInfo codeInfo = resetCodes.get(email);
        
        if (codeInfo == null) {
            throw new RuntimeException("验证码不存在");
        }
        
        if (System.currentTimeMillis() > codeInfo.expiryTime) {
            resetCodes.remove(email);
            throw new RuntimeException("验证码已过期，请重新获取");
        }
        
        if (!codeInfo.code.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        SysUser user = sysUserMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        
        resetCodes.remove(email);
    }
}
