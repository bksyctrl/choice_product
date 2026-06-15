package com.ecommerce.workflow.service.user;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.workflow.entity.UserProfile;
import com.ecommerce.workflow.mapper.UserProfileMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UserProfileService {
    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;
    public UserProfileService(UserProfileMapper userProfileMapper, ObjectMapper objectMapper) {
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
    }
    public UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(
                new QueryWrapper<UserProfile>()
                        .eq("user_id", userId)
                        .eq("deleted", 0)
        );
        if (profile == null) {
            profile = createDefaultProfile(userId);
        }
        return profile;
    }
    private UserProfile createDefaultProfile(Long userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setPreferences("{}");
        profile.setInterests("[]");
        profile.setBehaviorPatterns("{}");
        profile.setFrequentlyUsedSkills("[]");
        profile.setTopicExpertise("{}");
        profile.setCommunicationStyle("CASUAL");
        profile.setTotalSessions(0);
        profile.setTotalMessages(0);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profile.setDeleted(0);
        userProfileMapper.insert(profile);
        log.info("创建用户档案: userId={}", userId);
        return profile;
    }
    public void updatePreferences(Long userId, String key, Double value) {
        UserProfile profile = getOrCreateProfile(userId);
        try {
            Map<String, Double> prefs = objectMapper.readValue(
                    profile.getPreferences() != null ? profile.getPreferences() : "{}",
                    new TypeReference<Map<String, Double>>() {}
            );
            prefs.put(key, value);
            profile.setPreferences(objectMapper.writeValueAsString(prefs));
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            log.warn("更新用户偏好失败", e);
        }
    }
    public void addInterest(Long userId, String interest) {
        UserProfile profile = getOrCreateProfile(userId);
        try {
            List<String> interests = objectMapper.readValue(
                    profile.getInterests() != null ? profile.getInterests() : "[]",
                    new TypeReference<List<String>>() {}
            );
            if (!interests.contains(interest)) {
                interests.add(interest);
            }
            profile.setInterests(objectMapper.writeValueAsString(interests));
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            log.warn("添加用户兴趣失败", e);
        }
    }
    public void recordSkillUsage(Long userId, String skillId) {
        UserProfile profile = getOrCreateProfile(userId);
        try {
            Map<String, Integer> skillUsage = objectMapper.readValue(
                    profile.getFrequentlyUsedSkills() != null ? profile.getFrequentlyUsedSkills() : "{}",
                    new TypeReference<Map<String, Integer>>() {}
            );
            skillUsage.put(skillId, skillUsage.getOrDefault(skillId, 0) + 1);
            profile.setFrequentlyUsedSkills(objectMapper.writeValueAsString(skillUsage));
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            log.warn("记录技能使用失败", e);
        }
    }
    public void incrementSessionCount(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setTotalSessions(profile.getTotalSessions() + 1);
        profile.setLastActiveAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.updateById(profile);
    }
    public void incrementMessageCount(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setTotalMessages(profile.getTotalMessages() + 1);
        profile.setLastActiveAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.updateById(profile);
    }
    public void updateTopicExpertise(Long userId, String topic, Double score) {
        UserProfile profile = getOrCreateProfile(userId);
        try {
            Map<String, Double> expertise = objectMapper.readValue(
                    profile.getTopicExpertise() != null ? profile.getTopicExpertise() : "{}",
                    new TypeReference<Map<String, Double>>() {}
            );
            expertise.put(topic, score);
            profile.setTopicExpertise(objectMapper.writeValueAsString(expertise));
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            log.warn("更新用户专业度失败", e);
        }
    }
    public void setCommunicationStyle(Long userId, String style) {
        UserProfile profile = getOrCreateProfile(userId);
        profile.setCommunicationStyle(style);
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.updateById(profile);
    }
    public Map<String, Object> getProfileData(Long userId) {
        UserProfile profile = getOrCreateProfile(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", profile.getUserId());
        data.put("totalSessions", profile.getTotalSessions());
        data.put("totalMessages", profile.getTotalMessages());
        data.put("communicationStyle", profile.getCommunicationStyle());
        try {
            data.put("preferences", objectMapper.readValue(
                    profile.getPreferences() != null ? profile.getPreferences() : "{}",
                    new TypeReference<Map<String, Double>>() {}
            ));
            data.put("interests", objectMapper.readValue(
                    profile.getInterests() != null ? profile.getInterests() : "[]",
                    new TypeReference<List<String>>() {}
            ));
            data.put("frequentlyUsedSkills", objectMapper.readValue(
                    profile.getFrequentlyUsedSkills() != null ? profile.getFrequentlyUsedSkills() : "{}",
                    new TypeReference<Map<String, Integer>>() {}
            ));
            data.put("topicExpertise", objectMapper.readValue(
                    profile.getTopicExpertise() != null ? profile.getTopicExpertise() : "{}",
                    new TypeReference<Map<String, Double>>() {}
            ));
        } catch (Exception e) {
            log.warn("解析用户偏好数据失败", e);
        }
        return data;
    }
}
