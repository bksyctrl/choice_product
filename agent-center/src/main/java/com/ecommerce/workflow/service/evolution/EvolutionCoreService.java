package com.ecommerce.workflow.service.evolution;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.workflow.config.EvolutionConfig;
import com.ecommerce.workflow.entity.SkillConfig;
import com.ecommerce.workflow.service.ai.GptChatService;
import com.ecommerce.workflow.service.config.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EvolutionCoreService {
    private static final Logger log = LoggerFactory.getLogger(EvolutionCoreService.class);

    private final SkillConfigService skillConfigService;
    private final JdbcTemplate jdbcTemplate;
    private final GptChatService gptChatService;
    private final SysConfigService sysConfigService;
    private final EvolutionConfig config;
    private final ObjectMapper objectMapper;

    private final Map<String, LearningState> learningStates = new ConcurrentHashMap<>();
    private final Map<String, Double> qTable = new ConcurrentHashMap<>();
    private final List<LearningEpisode> episodes = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<EvolutionMemory>> evolutionMemories = new ConcurrentHashMap<>();
    private final Map<String, EffectiveStrategy> effectiveStrategies = new ConcurrentHashMap<>();
    private final Map<String, EvolutionRecord> evolutionHistory = new ConcurrentHashMap<>();

    private double explorationRate = 0.2;
    private double learningRate = 0.1;
    private double discountFactor = 0.9;
    private long lastEvolutionTime = 0;

    public EvolutionCoreService(SkillConfigService skillConfigService,
                                JdbcTemplate jdbcTemplate,
                                GptChatService gptChatService,
                                SysConfigService sysConfigService,
                                EvolutionConfig config) {
        this.skillConfigService = skillConfigService;
        this.jdbcTemplate = jdbcTemplate;
        this.gptChatService = gptChatService;
        this.sysConfigService = sysConfigService;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        loadQTableFromDB();
        loadEvolutionHistory();
    }

    @Scheduled(cron = "0 0 */6 * * ?")
    public void executeEvolutionCycle() {
        log.info("=== 开始统一进化周期 ===");
        long startTime = System.currentTimeMillis();

        try {
            validatePreviousEvolutions();
            runLearningIteration();
            analyzeAndOptimizeSkills();
            autoEvolveTopSkills();

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== 统一进化周期完成，耗时{}ms ===", duration);

        } catch (Exception e) {
            log.error("统一进化周期发生异常", e);
        }
    }

    public void recordUsage(String skillCode, boolean success, Double cvr, Double gmv) {
        try {
            updateLearningState(skillCode, success, cvr);
            recordEpisode(skillCode, success, cvr);
            
            if (cvr != null && cvr > 0.03) {
                updateQValue(skillCode, cvr);
            }

        } catch (Exception e) {
            log.warn("记录使用数据失败: skill={}", skillCode, e);
        }
    }

    private void updateLearningState(String skillCode, boolean success, Double cvr) {
        LearningState state = learningStates.computeIfAbsent(skillCode, k -> {
            LearningState newState = new LearningState();
            newState.setSkillCode(skillCode);
            newState.setStateId(generateStateId(skillCode));
            newState.setTimestamp(LocalDateTime.now());
            return newState;
        });

        double oldCvr = state.getCurrentCvr();
        state.setCurrentCvr(cvr != null ? cvr : 0.0);
        
        int totalEpisodes = countRecentEpisodes(skillCode, 50);
        int recentSuccesses = countRecentSuccesses(skillCode, 50);
        state.setCurrentSuccessRate(totalEpisodes > 0 ? (double) recentSuccesses / totalEpisodes : 0.0);

        if (oldCvr > 0) {
            int trend = cvr.compareTo(oldCvr);
            state.setRecentTrend(trend);
        }

        state.setStateHash(computeStateHash(state));
        state.setTimestamp(LocalDateTime.now());

        log.debug("更新学习状态: skill={}, cvr={}, successRate={}, trend={}", 
                  skillCode, cvr, state.getCurrentSuccessRate(), state.getRecentTrend());
    }

    private void recordEpisode(String skillCode, boolean success, Double cvr) {
        LearningEpisode episode = new LearningEpisode();
        episode.setEpisodeId(UUID.randomUUID().toString());
        episode.setSkillCode(skillCode);
        episode.setStateHash(getCurrentStateHash(skillCode));
        episode.setActionId(selectAction(skillCode));
        episode.setReward(calculateReward(success, cvr));
        episode.setVerified(false);
        episode.setBeforeMetric(getPreviousMetric(skillCode));
        episode.setAfterMetric(cvr != null ? cvr : 0.0);
        episode.setTimestamp(LocalDateTime.now());

        episodes.add(episode);

        if (episodes.size() > 10000) {
            episodes.subList(0, episodes.size() - 10000).clear();
        }

        persistEpisode(episode);
    }

    private String selectAction(String skillCode) {
        if (Math.random() < explorationRate) {
            return "EXPLORE_" + getRandomParamChange(skillCode);
        } else {
            return "EXPLOIT_" + getBestKnownAction(skillCode);
        }
    }

    private double calculateReward(boolean success, Double cvr) {
        double baseReward = success ? 1.0 : -0.5;
        
        if (cvr != null) {
            if (cvr > 0.10) baseReward += 2.0;
            else if (cvr > 0.05) baseReward += 1.0;
            else if (cvr > 0.03) baseReward += 0.5;
            else if (cvr < 0.01) baseReward -= 1.0;
        }

        return baseReward;
    }

    @Transactional
    public void runLearningIteration() {
        log.info("=== 运行Q-Learning迭代 ===");

        List<LearningEpisode> unverifiedEpisodes = episodes.stream()
                .filter(e -> !e.isVerified())
                .collect(Collectors.toList());

        for (LearningEpisode episode : unverifiedEpisodes) {
            try {
                boolean isMature = isEpisodeMature(episode);
                
                if (isMature) {
                    updateQValueFromEpisode(episode);
                    episode.setVerified(true);
                    log.debug("验证并更新Q值: skill={}, reward={}", 
                             episode.getSkillCode(), episode.getReward());
                }
            } catch (Exception e) {
                log.warn("处理学习回合失败: {}", episode.getEpisodeId(), e);
            }
        }

        syncQTableToDB();
        log.info("Q-Learning迭代完成，处理了{}个回合并", unverifiedEpisodes.size());
    }

    private boolean isEpisodeMature(LearningEpisode episode) {
        LocalDateTime episodeTime = episode.getTimestamp();
        long hoursSinceEpisode = java.time.Duration.between(episodeTime, LocalDateTime.now()).toHours();

        return hoursSinceEpisode >= 24;
    }

    private void updateQValueFromEpisode(LearningEpisode episode) {
        String stateKey = episode.getStateHash();
        String actionKey = episode.getActionId();
        String saKey = stateKey + "_" + actionKey;

        double currentQ = qTable.getOrDefault(saKey, 0.0);
        double reward = episode.getReward();
        
        String nextStateKey = episode.getNextStateHash();
        double maxNextQ = getMaxQForState(nextStateKey);
        
        double newQ = currentQ + learningRate * (reward + discountFactor * maxNextQ - currentQ);
        
        qTable.put(saKey, newQ);
    }

    private double getMaxQForState(String stateHash) {
        return qTable.entrySet().stream()
                .filter(e -> e.getKey().startsWith(stateHash + "_"))
                .map(Map.Entry::getValue)
                .max(Double::compare)
                .orElse(0.0);
    }

    private void updateQValue(String skillCode, double cvr) {
        String currentState = getCurrentStateHash(skillCode);
        String bestAction = getBestKnownAction(skillCode);
        String saKey = currentState + "_" + bestAction;

        double currentQ = qTable.getOrDefault(saKey, 0.0);
        double reward = cvr * 10.0;
        double newQ = currentQ + learningRate * (reward - currentQ);

        qTable.put(saKey, Math.max(0.0, Math.min(10.0, newQ)));
    }

    public void analyzeAndOptimizeSkills() {
        log.info("=== 分析和优化技能配置 ===");

        try {
            List<SkillConfig> allSkills = skillConfigService.getAllActiveSkills();

            for (SkillConfig skill : allSkills) {
                try {
                    analyzeSkillPerformance(skill);
                    generateOptimizationSuggestions(skill);
                } catch (Exception e) {
                    log.warn("分析技能失败: {}", skill.getSkillCode(), e);
                }
            }

        } catch (Exception e) {
            log.error("批量分析技能失败", e);
        }
    }

    private void analyzeSkillPerformance(SkillConfig skill) {
        String skillCode = skill.getSkillCode();
        
        Map<String, Object> performance = calculateSkillMetrics(skillCode);
        
        EvolutionMemory memory = new EvolutionMemory();
        memory.setStrategyType("PERFORMANCE_ANALYSIS");
        memory.setParams(performance);
        memory.setBeforeMetric((Double) performance.getOrDefault("avgCvrBefore", 0.0));
        memory.setAfterMetric((Double) performance.getOrDefault("avgCvrAfter", 0.0));
        memory.setEffective(memory.getAfterMetric() > memory.getBeforeMetric() * 1.1);
        memory.setTimestamp(LocalDateTime.now());

        evolutionMemories.computeIfAbsent(skillCode, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(memory);

        if (memory.isEffective()) {
            updateEffectiveStrategy(skillCode, memory);
        }
    }

    private Map<String, Object> calculateSkillMetrics(String skillCode) {
        Map<String, Object> metrics = new HashMap<>();

        try {
            Map<String, Object> recentData = jdbcTemplate.queryForMap("""
                SELECT 
                    COUNT(*) as totalTasks,
                    SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as successTasks,
                    AVG(CASE WHEN cvr IS NOT NULL THEN cvr ELSE 0 END) as avgCvr,
                    AVG(CASE WHEN gmv IS NOT NULL THEN gmv ELSE 0 END) as avgGmv
                FROM biz_video_task 
                WHERE skill_code = ? 
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                  AND deleted = 0
                """, skillCode);

            metrics.putAll(recentData);

            Map<String, Object> previousData = jdbcTemplate.queryForMap("""
                SELECT 
                    AVG(CASE WHEN cvr IS NOT NULL THEN cvr ELSE 0 END) as avgCvrBefore
                FROM biz_video_task 
                WHERE skill_code = ? 
                  AND created_at BETWEEN DATE_SUB(NOW(), INTERVAL 14 DAY) AND DATE_SUB(NOW(), INTERVAL 7 DAY)
                  AND deleted = 0
                """, skillCode);

            metrics.putAll(previousData);

        } catch (Exception e) {
            log.error("计算技能指标失败: skill={}", skillCode, e);
            metrics.put("totalTasks", 0);
            metrics.put("successTasks", 0);
            metrics.put("avgCvr", 0.0);
            metrics.put("avgGmv", 0.0);
        }

        return metrics;
    }

    private void generateOptimizationSuggestions(SkillConfig skill) {
        String skillCode = skill.getSkillCode();
        LearningState state = learningStates.get(skillCode);

        if (state == null) {
            return;
        }

        double currentCvr = state.getCurrentCvr();
        int trend = state.getRecentTrend();

        if (currentCvr < 0.02 || trend < -2) {
            log.info("检测到低性能技能: {}, cvr={}, trend={}, 生成优化建议", 
                     skillCode, currentCvr, trend);
            
            suggestParameterOptimization(skill);
        }
    }

    private void suggestParameterOptimization(SkillConfig skill) {
        try {
            String skillCode = skill.getSkillCode();
            String currentParams = objectMapper.writeValueAsString(skill.getParamsJson());

            String systemPrompt = "你是一个AI参数优化专家。请分析当前技能配置并给出优化建议。";
            String userMessage = String.format("""
                当前技能: %s
                当前参数: %s
                当前CVR: %.4f
                最近趋势: %s

                请给出3个具体的参数优化建议，每个建议包含：
                1. 参数名
                2. 当前值
                3. 建议值
                4. 优化理由

                以JSON数组格式返回。
                """,
                skillCode,
                currentParams,
                learningStates.getOrDefault(skillCode, new LearningState()).getCurrentCvr(),
                getTrendDescription(learningStates.getOrDefault(skillCode, new LearningState()).getRecentTrend())
            );

            String response = gptChatService.chat(systemPrompt, userMessage);

            if (response != null && !response.isEmpty()) {
                log.info("生成优化建议成功: skill={}, suggestions=\n{}", skillCode, response);
                storeOptimizationSuggestions(skillCode, response);
            }

        } catch (Exception e) {
            log.error("生成优化建议失败: skill={}", skill.getSkillCode(), e);
        }
    }

    public void autoEvolveTopSkills() {
        log.info("=== 自动进化顶级技能 ===");

        try {
            List<String> topSkills = identifyTopSkillsForEvolution(5);

            for (String skillCode : topSkills) {
                try {
                    evolveSingleSkill(skillCode);
                } catch (Exception e) {
                    log.warn("进化技能失败: {}", skillCode, e);
                }
        }

        } catch (Exception e) {
            log.error("批量进化技能失败", e);
        }
    }

    private List<String> identifyTopSkillsForEvolution(int limit) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT skill_code 
                FROM biz_video_task 
                WHERE status = 'completed' 
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                  AND deleted = 0
                GROUP BY skill_code 
                ORDER BY AVG(cvr) DESC, COUNT(*) DESC
                LIMIT ?
                """, String.class, limit);
        } catch (Exception e) {
            log.error("识别顶级技能失败", e);
            return new ArrayList<>();
        }
    }

    private void evolveSingleSkill(String skillCode) {
        log.info("开始进化技能: {}", skillCode);

        try {
            SkillConfig currentConfig = skillConfigService.getActiveSkill(skillCode);
            if (currentConfig == null) {
                log.warn("技能配置不存在: {}", skillCode);
                return;
            }

            double currentSuccessRate = calculateCurrentSuccessRate(skillCode);

            Map<String, Object> optimizedParams = optimizeParameters(currentConfig);

            if (optimizedParams != null && !optimizedParams.isEmpty()) {
                EvolutionRecord record = new EvolutionRecord(
                    skillCode,
                    "params",
                    objectMapper.writeValueAsString(currentConfig.getParamsJson()),
                    objectMapper.writeValueAsString(optimizedParams),
                    currentSuccessRate
                );

                evolutionHistory.put(skillCode + "_" + System.currentTimeMillis(), record);

                applyOptimizedParams(skillCode, optimizedParams);

                log.info("技能进化完成: skill={}, 新参数已应用", skillCode);
            }

        } catch (Exception e) {
            log.error("进化单个技能失败: skill={}", skillCode, e);
        }
    }

    private Map<String, Object> optimizeParameters(SkillConfig config) {
        Map<String, Object> params = new HashMap<>(config.getParamsJson());
        
        Random random = new Random();
        double mutationRate = 0.3;
        
        for (String key : params.keySet()) {
            if (random.nextDouble() < mutationRate) {
                Object value = params.get(key);
                
                if (value instanceof Number) {
                    double numValue = ((Number) value).doubleValue();
                    double change = (random.nextDouble() - 0.5) * 0.2 * numValue;
                    params.put(key, numValue + change);
                } else if (value instanceof String) {
                    String strValue = (String) value;
                    if (strValue.matches("\\d+")) {
                        int intValue = Integer.parseInt(strValue);
                        int change = random.nextInt(21) - 10;
                        params.put(key, String.valueOf(Math.max(1, intValue + change)));
                    }
                }
            }
        }

        return params;
    }

    private void applyOptimizedParams(String skillCode, Map<String, Object> newParams) {
        try {
            SkillConfig currentSkill = skillConfigService.getActiveSkill(skillCode);
            if (currentSkill != null) {
                skillConfigService.upgradeSkill(currentSkill, newParams, "自动优化: EvolutionCoreService");
                log.info("应用优化参数: skill={}, params={}", skillCode, newParams.keySet());
            } else {
                log.warn("无法应用优化参数，技能不存在: {}", skillCode);
            }
        } catch (Exception e) {
            log.error("应用优化参数失败: skill={}", skillCode, e);
        }
    }

    private void validatePreviousEvolutions() {
        log.info("=== 验证之前的进化结果 ===");
        
        Iterator<Map.Entry<String, EvolutionRecord>> iterator = evolutionHistory.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, EvolutionRecord> entry = iterator.next();
            EvolutionRecord record = entry.getValue();
            
            long hoursSinceEvolution = (System.currentTimeMillis() - record.evolutionTime) / 3600000;
            
            if (hoursSinceEvolution >= 24) {
                validateSingleEvolution(record);
                
                if (record.isValid()) {
                    log.info("进化验证通过: skill={}, rateBefore=%.2f%%, rateAfter=%.2f%%",
                             record.skillCode, record.successRateBefore * 100, record.getSuccessRateAfter() * 100);
                } else {
                    log.warn("进化验证失败，将回滚: skill={}, rateBefore=%.2f%%, rateAfter=%.2f%%",
                             record.skillCode, record.successRateBefore * 100, record.getSuccessRateAfter() * 100);
                    rollbackEvolution(record);
                }
                
                iterator.remove();
            }
        }
    }

    private void validateSingleEvolution(EvolutionRecord record) {
        try {
            Integer totalTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE created_at > FROM_UNIXTIME(?)",
                    Integer.class, record.evolutionTime / 1000);
            
            Integer successTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_video_task WHERE status = 'completed' AND created_at > FROM_UNIXTIME(?)",
                    Integer.class, record.evolutionTime / 1000);
            
            record.tasksAfterEvolution = totalTasks != null ? totalTasks : 0;
            record.successesAfterEvolution = successTasks != null ? successTasks : 0;

        } catch (Exception e) {
            log.error("验证进化失败: skill={}", record.skillCode, e);
        }
    }

    private void rollbackEvolution(EvolutionRecord record) {
        try {
            SkillConfig currentSkill = skillConfigService.getActiveSkill(record.skillCode);
            if (currentSkill != null) {
                Map<String, Object> oldParams = objectMapper.readValue(record.oldValue, Map.class);
                skillConfigService.upgradeSkill(currentSkill, oldParams, "回滚: EvolutionCoreService");
                log.info("进化已回滚: skill={}", record.skillCode);
            } else {
                log.warn("无法回滚，技能不存在: {}", record.skillCode);
            }
        } catch (Exception e) {
            log.error("回滚进化失败: skill={}", record.skillCode, e);
        }
    }

    private void updateEffectiveStrategy(String skillCode, EvolutionMemory memory) {
        EffectiveStrategy strategy = effectiveStrategies.computeIfAbsent(skillCode, k -> {
            EffectiveStrategy s = new EffectiveStrategy();
            s.setStrategyType(memory.getStrategyType());
            return s;
        });

        double totalImprovement = strategy.getAvgImprovement() * strategy.getSuccessCount() + 
                                (memory.getAfterMetric() - memory.getBeforeMetric());
        strategy.setSuccessCount(strategy.getSuccessCount() + 1);
        strategy.setAvgImprovement(totalImprovement / strategy.getSuccessCount());
    }

    private void storeOptimizationSuggestions(String skillCode, String suggestions) {
        try {
            jdbcTemplate.update("""
                INSERT INTO skill_optimization_suggestions 
                (skill_code, suggestions, created_at)
                VALUES (?, ?, NOW())
                ON DUPLICATE KEY UPDATE 
                suggestions = VALUES(suggestions),
                created_at = VALUES(created_at)
                """, skillCode, suggestions);
        } catch (Exception e) {
            log.error("存储优化建议失败: skill={}", skillCode, e);
        }
    }

    private void persistEpisode(LearningEpisode episode) {
        try {
            jdbcTemplate.update("""
                INSERT INTO learning_episodes 
                (episode_id, skill_code, state_hash, action_id, reward, verified, before_metric, after_metric, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, 
                episode.getEpisodeId(), episode.getSkillCode(), episode.getStateHash(),
                episode.getActionId(), episode.getReward(), episode.isVerified(),
                episode.getBeforeMetric(), episode.getAfterMetric(), episode.getTimestamp()
            );
        } catch (Exception e) {
            log.debug("持久化学习回合失败（可能表不存在）: {}", episode.getEpisodeId());
        }
    }

    private void loadQTableFromDB() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT state_action_key, q_value FROM q_table");

            for (Map<String, Object> row : rows) {
                String key = (String) row.get("state_action_key");
                Double value = (Double) row.get("q_value");
                if (key != null && value != null) {
                    qTable.put(key, value);
                }
            }

            log.info("从数据库加载Q表，共{}条记录", qTable.size());
        } catch (Exception e) {
            log.warn("加载Q表失败（可能表不存在），将使用空Q表");
        }
    }

    private void syncQTableToDB() {
        try {
            for (Map.Entry<String, Double> entry : qTable.entrySet()) {
                jdbcTemplate.update("""
                    INSERT INTO q_table (state_action_key, q_value, updated_at)
                    VALUES (?, ?, NOW())
                    ON DUPLICATE KEY UPDATE q_value = VALUES(q_value), updated_at = VALUES(updated_at)
                    """, entry.getKey(), entry.getValue());
            }

            log.debug("Q表已同步到数据库");
        } catch (Exception e) {
            log.warn("同步Q表到数据库失败（可能表不存在）");
        }
    }

    private void loadEvolutionHistory() {
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                "SELECT * FROM evolution_records WHERE validated = 0 ORDER BY evolution_time DESC LIMIT 50");

            for (Map<String, Object> record : records) {
                String key = (String) record.get("skill_code") + "_" + record.get("evolution_time");
                evolutionHistory.put(key, convertToEvolutionRecord(record));
            }

            log.info("加载进化历史，共{}条未验证记录", evolutionHistory.size());
        } catch (Exception e) {
            log.warn("加载进化历史失败（可能表不存在）");
        }
    }

    private EvolutionRecord convertToEvolutionRecord(Map<String, Object> dbRecord) {
        EvolutionRecord record = new EvolutionRecord(
            (String) dbRecord.get("skill_code"),
            (String) dbRecord.get("param_name"),
            (String) dbRecord.get("old_value"),
            (String) dbRecord.get("new_value"),
            (Double) dbRecord.get("success_rate_before")
        );

        record.evolutionTime = ((java.sql.Timestamp) dbRecord.get("evolution_time")).getTime();
        record.tasksAfterEvolution = (Integer) dbRecord.getOrDefault("tasks_after", 0);
        record.successesAfterEvolution = (Integer) dbRecord.getOrDefault("successes_after", 0);

        return record;
    }

    private String generateStateId(String skillCode) {
        return skillCode + "_" + System.currentTimeMillis();
    }

    private String computeStateHash(LearningState state) {
        return String.format("%s_%.4f_%.4f_%d",
            state.getSkillCode(),
            state.getCurrentCvr(),
            state.getCurrentSuccessRate(),
            state.getRecentTrend()
        ).hashCode() + "";
    }

    private String getCurrentStateHash(String skillCode) {
        LearningState state = learningStates.get(skillCode);
        return state != null ? state.getStateHash() : skillCode;
    }

    private Double getPreviousMetric(String skillCode) {
        LearningState state = learningStates.get(skillCode);
        return state != null ? state.getCurrentCvr() : 0.0;
    }

    private String getRandomParamChange(String skillCode) {
        String[] changes = {"increase_temperature", "decrease_temperature", 
                           "change_model", "adjust_prompt_length"};
        return changes[new Random().nextInt(changes.length)];
    }

    private String getBestKnownAction(String skillCode) {
        String currentState = getCurrentStateHash(skillCode);
        
        return qTable.entrySet().stream()
                .filter(e -> e.getKey().startsWith(currentState + "_"))
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().substring(currentState.length() + 1))
                .orElse("default_action");
    }

    private double calculateCurrentSuccessRate(String skillCode) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap("""
                SELECT 
                    COUNT(*) as total,
                    SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as successes
                FROM biz_video_task 
                WHERE skill_code = ? 
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                  AND deleted = 0
                """, skillCode);

            int total = ((Number) result.get("total")).intValue();
            int successes = result.get("successes") != null ? ((Number) result.get("successes")).intValue() : 0;

            return total > 0 ? (double) successes / total : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private int countRecentEpisodes(String skillCode, int limit) {
        return (int) episodes.stream()
                .filter(e -> e.getSkillCode().equals(skillCode))
                .count();
    }

    private int countRecentSuccesses(String skillCode, int limit) {
        return (int) episodes.stream()
                .filter(e -> e.getSkillCode().equals(skillCode) && e.getReward() > 0)
                .count();
    }

    private String getTrendDescription(int trend) {
        if (trend > 2) return "明显上升";
        if (trend > 0) return "轻微上升";
        if (trend == 0) return "平稳";
        if (trend > -2) return "轻微下降";
        return "明显下降";
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public Map<String, Double> getQTable() {
        return new HashMap<>(qTable);
    }

    public Map<String, LearningState> getLearningStates() {
        return new HashMap<>(learningStates);
    }

    public List<LearningEpisode> getRecentEpisodes(int limit) {
        return episodes.stream()
                .skip(Math.max(0, episodes.size() - limit))
                .collect(Collectors.toList());
    }

    public static class LearningState {
        private String stateId;
        private String skillCode;
        private double currentCvr;
        private double currentSuccessRate;
        private int recentTrend;
        private String stateHash;
        private LocalDateTime timestamp;

        public String getStateId() { return stateId; }
        public void setStateId(String stateId) { this.stateId = stateId; }
        public String getSkillCode() { return skillCode; }
        public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
        public double getCurrentCvr() { return currentCvr; }
        public void setCurrentCvr(double currentCvr) { this.currentCvr = currentCvr; }
        public double getCurrentSuccessRate() { return currentSuccessRate; }
        public void setCurrentSuccessRate(double currentSuccessRate) { this.currentSuccessRate = currentSuccessRate; }
        public int getRecentTrend() { return recentTrend; }
        public void setRecentTrend(int recentTrend) { this.recentTrend = recentTrend; }
        public String getStateHash() { return stateHash; }
        public void setStateHash(String stateHash) { this.stateHash = stateHash; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class LearningEpisode {
        private String episodeId;
        private String skillCode;
        private String stateHash;
        private String actionId;
        private double reward;
        private String nextStateHash;
        private boolean verified;
        private double beforeMetric;
        private double afterMetric;
        private LocalDateTime timestamp;

        public String getEpisodeId() { return episodeId; }
        public void setEpisodeId(String episodeId) { this.episodeId = episodeId; }
        public String getSkillCode() { return skillCode; }
        public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
        public String getStateHash() { return stateHash; }
        public void setStateHash(String stateHash) { this.stateHash = stateHash; }
        public String getActionId() { return actionId; }
        public void setActionId(String actionId) { this.actionId = actionId; }
        public double getReward() { return reward; }
        public void setReward(double reward) { this.reward = reward; }
        public String getNextStateHash() { return nextStateHash; }
        public void setNextStateHash(String nextStateHash) { this.nextStateHash = nextStateHash; }
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        public double getBeforeMetric() { return beforeMetric; }
        public void setBeforeMetric(double beforeMetric) { this.beforeMetric = beforeMetric; }
        public double getAfterMetric() { return afterMetric; }
        public void setAfterMetric(double afterMetric) { this.afterMetric = afterMetric; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class EvolutionMemory {
        private String strategyType;
        private Map<String, Object> params;
        private double beforeMetric;
        private double afterMetric;
        private boolean effective;
        private LocalDateTime timestamp;

        public String getStrategyType() { return strategyType; }
        public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public double getBeforeMetric() { return beforeMetric; }
        public void setBeforeMetric(double beforeMetric) { this.beforeMetric = beforeMetric; }
        public double getAfterMetric() { return afterMetric; }
        public void setAfterMetric(double afterMetric) { this.afterMetric = afterMetric; }
        public boolean isEffective() { return effective; }
        public void setEffective(boolean effective) { this.effective = effective; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class EffectiveStrategy {
        private String strategyType;
        private double avgImprovement;
        private int successCount;
        private int failCount;
        private List<Map<String, Object>> successfulParams = new ArrayList<>();

        public String getStrategyType() { return strategyType; }
        public void setStrategyType(String strategyType) { this.strategyType = strategyType; }
        public double getAvgImprovement() { return avgImprovement; }
        public void setAvgImprovement(double avgImprovement) { this.avgImprovement = avgImprovement; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public List<Map<String, Object>> getSuccessfulParams() { return successfulParams; }
        public void setSuccessfulParams(List<Map<String, Object>> successfulParams) { this.successfulParams = successfulParams; }
    }

    public static class EvolutionRecord {
        String skillCode;
        String paramName;
        String oldValue;
        String newValue;
        double successRateBefore;
        long evolutionTime;
        int tasksAfterEvolution;
        int successesAfterEvolution;

        public EvolutionRecord(String skillCode, String paramName, String oldValue, String newValue, double successRateBefore) {
            this.skillCode = skillCode;
            this.paramName = paramName;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.successRateBefore = successRateBefore;
            this.evolutionTime = System.currentTimeMillis();
            this.tasksAfterEvolution = 0;
            this.successesAfterEvolution = 0;
        }

        public double getSuccessRateAfter() {
            return tasksAfterEvolution > 0 ? (successesAfterEvolution * 1.0 / tasksAfterEvolution) : 0;
        }

        public boolean isValid() {
            return getSuccessRateAfter() >= successRateBefore;
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void assessLearningOutcomes() {
        log.info("=== 开始学习成果量化评估 ===");

        Map<String, Object> assessment = new HashMap<>();

        assessment.put("qTableLearning", assessQTableLearning());
        assessment.put("skillEvolution", assessSkillEvolution());
        assessment.put("templateLearning", assessTemplateLearning());
        assessment.put("expertLearning", assessExpertLearning());
        assessment.put("knowledgeExtraction", assessKnowledgeExtraction());
        assessment.put("overallLearningDepth", calculateOverallLearningDepth(assessment));

        persistAssessmentResults(assessment);

        log.info("=== 学习成果量化评估完成 ===");
        log.info("总体学习深度评分: {}/100", assessment.get("overallLearningDepth"));
    }

    private Map<String, Object> assessQTableLearning() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            metrics.put("qTableSize", qTable.size());

            double avgQValue = qTable.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            metrics.put("avgQValue", avgQValue);

            long highQValueCount = qTable.values().stream()
                    .filter(v -> v > 0.7)
                    .count();
            metrics.put("highQValueCount", highQValueCount);

            metrics.put("totalEpisodes", episodes.size());
            metrics.put("verifiedEpisodes", episodes.stream().filter(LearningEpisode::isVerified).count());

            double depthScore = (avgQValue * 30) + (highQValueCount * 2) + (episodes.size() * 0.1);
            metrics.put("depthScore", Math.min(100.0, depthScore));

            log.info("Q-Table学习评估: 大小={}, 平均Q值={:.3f}, 高价值Q值={}, 总回合={}, 深度评分={:.1f}/100",
                    metrics.get("qTableSize"), avgQValue, highQValueCount, episodes.size(), depthScore);

        } catch (Exception e) {
            log.error("Q-Table学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessSkillEvolution() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            metrics.put("totalEvolutions", evolutionHistory.size());
            metrics.put("effectiveStrategies", effectiveStrategies.size());
            metrics.put("evolutionMemories", evolutionMemories.values().stream()
                    .mapToInt(List::size).sum());

            double avgImprovement = effectiveStrategies.values().stream()
                    .mapToDouble(EffectiveStrategy::getAvgImprovement)
                    .average()
                    .orElse(0.0);
            metrics.put("avgImprovement", avgImprovement);

            double depthScore = Math.min(100.0, (evolutionHistory.size() * 5) + (avgImprovement * 20));
            metrics.put("depthScore", depthScore);

            log.info("技能进化评估: 总进化次数={}, 有效策略={}, 记忆数={}, 平均提升={:.2f}%, 深度评分={:.1f}/100",
                    evolutionHistory.size(), effectiveStrategies.size(),
                    evolutionMemories.values().stream().mapToInt(List::size).sum(),
                    avgImprovement * 100, depthScore);

        } catch (Exception e) {
            log.error("技能进化评估失败", e);
            metrics.put("depthScore", 0.0);
        }

        return metrics;
    }

    private Map<String, Object> assessTemplateLearning() {
        Map<String, Object> metrics = new HashMap<>();
        try {
            Integer templateCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM prompt_templates WHERE is_active = 1", Integer.class);
            metrics.put("activeTemplates", templateCount != null ? templateCount : 0);

            Integer optimizedTemplates = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM prompt_templates WHERE last_optimized_at IS NOT NULL", Integer.class);
            metrics.put("optimizedTemplates", optimizedTemplates != null ? optimizedTemplates : 0);

            double depthScore = ((templateCount != null ? templateCount : 0) * 10) +
                              ((optimizedTemplates != null ? optimizedTemplates : 0) * 15);
            metrics.put("depthScore", Math.min(100.0, depthScore));

        } catch (Exception e) {
            log.error("模板学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }
        return metrics;
    }

    private Map<String, Object> assessExpertLearning() {
        Map<String, Object> metrics = new HashMap<>();
        try {
            Integer expertRoles = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM expert_role_config WHERE is_active = 1", Integer.class);
            metrics.put("activeExpertRoles", expertRoles != null ? expertRoles : 0);

            Integer appliedCases = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT expert_role) FROM case_memory WHERE status = 'APPLIED'", Integer.class);
            metrics.put("appliedCases", appliedCases != null ? appliedCases : 0);

            double depthScore = ((expertRoles != null ? expertRoles : 0) * 15) +
                              ((appliedCases != null ? appliedCases : 0) * 2);
            metrics.put("depthScore", Math.min(100.0, depthScore));

        } catch (Exception e) {
            log.error("专家学习评估失败", e);
            metrics.put("depthScore", 0.0);
        }
        return metrics;
    }

    private Map<String, Object> assessKnowledgeExtraction() {
        Map<String, Object> metrics = new HashMap<>();
        try {
            Integer knowledgeItems = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_base WHERE is_valid = 1", Integer.class);
            metrics.put("knowledgeItems", knowledgeItems != null ? knowledgeItems : 0);

            Integer extractedToday = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_base WHERE DATE(created_at) = CURDATE()", Integer.class);
            metrics.put("extractedToday", extractedToday != null ? extractedToday : 0);

            double depthScore = ((knowledgeItems != null ? knowledgeItems : 0) * 2) +
                              ((extractedToday != null ? extractedToday : 0) * 10);
            metrics.put("depthScore", Math.min(100.0, depthScore));

        } catch (Exception e) {
            log.error("知识提取评估失败", e);
            metrics.put("depthScore", 0.0);
        }
        return metrics;
    }

    private double calculateOverallLearningDepth(Map<String, Object> assessment) {
        double qTableScore = ((Map<String, Object>) assessment.get("qTableLearning")).containsKey("depthScore") ?
                (double) ((Map<String, Object>) assessment.get("qTableLearning")).get("depthScore") : 0.0;
        double evolutionScore = ((Map<String, Object>) assessment.get("skillEvolution")).containsKey("depthScore") ?
                (double) ((Map<String, Object>) assessment.get("skillEvolution")).get("depthScore") : 0.0;
        double templateScore = ((Map<String, Object>) assessment.get("templateLearning")).containsKey("depthScore") ?
                (double) ((Map<String, Object>) assessment.get("templateLearning")).get("depthScore") : 0.0;
        double expertScore = ((Map<String, Object>) assessment.get("expertLearning")).containsKey("depthScore") ?
                (double) ((Map<String, Object>) assessment.get("expertLearning")).get("depthScore") : 0.0;
        double knowledgeScore = ((Map<String, Object>) assessment.get("knowledgeExtraction")).containsKey("depthScore") ?
                (double) ((Map<String, Object>) assessment.get("knowledgeExtraction")).get("depthScore") : 0.0;

        return (qTableScore * 0.25 + evolutionScore * 0.25 + templateScore * 0.2 +
                expertScore * 0.15 + knowledgeScore * 0.15);
    }

    private void persistAssessmentResults(Map<String, Object> assessment) {
        try {
            String assessmentJson = objectMapper.writeValueAsString(assessment);
            jdbcTemplate.update("""
                INSERT INTO learning_assessment_results 
                (assessment_data, assessment_time)
                VALUES (?, NOW())
                """, assessmentJson);
        } catch (Exception e) {
            log.warn("持久化评估结果失败", e);
        }
    }

    public Long recordSkillUsage(String skillCode, String skillName, String intentType,
                                String sessionId, Long userId, boolean success,
                                long executionTimeMs, Map<String, Object> inputParams,
                                Map<String, Object> outputData, String errorMessage) {
        try {
            jdbcTemplate.update("""
                INSERT INTO skill_usage_data 
                (skill_code, skill_name, intent_type, session_id, user_id, success,
                 execution_time_ms, input_params, output_data, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, skillCode, skillName, intentType, sessionId, userId, success,
                executionTimeMs,
                inputParams != null ? objectMapper.writeValueAsString(inputParams) : "{}",
                outputData != null ? objectMapper.writeValueAsString(outputData) : "{}",
                errorMessage);

            Long usageId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            recordUsage(skillCode, success, null, null);

            log.debug("记录技能使用情况: skillCode={}, success={}, time={}ms, usageId={}",
                     skillCode, success, executionTimeMs, usageId);

            return usageId;

        } catch (Exception e) {
            log.error("记录技能使用情况失败: skill={}", skillCode, e);
            return null;
        }
    }

    public UsageStats getSkillUsageStats(String skillCode) {
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap("""
                SELECT 
                    COUNT(*) as totalUsage,
                    SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as successCount,
                    AVG(execution_time_ms) as avgExecutionTime,
                    SUM(CASE WHEN error_message IS NOT NULL THEN 1 ELSE 0 END) as errorCount,
                    MAX(created_at) as lastUsedAt
                FROM skill_usage_data 
                WHERE skill_code = ?
                  AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                """, skillCode);

            UsageStats stats = new UsageStats();
            stats.setTotalUsage(((Number) result.get("totalUsage")).intValue());
            stats.setSuccessCount(result.get("successCount") != null ? ((Number) result.get("successCount")).intValue() : 0);
            stats.setAvgExecutionTime(result.get("avgExecutionTime") != null ? ((Number) result.get("avgExecutionTime")).longValue() : 0L);
            stats.setErrorCount(result.get("errorCount") != null ? ((Number) result.get("errorCount")).intValue() : 0);
            stats.setSuccessRate(stats.getTotalUsage() > 0 ? (double) stats.getSuccessCount() / stats.getTotalUsage() : 0.0);
            stats.setLastUsedAt(result.get("lastUsedAt") != null ? result.get("lastUsedAt").toString() : "");

            return stats;

        } catch (Exception e) {
            log.error("获取技能使用统计失败: skill={}", skillCode, e);
            return new UsageStats();
        }
    }

    public Map<String, Object> autoOptimizeSkill(String skillCode) {
        Map<String, Object> result = new HashMap<>();
        result.put("skillCode", skillCode);

        try {
            SkillConfig skill = skillConfigService.getActiveSkill(skillCode);
            if (skill == null) {
                result.put("success", false);
                result.put("error", "Skill不存在");
                return result;
            }

            UsageStats stats = getSkillUsageStats(skillCode);
            if (stats.getTotalUsage() < 10) {
                result.put("success", false);
                result.put("error", "样本不足，需要至少10次使用记录");
                return result;
            }

            Map<String, Object> currentParams = skill.getParamsJson();
            Map<String, Object> optimizedParams = generateStatisticalOptimization(skillCode, currentParams, stats);

            if (optimizedParams != null && !optimizedParams.equals(currentParams)) {
                result.put("beforeParams", currentParams);
                result.put("afterParams", optimizedParams);
                result.put("confidence", calculateOptimizationConfidence(stats));

                boolean shouldApply = stats.getSuccessRate() < 0.8 || stats.getAvgExecutionTime() > 5000;

                if (shouldApply) {
                    applyOptimizedParams(skillCode, optimizedParams);
                    result.put("applied", true);
                    result.put("reason", "统计数据显示需要优化");

                    logOptimizationResult("AUTO_OPTIMIZE", skillCode, currentParams, optimizedParams, stats);
                } else {
                    result.put("applied", false);
                    result.put("reason", "当前性能良好，暂不优化");
                }
            } else {
                result.put("success", true);
                result.put("message", "无需优化，当前参数已经是最优的");
            }

        } catch (Exception e) {
            log.error("自动优化技能失败: skill={}", skillCode, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> generateStatisticalOptimization(String skillCode, Map<String, Object> currentParams, UsageStats stats) {
        Map<String, Object> optimizedParams = new HashMap<>(currentParams);

        Random random = new Random();

        if (stats.getSuccessRate() < 0.7) {
            for (String key : currentParams.keySet()) {
                if (key.toLowerCase().contains("temperature") && currentParams.get(key) instanceof Number) {
                    double temp = ((Number) currentParams.get(key)).doubleValue();
                    optimizedParams.put(key, Math.max(0.1, Math.min(2.0, temp * 0.9)));
                }
            }
        }

        if (stats.getAvgExecutionTime() > 10000) {
            for (String key : currentParams.keySet()) {
                if (key.toLowerCase().contains("max_tokens") && currentParams.get(key) instanceof Number) {
                    int tokens = ((Number) currentParams.get(key)).intValue();
                    optimizedParams.put(key, Math.max(100, (int)(tokens * 0.8)));
                }
            }
        }

        if (random.nextDouble() < 0.3) {
            for (String key : new ArrayList<>(currentParams.keySet())) {
                if (random.nextDouble() < 0.2) {
                    Object value = currentParams.get(key);
                    if (value instanceof Number) {
                        double numValue = ((Number) value).doubleValue();
                        double change = (random.nextDouble() - 0.5) * 0.1 * numValue;
                        optimizedParams.put(key, numValue + change);
                    }
                }
            }
        }

        return optimizedParams;
    }

    private double calculateOptimizationConfidence(UsageStats stats) {
        double confidence = 0.0;

        confidence += Math.min(0.3, stats.getTotalUsage() * 0.01);
        confidence += stats.getSuccessRate() * 0.3;

        if (stats.getErrorCount() == 0) {
            confidence += 0.2;
        } else {
            confidence -= Math.min(0.2, stats.getErrorCount() * 0.02);
        }

        if (stats.getAvgExecutionTime() < 3000) {
            confidence += 0.2;
        } else if (stats.getAvgExecutionTime() > 10000) {
            confidence -= 0.1;
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private void logOptimizationResult(String optimizationType, String skillCode,
                                      Map<String, Object> beforeParams, Map<String, Object> afterParams,
                                      UsageStats stats) {
        try {
            jdbcTemplate.update("""
                INSERT INTO optimization_logs 
                (optimization_type, skill_code, before_params, after_params, 
                 success_rate_before, execution_time_before, confidence, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                """, optimizationType, skillCode,
                objectMapper.writeValueAsString(beforeParams),
                objectMapper.writeValueAsString(afterParams),
                stats.getSuccessRate(),
                stats.getAvgExecutionTime(),
                calculateOptimizationConfidence(stats));

            log.info("优化已应用: type={}, skill={}, confidence={:.2f}",
                     optimizationType, skillCode, calculateOptimizationConfidence(stats));

        } catch (Exception e) {
            log.warn("记录优化日志失败", e);
        }
    }

    public static class UsageStats {
        private int totalUsage;
        private int successCount;
        private long avgExecutionTime;
        private int errorCount;
        private double successRate;
        private String lastUsedAt;

        public int getTotalUsage() { return totalUsage; }
        public void setTotalUsage(int totalUsage) { this.totalUsage = totalUsage; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public long getAvgExecutionTime() { return avgExecutionTime; }
        public void setAvgExecutionTime(long avgExecutionTime) { this.avgExecutionTime = avgExecutionTime; }
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public String getLastUsedAt() { return lastUsedAt; }
        public void setLastUsedAt(String lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    }
}