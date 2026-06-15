package com.ecommerce.workflow.agent.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ecommerce.workflow.agent.AgentRequest;
import com.ecommerce.workflow.agent.AgentResponse;
import com.ecommerce.workflow.service.config.SysConfigService;

import jakarta.annotation.PostConstruct;

@Component
public class ComplianceControlAgent {

    private static final Logger log = LoggerFactory.getLogger(ComplianceControlAgent.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    private SysConfigService sysConfigService;

    private List<ComplianceRule> cachedRules = new ArrayList<>();
    private long lastRuleRefreshTime = 0;

    public ComplianceControlAgent(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        initTables();
        loadDefaultRules();
        refreshRules();
        log.info("安全层Guard初始化完成: 规则数={}", cachedRules.size());
    }

    private void initTables() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_compliance_rule (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    rule_code VARCHAR(50) NOT NULL,
                    rule_name VARCHAR(200) NOT NULL,
                    category VARCHAR(50) NOT NULL,
                    rule_type VARCHAR(20) DEFAULT 'KEYWORD',
                    pattern TEXT,
                    replacement VARCHAR(200),
                    severity VARCHAR(20) DEFAULT 'WARNING',
                    platform VARCHAR(50) DEFAULT 'ALL',
                    enabled INT DEFAULT 1,
                    auto_fix INT DEFAULT 1,
                    description VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE INDEX uk_rule_code (rule_code),
                    INDEX idx_category (category),
                    INDEX idx_platform (platform)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_compliance_rule table may already exist");
        }

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sys_compliance_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    audit_id VARCHAR(64) NOT NULL,
                    content_type VARCHAR(30),
                    content_preview TEXT,
                    rule_code VARCHAR(50),
                    rule_name VARCHAR(200),
                    severity VARCHAR(20),
                    passed INT DEFAULT 1,
                    violation_detail TEXT,
                    auto_fixed INT DEFAULT 0,
                    fixed_content TEXT,
                    platform VARCHAR(50),
                    operator VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_rule_code (rule_code),
                    INDEX idx_passed (passed),
                    INDEX idx_created (created_at)
                )
                """);
        } catch (Exception e) {
            log.debug("sys_compliance_audit_log table may already exist");
        }
    }

    private void loadDefaultRules() {
        String[][] defaultRules = {
                {"PROHIBITED_ABSOLUTE", "禁止使用绝对化用语", "KEYWORD", "绝对", "非常", "HIGH", "ALL"},
                {"PROHIBITED_BEST", "禁止使用最高级用语", "KEYWORD", "最好", "优质", "HIGH", "ALL"},
                {"PROHIBITED_FIRST", "禁止使用第一用语", "KEYWORD", "第一", "领先", "HIGH", "ALL"},
                {"PROHIBITED_TOP", "禁止使用顶级用语", "KEYWORD", "顶级", "高端", "MEDIUM", "ALL"},
                {"PROHIBITED_PREMIUM", "禁止使用极品用语", "KEYWORD", "极品", "精品", "MEDIUM", "ALL"},
                {"PROHIBITED_EXCLUSIVE", "禁止使用独家用语", "KEYWORD", "独家", "特色", "MEDIUM", "ALL"},
                {"PROHIBITED_GUARANTEE", "禁止使用保证用语", "KEYWORD", "保证", "", "HIGH", "ALL"},
                {"PROHIBITED_CURE", "禁止使用治疗用语", "KEYWORD", "治愈,治疗,疗效", "", "CRITICAL", "ALL"},
                {"DOUYIN_NO_MEDICAL", "抖音禁止医疗用语", "KEYWORD", "处方药,医疗器械", "", "CRITICAL", "douyin"},
                {"KUAISHOU_NO_EXAGGERATE", "快手禁止夸大用语", "KEYWORD", "100%,零风险,包治", "", "HIGH", "kuaishou"},
                {"LENGTH_LIMIT_SCRIPT", "脚本长度限制", "LENGTH", "script_max:5000", "", "LOW", "ALL"},
                {"LENGTH_LIMIT_TITLE", "标题长度限制", "LENGTH", "title_max:50", "", "LOW", "ALL"},
        };

        for (String[] rule : defaultRules) {
            try {
                jdbcTemplate.update("""
                    INSERT INTO sys_compliance_rule (rule_code, rule_name, category, rule_type, pattern, replacement, severity, platform, enabled, auto_fix)
                    VALUES (?, ?, 'prohibited', ?, ?, ?, ?, ?, 1, 1)
                    ON DUPLICATE KEY UPDATE rule_name=?, pattern=?, replacement=?, severity=?
                    """, rule[0], rule[1], rule[2], rule[3], rule[4], rule[5], rule[6],
                        rule[1], rule[3], rule[4], rule[5]);
            } catch (Exception e) {
                log.debug("加载默认合规规则失败: {}", rule[0], e);
            }
        }
    }

    private void refreshRules() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM sys_compliance_rule WHERE enabled = 1");
            List<ComplianceRule> rules = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                ComplianceRule rule = new ComplianceRule();
                rule.setId(((Number) row.get("id")).longValue());
                rule.setRuleCode((String) row.get("rule_code"));
                rule.setRuleName((String) row.get("rule_name"));
                rule.setCategory((String) row.get("category"));
                rule.setRuleType((String) row.get("rule_type"));
                rule.setPattern((String) row.get("pattern"));
                rule.setReplacement((String) row.get("replacement"));
                rule.setSeverity((String) row.get("severity"));
                rule.setPlatform((String) row.get("platform"));
                rule.setAutoFix(row.get("auto_fix") != null && ((Number) row.get("auto_fix")).intValue() == 1);
                rules.add(rule);
            }
            cachedRules = rules;
            lastRuleRefreshTime = System.currentTimeMillis();
            log.debug("合规规则刷新: {} 条", rules.size());
        } catch (Exception e) {
            log.warn("刷新合规规则失败", e);
        }
    }

    private List<ComplianceRule> getActiveRules() {
        long ruleCacheTtlMs = sysConfigService.getLongConfig("rule_refresh_interval_ms", 300000);
        if (System.currentTimeMillis() - lastRuleRefreshTime > ruleCacheTtlMs) {
            refreshRules();
        }
        return cachedRules;
    }

    public AgentResponse process(AgentRequest request) {
        String action = "check_compliance";
        if (request.getParameters() != null && request.getParameters().get("action") != null) {
            action = request.getParameters().get("action").toString();
        }
        log.info("处理合规检查请求: action={}", action);

        String content = request.getMessage();
        String platform = "ALL";
        if (request.getParameters() != null && request.getParameters().get("platform") != null) {
            platform = request.getParameters().get("platform").toString();
        }

        Map<String, Object> data = new HashMap<>();

        if ("check_compliance".equals(action)) {
            ComplianceCheckResult result = checkCompliance(content, platform);
            data.put("compliancePassed", result.isPassed());
            data.put("platform", platform);
            data.put("violations", result.getViolations());
            data.put("severity", result.getMaxSeverity());

            if (!result.isPassed()) {
                data.put("reviewReason", result.getSummary());
                AgentResponse response = new AgentResponse();
                response.setSuccess(false);
                response.setRequiresHumanReview(result.hasCriticalViolation());
                response.setReviewReason(result.getSummary());
                response.setData(data);
                return response;
            }

            AgentResponse response = new AgentResponse();
            response.setSuccess(true);
            response.setRequiresHumanReview(false);
            response.setData(data);
            return response;

        } else if ("fix_violations".equals(action)) {
            ComplianceFixResult fixResult = fixViolations(content, platform);
            data.put("fixedContent", fixResult.getFixedContent());
            data.put("originalContent", content);
            data.put("fixesApplied", fixResult.getFixesApplied());
            data.put("remainingViolations", fixResult.getRemainingViolations());

            AgentResponse response = new AgentResponse();
            response.setSuccess(true);
            response.setRequiresHumanReview(!fixResult.getRemainingViolations().isEmpty());
            response.setData(data);
            return response;
        }

        AgentResponse response = new AgentResponse();
        response.setSuccess(false);
        response.setRequiresHumanReview(true);
        response.setReviewReason("未知操作: " + action);
        return response;
    }

    public ComplianceCheckResult checkCompliance(String content, String platform) {
        if (content == null || content.isEmpty()) {
            return new ComplianceCheckResult(true, List.of(), "none");
        }

        List<ComplianceRule> activeRules = getActiveRules();
        List<Violation> violations = new ArrayList<>();

        for (ComplianceRule rule : activeRules) {
            if (!matchesPlatform(rule.getPlatform(), platform)) {
                continue;
            }

            if ("KEYWORD".equals(rule.getRuleType())) {
                checkKeywordViolation(content, rule, violations);
            } else if ("LENGTH".equals(rule.getRuleType())) {
                checkLengthViolation(content, rule, violations);
            } else if ("REGEX".equals(rule.getRuleType())) {
                checkRegexViolation(content, rule, violations);
            }
        }

        String maxSeverity = violations.isEmpty() ? "none" :
                violations.stream().map(Violation::getSeverity)
                        .max(Comparator.comparingInt(this::severityOrder))
                        .orElse("none");

        ComplianceCheckResult result = new ComplianceCheckResult(violations.isEmpty(), violations, maxSeverity);

        logAudit(content, platform, violations, false, null);

        return result;
    }

    public ComplianceFixResult fixViolations(String content, String platform) {
        if (content == null) {
            return new ComplianceFixResult("", List.of(), List.of());
        }

        String fixed = content;
        List<Map<String, Object>> fixesApplied = new ArrayList<>();
        List<Violation> remaining = new ArrayList<>();

        List<ComplianceRule> activeRules = getActiveRules();

        for (ComplianceRule rule : activeRules) {
            if (!matchesPlatform(rule.getPlatform(), platform)) {
                continue;
            }
            if (!rule.isAutoFix()) {
                continue;
            }

            if ("KEYWORD".equals(rule.getRuleType()) && rule.getPattern() != null && rule.getReplacement() != null) {
                String[] keywords = rule.getPattern().split(",");
                for (String keyword : keywords) {
                    String kw = keyword.trim();
                    if (fixed.contains(kw)) {
                        fixed = fixed.replace(kw, rule.getReplacement());
                        fixesApplied.add(Map.of("rule", rule.getRuleCode(), "keyword", kw,
                                "replacement", rule.getReplacement()));
                    }
                }
            }
        }

        ComplianceCheckResult recheck = checkCompliance(fixed, platform);
        remaining = recheck.getViolations();

        logAudit(content, platform, recheck.getViolations(), true, fixed);

        return new ComplianceFixResult(fixed, fixesApplied, remaining);
    }

    private void checkKeywordViolation(String content, ComplianceRule rule, List<Violation> violations) {
        if (rule.getPattern() == null) return;
        String[] keywords = rule.getPattern().split(",");
        for (String keyword : keywords) {
            String kw = keyword.trim();
            if (!kw.isEmpty() && content.contains(kw)) {
                violations.add(new Violation(rule.getRuleCode(), rule.getRuleName(), kw,
                        rule.getSeverity(), rule.getCategory()));
            }
        }
    }

    private void checkLengthViolation(String content, ComplianceRule rule, List<Violation> violations) {
        if (rule.getPattern() == null) return;
        try {
            String[] parts = rule.getPattern().split(":");
            if (parts.length == 2) {
                int maxLength = Integer.parseInt(parts[1].trim());
                if (content.length() > maxLength) {
                    violations.add(new Violation(rule.getRuleCode(), rule.getRuleName(),
                            "内容长度" + content.length() + "超过限制" + maxLength,
                            rule.getSeverity(), rule.getCategory()));
                }
            }
        } catch (Exception e) {
            log.debug("长度规则解析失败: {}", rule.getPattern());
        }
    }

    private void checkRegexViolation(String content, ComplianceRule rule, List<Violation> violations) {
        if (rule.getPattern() == null) return;
        try {
            if (content.matches(".*" + rule.getPattern() + ".*")) {
                violations.add(new Violation(rule.getRuleCode(), rule.getRuleName(),
                        "匹配规则: " + rule.getPattern(), rule.getSeverity(), rule.getCategory()));
            }
        } catch (Exception e) {
            log.debug("正则规则匹配失败: {}", rule.getPattern());
        }
    }

    private boolean matchesPlatform(String rulePlatform, String targetPlatform) {
        if ("ALL".equals(rulePlatform)) return true;
        if (targetPlatform == null) return true;
        return rulePlatform.equalsIgnoreCase(targetPlatform);
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private void logAudit(String content, String platform, List<Violation> violations,
                          boolean autoFixed, String fixedContent) {
        if (!sysConfigService.getBooleanConfig("audit_log_enabled", true)) {
            return;
        }
        try {
            String auditId = "AUDIT_" + System.currentTimeMillis();
            if (violations.isEmpty()) {
                jdbcTemplate.update("""
                    INSERT INTO sys_compliance_audit_log
                    (audit_id, content_type, content_preview, passed, platform, created_at)
                    VALUES (?, 'text', ?, 1, ?, NOW())
                    """, auditId, content.substring(0, Math.min(content.length(), 200)), platform);
            } else {
                for (Violation v : violations) {
                    jdbcTemplate.update("""
                        INSERT INTO sys_compliance_audit_log
                        (audit_id, content_type, content_preview, rule_code, rule_name, severity,
                         passed, violation_detail, auto_fixed, fixed_content, platform, created_at)
                        VALUES (?, 'text', ?, ?, ?, ?, 0, ?, ?, ?, ?, NOW())
                        """, auditId, content.substring(0, Math.min(content.length(), 200)),
                            v.getRuleCode(), v.getRuleName(), v.getSeverity(),
                            v.getDetail(), autoFixed ? 1 : 0,
                            fixedContent != null ? fixedContent.substring(0, Math.min(fixedContent.length(), 200)) : null,
                            platform);
                }
            }
        } catch (Exception e) {
            log.debug("记录合规审计日志失败", e);
        }
    }

    public List<ComplianceRule> getRules() {
        return getActiveRules();
    }

    public void addRule(String ruleCode, String ruleName, String category, String ruleType,
                        String pattern, String replacement, String severity, String platform) {
        try {
            jdbcTemplate.update("""
                INSERT INTO sys_compliance_rule (rule_code, rule_name, category, rule_type, pattern, replacement, severity, platform, enabled, auto_fix)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1)
                ON DUPLICATE KEY UPDATE rule_name=?, pattern=?, replacement=?, severity=?, platform=?
                """, ruleCode, ruleName, category, ruleType, pattern, replacement, severity, platform,
                    ruleName, pattern, replacement, severity, platform);
            refreshRules();
            log.info("添加合规规则: {}", ruleCode);
        } catch (Exception e) {
            log.error("添加合规规则失败: {}", ruleCode, e);
        }
    }

    public void removeRule(String ruleCode) {
        try {
            jdbcTemplate.update("UPDATE sys_compliance_rule SET enabled = 0 WHERE rule_code = ?", ruleCode);
            refreshRules();
        } catch (Exception e) {
            log.error("移除合规规则失败: {}", ruleCode, e);
        }
    }

    public static class ComplianceRule {
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String category;
        private String ruleType;
        private String pattern;
        private String replacement;
        private String severity;
        private String platform;
        private boolean autoFix;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRuleCode() { return ruleCode; }
        public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getRuleType() { return ruleType; }
        public void setRuleType(String ruleType) { this.ruleType = ruleType; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }
        public boolean isAutoFix() { return autoFix; }
        public void setAutoFix(boolean autoFix) { this.autoFix = autoFix; }
    }

    public static class Violation {
        private final String ruleCode;
        private final String ruleName;
        private final String detail;
        private final String severity;
        private final String category;

        public Violation(String ruleCode, String ruleName, String detail, String severity, String category) {
            this.ruleCode = ruleCode;
            this.ruleName = ruleName;
            this.detail = detail;
            this.severity = severity;
            this.category = category;
        }

        public String getRuleCode() { return ruleCode; }
        public String getRuleName() { return ruleName; }
        public String getDetail() { return detail; }
        public String getSeverity() { return severity; }
        public String getCategory() { return category; }
    }

    public static class ComplianceCheckResult {
        private final boolean passed;
        private final List<Violation> violations;
        private final String maxSeverity;

        public ComplianceCheckResult(boolean passed, List<Violation> violations, String maxSeverity) {
            this.passed = passed;
            this.violations = violations;
            this.maxSeverity = maxSeverity;
        }

        public boolean isPassed() { return passed; }
        public List<Violation> getViolations() { return violations; }
        public String getMaxSeverity() { return maxSeverity; }
        public boolean hasCriticalViolation() {
            return violations.stream().anyMatch(v -> "CRITICAL".equals(v.getSeverity()));
        }
        public String getSummary() {
            return violations.stream().map(Violation::getDetail).collect(Collectors.joining("; "));
        }
    }

    public static class ComplianceFixResult {
        private final String fixedContent;
        private final List<Map<String, Object>> fixesApplied;
        private final List<Violation> remainingViolations;

        public ComplianceFixResult(String fixedContent, List<Map<String, Object>> fixesApplied,
                                   List<Violation> remainingViolations) {
            this.fixedContent = fixedContent;
            this.fixesApplied = fixesApplied;
            this.remainingViolations = remainingViolations;
        }

        public String getFixedContent() { return fixedContent; }
        public List<Map<String, Object>> getFixesApplied() { return fixesApplied; }
        public List<Violation> getRemainingViolations() { return remainingViolations; }
    }
}
