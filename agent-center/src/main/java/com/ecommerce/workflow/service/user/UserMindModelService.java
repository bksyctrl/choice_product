package com.ecommerce.workflow.service.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.entity.Knowledge;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.service.evolution.SkillConfigService;
import com.ecommerce.workflow.service.knowledge.KnowledgeService;

@Service
public class UserMindModelService {
    private static final Logger log = LoggerFactory.getLogger(UserMindModelService.class);
    private final UserProfileService userProfileService;
    private final KnowledgeService knowledgeService;
    private final SkillConfigService skillConfigService;

    public UserMindModelService(UserProfileService userProfileService,
            KnowledgeService knowledgeService,
            SkillConfigService skillConfigService) {
        this.userProfileService = userProfileService;
        this.knowledgeService = knowledgeService;
        this.skillConfigService = skillConfigService;
    }

    public void learnFromAction(Long userId, String actionType, Map<String, Object> actionData) {
        log.info("用户行为学习: userId={}, action={}", userId, actionType);
        switch (actionType) {
            case "SKILL_USED":
                handleSkillUsed(userId, actionData);
                break;
            case "TOPIC_DISCUSSED":
                handleTopicDiscussed(userId, actionData);
                break;
            case "PREFERENCE_SHOWN":
                handlePreferenceShown(userId, actionData);
                break;
            case "FEEDBACK_GIVEN":
                handleFeedbackGiven(userId, actionData);
                break;
            default:
                log.debug("未知行为类型: {}", actionType);
        }
    }

    private void handleSkillUsed(Long userId, Map<String, Object> actionData) {
        String skillId = (String) actionData.get("skillId");
        if (skillId != null) {
            userProfileService.recordSkillUsage(userId, skillId);
        }
    }

    private void handleTopicDiscussed(Long userId, Map<String, Object> actionData) {
        String topic = (String) actionData.get("topic");
        if (topic != null) {
            userProfileService.addInterest(userId, topic);
            Double currentScore = getTopicExpertise(userId, topic);
            userProfileService.updateTopicExpertise(userId, topic, currentScore + 0.1);
        }
    }

    private void handlePreferenceShown(Long userId, Map<String, Object> actionData) {
        String preference = (String) actionData.get("preference");
        Double value = (Double) actionData.get("value");
        if (preference != null && value != null) {
            userProfileService.updatePreferences(userId, preference, value);
        }
    }

    private void handleFeedbackGiven(Long userId, Map<String, Object> actionData) {
        String type = (String) actionData.get("type");
        if ("positive".equals(type)) {
            userProfileService.updatePreferences(userId, "satisfaction", 1.0);
        } else if ("negative".equals(type)) {
            userProfileService.updatePreferences(userId, "satisfaction", -0.5);
        }
    }

    private Double getTopicExpertise(Long userId, String topic) {
        Map<String, Object> profileData = userProfileService.getProfileData(userId);
        @SuppressWarnings("unchecked")
        Map<String, Double> expertise = profileData.get("topicExpertise") instanceof Map
                ? (Map<String, Double>) profileData.get("topicExpertise")
                : new HashMap<>();
        return expertise != null ? expertise.getOrDefault(topic, 0.5) : 0.5;
    }

    public List<SkillConfig> recommendSkills(Long userId, String context) {
        log.info("推荐技能: userId={}, context={}", userId, context);
        Map<String, Object> profileData = userProfileService.getProfileData(userId);
        @SuppressWarnings("unchecked")
        Map<String, Integer> usedSkills = profileData.get("frequentlyUsedSkills") instanceof Map
                ? (Map<String, Integer>) profileData.get("frequentlyUsedSkills")
                : new HashMap<>();
        List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();
        List<SkillConfig> recommended = new ArrayList<>();

        for (SkillConfig skill : allSkills) {
            double score = calculateRecommendationScore(skill, usedSkills, context);
            if (score > 0.3) {
                recommended.add(skill);
            }
        }

        recommended.sort((a, b) -> Double.compare(
                calculateRecommendationScore(b, usedSkills, context),
                calculateRecommendationScore(a, usedSkills, context)));

        return recommended.subList(0, Math.min(5, recommended.size()));
    }

    private double calculateRecommendationScore(SkillConfig skill, Map<String, Integer> usedSkills, String context) {
        double score = skill.getConfidence() != null ? skill.getConfidence() : 0.5;

        if (usedSkills != null && usedSkills.containsKey(skill.getSkillCode())) {
            score += 0.3;
        }

        if (context != null && skill.getSkillName() != null) {
            if (context.toLowerCase().contains(skill.getSkillName().toLowerCase())) {
                score += 0.2;
            }
        }

        return Math.min(score, 1.0);
    }

    public List<Knowledge> retrieveRelevantKnowledge(Long userId, String query) {
        log.info("检索相关知识: userId={}, query={}", userId, query);
        List<Knowledge> knowledge = knowledgeService.searchSimilar(query, 5);
        return knowledge;
    }

    public String personalizeResponse(Long userId, String response) {
        Map<String, Object> profileData = userProfileService.getProfileData(userId);
        String style = (String) profileData.getOrDefault("communicationStyle", "CASUAL");
        return switch (style) {
            case "FORMAL" -> makeFormal(response);
            case "TECHNICAL" -> makeTechnical(response);
            default -> response;
        };
    }

    private String makeFormal(String response) {
        return response;
    }

    private String makeTechnical(String response) {
        return response;
    }

    public Map<String, Object> getUserContext(Long userId) {
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> profileData = userProfileService.getProfileData(userId);
        context.put("profile", profileData);

        List<SkillConfig> recommendedSkills = recommendSkills(userId, null);
        context.put("recommendedSkills", recommendedSkills.stream()
                .map(s -> Map.of("id", s.getSkillCode(), "name", s.getSkillName()))
                .toList());

        return context;
    }

    public void updateFromSession(Long userId, List<String> messages, List<String> usedSkills) {
        userProfileService.incrementSessionCount(userId);
        userProfileService.incrementMessageCount(userId);

        for (String skillId : usedSkills) {
            userProfileService.recordSkillUsage(userId, skillId);
        }

        log.info("更新会话数据: userId={}", userId);
    }
}
