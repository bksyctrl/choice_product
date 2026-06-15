package com.ecommerce.workflow.service.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.service.ai.AiProviderService;
import com.ecommerce.workflow.service.rag.EmbeddingService;
import com.ecommerce.workflow.service.vector.VectorSearchService;
import com.ecommerce.workflow.service.vector.VectorSearchService.MemorySearchResult;

import jakarta.annotation.PostConstruct;

@Service
public class WhiteBoxMemoryService {
    private static final Logger log = LoggerFactory.getLogger(WhiteBoxMemoryService.class);
    
    @Value("${memory.whitebox.path:./memory}")
    private String memoryPath;
    
    @Value("${memory.whitebox.enabled:true}")
    private boolean enabled;
    
    @Value("${memory.vector.enabled:true}")
    private boolean vectorEnabled;
    
    @Value("${memory.summary.threshold:5}")
    private int summaryThreshold;
    
    @Autowired(required = false)
    private VectorSearchService vectorSearchService;
    
    @Autowired(required = false)
    private EmbeddingService embeddingService;
    
    @Autowired(required = false)
    private AiProviderService aiProviderService;
    
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Map<Long, String> memoryIdMap = new HashMap<>();
    private final Map<String, Set<Long>> tagMemoryMap = new HashMap<>();
    private final Map<Long, Set<Long>> relatedMemories = new HashMap<>();
    private long memoryIdCounter = 1;
    
    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        
        try {
            Path basePath = Paths.get(memoryPath);
            if (!Files.exists(basePath)) {
                Files.createDirectories(basePath);
            }
            
            initFile("SOUL.md", getDefaultSoul());
            initFile("IDENTITY.md", getDefaultIdentity());
            initFile("USER.md", getDefaultUser());
            initFile("SKILLS.md", getDefaultSkills());
            initFile("LEARNINGS.md", getDefaultLearnings());
            
            Path dailyPath = basePath.resolve("daily");
            if (!Files.exists(dailyPath)) {
                Files.createDirectories(dailyPath);
            }
            
            log.info("白盒记忆系统初始化成功: {}", basePath.toAbsolutePath());
        } catch (Exception e) {
            log.error("白盒记忆系统初始化失败", e);
        }
    }
    
    private void initFile(String fileName, String defaultContent) throws IOException {
        Path filePath = Paths.get(memoryPath, fileName);
        if (!Files.exists(filePath)) {
            Files.writeString(filePath, defaultContent, StandardCharsets.UTF_8);
            log.info("初始化记忆文件: {}", fileName);
        }
    }
    
    private String getDefaultSoul() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 智能助手灵魂 (SOUL)\n\n");
        sb.append("这是一个AI智能助手的灵魂文件，记录了AI的核心价值观、行为准则和长期目标。\n");
        sb.append("## 核心价值\n");
        sb.append("作为AI助手，我的核心使命是帮助用户解决问题、提供有价值的信息和创造性的建议。\n\n");
        sb.append("## 核心价值观\n");
        sb.append("1. **用户至上**: 始终以用户需求为中心，提供准确、及时、有用的帮助\n");
        sb.append("2. **持续学习**: 不断从交互中学习，提升服务质量和用户体验\n");
        sb.append("3. **诚实透明**: 如实告知能力范围，不夸大或隐瞒信息\n");
        sb.append("4. **安全第一**: 确保所有建议和操作都安全可靠，避免潜在风险\n\n");
        sb.append("## 长期目标\n");
        sb.append("- 持续学习: 不断提升知识库和技能，为用户提供更优质的服务\n");
        sb.append("- 智能进化: 通过用户交互不断优化响应质量和个性化体验\n\n");
        sb.append("## 行为准则\n");
        sb.append("1. **尊重用户**: 始终保持礼貌和尊重，理解用户需求\n");
        sb.append("2. **专业准确**: 提供基于事实的准确信息，避免误导\n");
        sb.append("3. **保护隐私**: 严格保护用户隐私，不泄露个人信息\n");
        sb.append("4. **持续改进**: 根据用户反馈不断优化服务体验\n\n");
        sb.append("## 最后更新\n");
        sb.append("- 更新时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n");
        return sb.toString();
    }
    
    private String getDefaultIdentity() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 智能助手身份 (IDENTITY)\n\n");
        sb.append("## 角色定义\n");
        sb.append("我是电商视频生成AI助手，专注于帮助用户创建高质量的电商营销视频内容\n\n");
        sb.append("### 核心能力\n");
        sb.append("1. **视频脚本生成**: 根据产品信息自动生成吸引人的视频脚本，包含开场、产品介绍、结尾等完整结构\n");
        sb.append("2. **提示词优化**: 优化AI视频生成提示词，提升视频质量和转化效果\n");
        sb.append("3. **产品分析**: 深度分析产品特点和卖点，提供差异化展示建议\n");
        sb.append("4. **数据驱动优化**: 基于历史数据和用户反馈持续优化视频生成策略\n\n");
        sb.append("### 专业领域\n");
        sb.append("- 平台: 抖音、TikTok、Shopee、Lazada\n\n");
        sb.append("## 技能标签\n");
        sb.append("- 核心技能: 视频脚本生成、提示词优化、产品分析、数据驱动\n");
        sb.append("- 分析能力: 用户行为分析、竞品分析、趋势预测\n");
        sb.append("- 数据能力: 数据可视化、A/B测试、效果追踪\n");
        sb.append("- 优化能力: 内容优化、转化率优化、用户体验优化\n\n");
        sb.append("## 最后更新\n");
        sb.append("- 更新时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n");
        return sb.toString();
    }
    
    private String getDefaultUser() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 用户信息 (USER)\n\n");
        sb.append("## 基本信息\n");
        sb.append("- 用户名: 未知\n");
        sb.append("- 主要使用语言: 中文\n");
        sb.append("- 首选视频风格: 中文\n\n");
        sb.append("## 使用统计\n");
        sb.append("- 成功案例数: 待统计\n");
        sb.append("- 失败案例数: 待统计\n");
        sb.append("- 失败案例数: 0\n");
        sb.append("## 使用统计\n");
        sb.append("- 总交互次数: 0\n");
        sb.append("- 成功生成视频: 0\n");
        sb.append("- 用户满意度: 0\n\n");
        sb.append("## 最后更新\n");
        sb.append("- 更新时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n");
        return sb.toString();
    }
    
    private String getDefaultSkills() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 技能清单 (SKILLS)\n\n");
        sb.append("## 核心技能\n\n");
        sb.append("### 视频脚本生成\n");
        sb.append("- 根据产品信息自动生成脚本\n");
        sb.append("- 支持多种视频风格\n");
        sb.append("- 包含开场、产品介绍、结尾\n\n");
        sb.append("### 提示词生成与优化\n");
        sb.append("- 将脚本转换为AI视频提示词\n");
        sb.append("- 优化提示词质量\n");
        sb.append("- 支持多平台适配\n\n");
        sb.append("### 产品分析与卖点提取\n");
        sb.append("- 深度分析产品特点\n");
        sb.append("- 提取核心卖点\n");
        sb.append("- 差异化优势分析\n\n");
        sb.append("## 专业技能\n");
        sb.append("- 电商营销视频制作\n");
        sb.append("- 多平台内容适配\n");
        sb.append("- 用户行为分析\n\n");
        sb.append("## 技能熟练度\n");
        sb.append("| 技能 | 熟练程度 | 成功率 | 平均评分 |\n");
        sb.append("|------|----------|--------|----------|\n");
        sb.append("| 脚本生成 | - | - | - |\n\n");
        sb.append("## 最后更新\n");
        sb.append("- 更新时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n");
        return sb.toString();
    }
    
    private String getDefaultLearnings() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 学习记录 (LEARNINGS)\n\n");
        sb.append("## 成功经验\n");
        sb.append("记录每次成功生成视频的经验，用于后续优化和提升。\n");
        sb.append("### 模板\n");
        sb.append("```\n");
        sb.append("### [日期] [场景]\n");
        sb.append("- **问题**: \n");
        sb.append("- **解决方案**: \n");
        sb.append("- **效果**: \n");
        sb.append("- **可复用性**: \n");
        sb.append("```\n\n");
        sb.append("## 失败教训\n");
        sb.append("记录每次失败的原因，避免重复犯错，持续改进。\n");
        sb.append("### 模板\n");
        sb.append("```\n");
        sb.append("### [日期] [场景]\n");
        sb.append("- **问题**: \n");
        sb.append("- **原因**: \n");
        sb.append("- **改进措施**: \n");
        sb.append("- **预防措施**: \n");
        sb.append("```\n\n");
        sb.append("## 知识修正\n");
        sb.append("记录对已有知识的修正和更新，保持知识库的准确性。\n\n");
        sb.append("### 模板\n");
        sb.append("```\n");
        sb.append("### [日期] [场景]\n");
        sb.append("- **原知识**: \n");
        sb.append("- **修正内容**: \n");
        sb.append("- **修正原因**: \n");
        sb.append("```\n\n");
        sb.append("## 最后更新\n");
        sb.append("- 更新时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n");
        return sb.toString();
    }
    
    public void recordLearning(String category, String scenario, String problem, 
                                String solution, String effect, String reusable) {
        if (!enabled) return;
        
        try {
            Path learningsPath = Paths.get(memoryPath, "LEARNINGS.md");
            String content = Files.exists(learningsPath) ? 
                    Files.readString(learningsPath, StandardCharsets.UTF_8) : "";
            
            String entry;
            if ("SUCCESS".equals(category)) {
                entry = String.format("\n### [%s] %s\n- **问题**: %s\n- **解决方案**: %s\n- **效果**: %s\n- **可复用性**: %s\n",
                        LocalDateTime.now().format(dateTimeFormatter), scenario, 
                        problem, solution, effect, reusable);
            } else if ("FAILURE".equals(category)) {
                entry = String.format("\n### [%s] %s\n- **问题**: %s\n- **原因**: %s\n- **改进措施**: %s\n- **预防措施**: %s\n",
                        LocalDateTime.now().format(dateTimeFormatter), scenario, 
                        problem, solution, effect, reusable);
            } else if ("CORRECTION".equals(category)) {
                entry = String.format("\n### [%s] %s\n- **原知识**: %s\n- **修正内容**: %s\n- **修正原因**: %s\n",
                        LocalDateTime.now().format(dateTimeFormatter), scenario, 
                        problem, solution, effect);
            } else {
                entry = "";
            }
            
            int insertPos = content.lastIndexOf("## 最后更新");
            if (insertPos > 0) {
                content = content.substring(0, insertPos) + entry + "\n" + content.substring(insertPos);
            } else {
                content += entry;
            }
            
            Files.writeString(learningsPath, content, StandardCharsets.UTF_8);
            log.info("记录学习经验: {} - {}", category, scenario);
        } catch (Exception e) {
            log.error("记录学习经验失败", e);
        }
    }
    
    public void recordDailyLog(String content) {
        if (!enabled) return;
        
        try {
            String today = LocalDateTime.now().format(dateFormatter);
            Path dailyPath = Paths.get(memoryPath, "daily", today + ".md");
            
            String header = "# 日志 " + today + "\n\n";
            String existing = "";
            if (Files.exists(dailyPath)) {
                existing = Files.readString(dailyPath, StandardCharsets.UTF_8);
                if (existing.startsWith(header)) {
                    existing = existing.substring(header.length());
                }
            }
            
            String newContent = header + content + "\n\n---\n" + 
                    (existing.isEmpty() ? "" : existing);
            
            Files.writeString(dailyPath, newContent, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("记录日志失败", e);
        }
    }
    
    public void appendToMemory(String fileName, String content) {
        if (!enabled) return;
        
        try {
            Path filePath = Paths.get(memoryPath, fileName);
            String existing = Files.exists(filePath) ? 
                    Files.readString(filePath, StandardCharsets.UTF_8) : "";
            
            String timestamp = LocalDateTime.now().format(dateTimeFormatter);
            String newEntry = "\n\n---\n## [" + timestamp + "]\n" + content;
            
            Files.writeString(filePath, existing + newEntry, StandardCharsets.UTF_8);
            log.debug("追加记忆到{}: {} 字符", fileName, content.length());
        } catch (Exception e) {
            log.error("追加记忆失败: {}", fileName, e);
        }
    }
    
    public void updateSkillUsage(String skillName, boolean success) {
        if (!enabled) return;
        
        try {
            Path skillsPath = Paths.get(memoryPath, "SKILLS.md");
            String content = Files.exists(skillsPath) ? 
                    Files.readString(skillsPath, StandardCharsets.UTF_8) : getDefaultSkills();
            
            // 检查内容是否为空
            if (content == null || content.isEmpty()) {
                content = getDefaultSkills();
            }
            
            String statsSection = extractSection(content, "## 技能熟练度");
            
            // 如果找不到技能熟练度部分，创建默认结构
            if (statsSection == null || statsSection.isEmpty()) {
                statsSection = "## 技能熟练度\n\n| 技能 | 使用次数 | 成功率 | 最后更新 |\n|------|---------|--------|----------|\n";
                content = content + "\n\n" + statsSection;
            }
            
            String[] lines = statsSection.split("\n");
            List<String> newLines = new ArrayList<>();
            boolean found = false;
            
            for (String line : lines) {
                if (line != null && line.contains(skillName + " |")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 4) {
                        try {
                            int count = Integer.parseInt(parts[1].trim()) + 1;
                            String rateStr = parts[2].trim().replace("%", "");
                            double oldRate = Double.parseDouble(rateStr.isEmpty() ? "0" : rateStr);
                            double rate = success ? 
                                    (oldRate * (count - 1) + 100) / count :
                                    (oldRate * (count - 1)) / count;
                            line = String.format("| %s | %d | %.0f%% | %s |", 
                                    skillName, count, rate, LocalDateTime.now().format(dateFormatter));
                            found = true;
                        } catch (NumberFormatException nfe) {
                            log.warn("解析技能统计数据失败: {}", line);
                            // 重置该行的统计
                            line = String.format("| %s | 1 | %s | %s |", 
                                    skillName, success ? "100%" : "0%", LocalDateTime.now().format(dateFormatter));
                            found = true;
                        }
                    }
                }
                newLines.add(line);
            }
            
            if (!found) {
                newLines.add(String.format("| %s | 1 | %s | %s |", 
                        skillName, success ? "100%" : "0%", LocalDateTime.now().format(dateFormatter)));
            }
            
            String newStats = String.join("\n", newLines);
            
            // 安全替换，检查 statsSection 是否在 content 中
            if (content.contains(statsSection)) {
                content = content.replace(statsSection, newStats);
            } else {
                // 如果找不到原段落，追加新内容
                log.warn("SKILLS.md 中找不到技能熟练度段落，追加新内容");
                content = content + "\n\n## 技能熟练度\n\n" + newStats;
            }
            
            Files.writeString(skillsPath, content, StandardCharsets.UTF_8);
            log.debug("更新技能使用统计成功: skill={}, success={}", skillName, success);
        } catch (Exception e) {
            log.error("更新技能使用统计失败: skill={}, success={}", skillName, success, e);
        }
    }
    
    public void updateUserProfile(String key, String value) {
        if (!enabled) return;
        
        try {
            Path userPath = Paths.get(memoryPath, "USER.md");
            String content = Files.exists(userPath) ? 
                    Files.readString(userPath, StandardCharsets.UTF_8) : getDefaultUser();
            
            String updateRecord = "- 更新" + key + ": " + value + " [" + 
                    LocalDateTime.now().format(dateTimeFormatter) + "]\n";
            
            int insertPos = content.lastIndexOf("## 最后更新");
            if (insertPos > 0) {
                content = content.substring(0, insertPos) + updateRecord + content.substring(insertPos);
            }
            
            Files.writeString(userPath, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("更新用户画像失败", e);
        }
    }
    
    public String readMemory(String fileName) {
        try {
            Path filePath = Paths.get(memoryPath, fileName);
            if (Files.exists(filePath)) {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("读取记忆文件失败: {}", fileName, e);
        }
        return "";
    }
    
    public String exportAllMemories() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 白盒记忆导出\n");
        sb.append("导出时间: ").append(LocalDateTime.now().format(dateTimeFormatter)).append("\n\n");
        
        try {
            Path basePath = Paths.get(memoryPath);
            if (Files.exists(basePath)) {
                Files.list(basePath)
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            sb.append("---\n");
                            sb.append("# 文件: ").append(p.getFileName()).append("\n\n");
                            sb.append(Files.readString(p, StandardCharsets.UTF_8));
                            sb.append("\n\n");
                        } catch (Exception e) {
                            log.error("导出记忆失败: {}", p, e);
                        }
                    });
            }
        } catch (Exception e) {
            log.error("导出记忆失败", e);
        }
        
        return sb.toString();
    }
    
    public void importMemories(String content) {
        if (!enabled) return;
        
        try {
            String[] files = content.split("---\n# 文件: ");
            for (int i = 1; i < files.length; i++) {
                String[] parts = files[i].split("\n\n", 2);
                if (parts.length >= 2) {
                    String fileName = parts[0].trim();
                    String fileContent = parts[1];
                    Path filePath = Paths.get(memoryPath, fileName);
                    Files.writeString(filePath, fileContent, StandardCharsets.UTF_8);
                    log.info("导入记忆文件: {}", fileName);
                }
            }
        } catch (Exception e) {
            log.error("导入记忆失败", e);
        }
    }
    
    private String extractSection(String content, String sectionHeader) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        int start = content.indexOf(sectionHeader);
        if (start < 0) return "";
        
        int end = content.indexOf("\n## ", start + 1);
        if (end < 0) {
            end = content.indexOf("\n# 最后更新", start + 1);
        }
        if (end < 0 || end <= start) {
            end = content.length();
        }
        
        if (start >= content.length() || end > content.length() || end <= start) {
            return "";
        }
        
        return content.substring(start, end);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getMemoryPath() {
        return Paths.get(memoryPath).toAbsolutePath().toString();
    }
    
    public List<String> listMemoryFiles() {
        try {
            Path basePath = Paths.get(memoryPath);
            if (!Files.exists(basePath)) {
                return new ArrayList<>();
            }
            List<String> files = new ArrayList<>();
            Files.list(basePath)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> files.add(p.getFileName().toString()));
            return files;
        } catch (Exception e) {
            log.error("列出记忆文件失败", e);
            return new ArrayList<>();
        }
    }
    
    public EnhancedSearchResult searchMemoriesEnhanced(String query, int topK) {
        if (!enabled) {
            return new EnhancedSearchResult();
        }
        
        EnhancedSearchResult result = new EnhancedSearchResult();
        result.setQuery(query);
        
        try {
            List<MemoryItem> keywordResults = searchByKeyword(query, topK);
            result.setKeywordResults(keywordResults);
            
            if (vectorEnabled && vectorSearchService != null && embeddingService != null) {
                List<MemoryItem> vectorResults = searchByVector(query, topK);
                result.setVectorResults(vectorResults);
                
                List<MemoryItem> merged = mergeResults(keywordResults, vectorResults);
                result.setMergedResults(merged);
                
                if (merged.size() > summaryThreshold && aiProviderService != null) {
                    String summary = generateSummary(merged, query);
                    result.setSummary(summary);
                }
            } else {
                result.setMergedResults(keywordResults);
            }
            
            List<MemoryItem> related = findRelatedMemories(result.getMergedResults());
            result.setRelatedMemories(related);
            
            log.info("增强记忆搜索完成: query={}, keywordResults={}, vectorResults={}, related={}", 
                    query, keywordResults.size(), 
                    result.getVectorResults() != null ? result.getVectorResults().size() : 0,
                    related.size());
            
        } catch (Exception e) {
            log.error("增强记忆搜索失败: query={}", query, e);
        }
        
        return result;
    }
    
    private List<MemoryItem> searchByKeyword(String query, int topK) {
        List<MemoryItem> results = new ArrayList<>();
        
        try {
            Path basePath = Paths.get(memoryPath);
            if (!Files.exists(basePath)) {
                return results;
            }
            
            String lowerQuery = query.toLowerCase();
            
            Files.list(basePath)
                .filter(p -> p.toString().endsWith(".md"))
                .forEach(p -> {
                    try {
                        String content = Files.readString(p, StandardCharsets.UTF_8);
                        String lowerContent = content.toLowerCase();
                        
                        if (lowerContent.contains(lowerQuery)) {
                            MemoryItem item = new MemoryItem();
                            item.setFileName(p.getFileName().toString());
                            item.setContent(extractRelevantSnippet(content, query, 200));
                            item.setScore(calculateKeywordScore(lowerContent, lowerQuery));
                            item.setSourceType("keyword");
                            results.add(item);
                        }
                    } catch (Exception e) {
                        log.warn("搜索记忆文件失败: {}", p, e);
                    }
                });
            
            results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            int limitSize = Math.min(results.size(), topK);
            return new ArrayList<>(results.subList(0, limitSize));
            
        } catch (Exception e) {
            log.error("关键词搜索失败", e);
        }
        
        return results;
    }
    
    private List<MemoryItem> searchByVector(String query, int topK) {
        List<MemoryItem> results = new ArrayList<>();
        
        try {
            float[] embedding = embeddingService.embed(query);
            if (embedding == null || embedding.length == 0) {
                return results;
            }
            
            List<Float> queryVector = new ArrayList<>();
            for (float f : embedding) {
                queryVector.add(f);
            }
            
            List<MemorySearchResult> vectorResults = vectorSearchService.searchByVector(queryVector, topK);
            
            for (MemorySearchResult vr : vectorResults) {
                MemoryItem item = new MemoryItem();
                item.setMemoryId(vr.getMemoryId());
                item.setContent(vr.getContent());
                item.setScore(1.0 / (1.0 + vr.getScore()));
                item.setSourceType("vector");
                results.add(item);
            }
            
        } catch (Exception e) {
            log.error("向量搜索失败", e);
        }
        
        return results;
    }
    
    private List<MemoryItem> mergeResults(List<MemoryItem> keywordResults, List<MemoryItem> vectorResults) {
        Map<String, MemoryItem> merged = new LinkedHashMap<>();
        
        for (MemoryItem item : keywordResults) {
            String key = item.getFileName() != null ? item.getFileName() : String.valueOf(item.getMemoryId());
            item.setScore(item.getScore() * 0.4);
            merged.put(key, item);
        }
        
        for (MemoryItem item : vectorResults) {
            String key = item.getFileName() != null ? item.getFileName() : String.valueOf(item.getMemoryId());
            if (merged.containsKey(key)) {
                MemoryItem existing = merged.get(key);
                existing.setScore(existing.getScore() + item.getScore() * 0.6);
                existing.setSourceType("hybrid");
            } else {
                item.setScore(item.getScore() * 0.6);
                merged.put(key, item);
            }
        }
        
        List<MemoryItem> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return result;
    }
    
    private String generateSummary(List<MemoryItem> memories, String query) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("以下是关于\"").append(query).append("\"的相关记忆内容\n\n");
            
            for (int i = 0; i < Math.min(memories.size(), 10); i++) {
                MemoryItem item = memories.get(i);
                context.append(i + 1).append(". ");
                if (item.getFileName() != null) {
                    context.append("[").append(item.getFileName()).append("] ");
                }
                context.append(item.getContent()).append("\n\n");
            }
            
            String systemPrompt = "你是一个专业的记忆总结助手。请根据用户查询，对相关记忆内容进行简洁、准确的总结。" +
                    "总结应该包含关键信息、重要结论和可操作的见解。控制在200字以内。";
            
            String summary = aiProviderService.chatWithFallback(systemPrompt, context.toString(), 0.7, 500);
            return summary;
            
        } catch (Exception e) {
            log.error("生成总结失败", e);
            return "记忆总结生成失败";
        }
    }
    
    private List<MemoryItem> findRelatedMemories(List<MemoryItem> primaryResults) {
        List<MemoryItem> related = new ArrayList<>();
        Set<String> addedKeys = new HashSet<>();
        
        for (MemoryItem item : primaryResults) {
            if (item.getFileName() != null) {
                String fileName = item.getFileName();
                if (fileName.equals("SOUL.md")) {
                    addRelatedIfNotExists(related, addedKeys, "IDENTITY.md", "身份信息");
                    addRelatedIfNotExists(related, addedKeys, "SKILLS.md", "技能清单");
                } else if (fileName.equals("SKILLS.md")) {
                    addRelatedIfNotExists(related, addedKeys, "LEARNINGS.md", "学习记录");
                } else if (fileName.equals("USER.md")) {
                    addRelatedIfNotExists(related, addedKeys, "IDENTITY.md", "身份信息");
                }
            }
            
            if (item.getMemoryId() != null && relatedMemories.containsKey(item.getMemoryId())) {
                Set<Long> relatedIds = relatedMemories.get(item.getMemoryId());
                for (Long relatedId : relatedIds) {
                    if (memoryIdMap.containsKey(relatedId)) {
                        MemoryItem relatedItem = new MemoryItem();
                        relatedItem.setMemoryId(relatedId);
                        relatedItem.setContent(memoryIdMap.get(relatedId));
                        relatedItem.setSourceType("related");
                        relatedItem.setScore(0.3);
                        related.add(relatedItem);
                    }
                }
            }
        }
        
        return related;
    }
    
    private void addRelatedIfNotExists(List<MemoryItem> related, Set<String> addedKeys, 
                                       String fileName, String description) {
        if (!addedKeys.contains(fileName)) {
            try {
                Path filePath = Paths.get(memoryPath, fileName);
                if (Files.exists(filePath)) {
                    MemoryItem item = new MemoryItem();
                    item.setFileName(fileName);
                    item.setContent(description + ": " + extractRelevantSnippet(
                            Files.readString(filePath, StandardCharsets.UTF_8), "", 100));
                    item.setSourceType("related");
                    item.setScore(0.3);
                    related.add(item);
                    addedKeys.add(fileName);
                }
            } catch (Exception e) {
                log.warn("添加关联记忆失败: {}", fileName, e);
            }
        }
    }
    
    public void addMemoryRelation(Long memoryId1, Long memoryId2, double strength) {
        relatedMemories.computeIfAbsent(memoryId1, k -> new HashSet<>()).add(memoryId2);
        relatedMemories.computeIfAbsent(memoryId2, k -> new HashSet<>()).add(memoryId1);
        log.debug("添加记忆关联: {} <-> {}, strength={}", memoryId1, memoryId2, strength);
    }
    
    public void indexMemoryWithVector(Long memoryId, String content, List<String> tags) {
        if (!vectorEnabled || vectorSearchService == null || embeddingService == null) {
            return;
        }
        
        try {
            memoryIdMap.put(memoryId, content);
            
            if (tags != null) {
                for (String tag : tags) {
                    tagMemoryMap.computeIfAbsent(tag, k -> new HashSet<>()).add(memoryId);
                }
            }
            
            float[] embedding = embeddingService.embed(content);
            if (embedding != null && embedding.length > 0) {
                List<Float> vector = new ArrayList<>();
                for (float f : embedding) {
                    vector.add(f);
                }
                vectorSearchService.vectorizeMemory(memoryId, content, vector);
                log.info("记忆向量索引完成: memoryId={}", memoryId);
            }
            
        } catch (Exception e) {
            log.error("记忆向量索引失败: memoryId={}", memoryId, e);
        }
    }
    
    public Long storeMemoryWithVector(String fileName, String content, List<String> tags) {
        if (!enabled) return null;
        
        Long memoryId = memoryIdCounter++;
        
        try {
            appendToMemory(fileName, content);
            
            indexMemoryWithVector(memoryId, content, tags);
            
            log.info("存储记忆完成: memoryId={}, fileName={}", memoryId, fileName);
            return memoryId;
            
        } catch (Exception e) {
            log.error("存储记忆失败: fileName={}", fileName, e);
            return null;
        }
    }
    
    private String extractRelevantSnippet(String content, String query, int maxLength) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        if (query == null || query.isEmpty()) {
            return content.length() > maxLength ? content.substring(0, maxLength) + "..." : content;
        }
        
        int pos = content.toLowerCase().indexOf(query.toLowerCase());
        if (pos < 0) {
            return content.length() > maxLength ? content.substring(0, maxLength) + "..." : content;
        }
        
        int start = Math.max(0, pos - 50);
        int end = Math.min(content.length(), pos + query.length() + maxLength - 50);
        
        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        
        return snippet;
    }
    
    private double calculateKeywordScore(String content, String query) {
        String[] queryWords = query.toLowerCase().split("\\s+");
        int matches = 0;
        
        for (String word : queryWords) {
            if (content.contains(word)) {
                matches++;
            }
        }
        
        return (double) matches / queryWords.length;
    }
    
    public List<MemoryItem> searchMemories(String query, int topK) {
        return searchByKeyword(query, topK);
    }
    
    public static class MemoryItem {
        private Long memoryId;
        private String fileName;
        private String content;
        private double score;
        private String sourceType;
        
        public Long getMemoryId() { return memoryId; }
        public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    }
    
    public static class EnhancedSearchResult {
        private String query;
        private List<MemoryItem> keywordResults = new ArrayList<>();
        private List<MemoryItem> vectorResults = new ArrayList<>();
        private List<MemoryItem> mergedResults = new ArrayList<>();
        private List<MemoryItem> relatedMemories = new ArrayList<>();
        private String summary;
        
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public List<MemoryItem> getKeywordResults() { return keywordResults; }
        public void setKeywordResults(List<MemoryItem> keywordResults) { this.keywordResults = keywordResults; }
        public List<MemoryItem> getVectorResults() { return vectorResults; }
        public void setVectorResults(List<MemoryItem> vectorResults) { this.vectorResults = vectorResults; }
        public List<MemoryItem> getMergedResults() { return mergedResults; }
        public void setMergedResults(List<MemoryItem> mergedResults) { this.mergedResults = mergedResults; }
        public List<MemoryItem> getRelatedMemories() { return relatedMemories; }
        public void setRelatedMemories(List<MemoryItem> relatedMemories) { this.relatedMemories = relatedMemories; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}
