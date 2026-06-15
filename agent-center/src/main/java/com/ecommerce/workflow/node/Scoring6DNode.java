package com.ecommerce.workflow.node;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommerce.workflow.engine.ExecutionContext;
import com.ecommerce.workflow.engine.NodeExecutor;
import com.ecommerce.workflow.engine.NodeResult;
import com.ecommerce.workflow.service.evolution.SkillConfigService;

@Service
public class Scoring6DNode implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(Scoring6DNode.class);
    private final SkillConfigService skillConfigService;

    public Scoring6DNode(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    @Override
    public String getNodeCode() {
        return "scoring_6d";
    }

    @Override
    public NodeResult execute(ExecutionContext context) throws Exception {
        log.info("执行6维评分节点(使用Skill配置)");

        Map<String, Object> skillParams = skillConfigService.getSkillParams("scorer_6d");
        double[] weights = extractWeights(skillParams);
        String[] dimensions = (String[]) skillParams.getOrDefault("dimensions",
                new String[]{"salesVolume", "growthRate", "profitMargin",
                        "competition", "marketDemand", "supplyStability"});
        double sThreshold = ((Number) skillParams.getOrDefault("sThreshold", 85.0)).doubleValue();
        double aThreshold = ((Number) skillParams.getOrDefault("aThreshold", 75.0)).doubleValue();
        double bThreshold = ((Number) skillParams.getOrDefault("bThreshold", 65.0)).doubleValue();

        log.info("获取Skill配置: weights={}, s={} a={} b={}",
                Arrays.toString(weights), sThreshold, aThreshold, bThreshold);

        List<Map<String, Object>> products = getProductsFromContext(context);

        if (products.isEmpty()) {
            log.warn("上下文中未找到产品数据，使用变量构造默认产品");
            products = fallbackProducts(context);
        }

        List<Map<String, Object>> scoredProducts = new ArrayList<>();
        for (Map<String, Object> product : products) {
            Map<String, Object> scores = calculateScoresFromRealData(product, dimensions);
            double totalScore = calculateWeightedTotal(scores, weights);
            String grade = assignGrade(totalScore, sThreshold, aThreshold, bThreshold);

            product.put("scores", scores);
            product.put("totalScore", Math.round(totalScore * 100.0) / 100.0);
            product.put("grade", grade);
            scoredProducts.add(product);
        }

        scoredProducts.sort((a, b) -> Double.compare(
                (Double) b.get("totalScore"),
                (Double) a.get("totalScore")
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("scoredProducts", scoredProducts);
        result.put("totalProducts", scoredProducts.size());
        result.put("sGradeCount", scoredProducts.stream().filter(p -> "S".equals(p.get("grade"))).count());
        result.put("aGradeCount", scoredProducts.stream().filter(p -> "A".equals(p.get("grade"))).count());
        result.put("skillVersion", "scorer_6d_v" + getCurrentVersion());
        result.put("weightsUsed", weights);

        context.setNodeOutput(getNodeCode(), result);

        log.info("评分完成: 共{}个产品, S级{}个, A级{}个 (Skill版本: {})",
                scoredProducts.size(), result.get("sGradeCount"), result.get("aGradeCount"),
                result.get("skillVersion"));

        return NodeResult.success(result);
    }

    private List<Map<String, Object>> getProductsFromContext(ExecutionContext context) {
        List<Map<String, Object>> products = new ArrayList<>();

        Object fetchData = context.getNodeOutput("data_fetch");
        if (fetchData instanceof Map) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) ((Map<?, ?>) fetchData).get("products");
            if (list != null) products.addAll(list);
        }

        Object inputProducts = context.getVariables().get("products");
        if (inputProducts instanceof List) {
            for (Object item : (List<?>) inputProducts) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) item;
                    products.add(new HashMap<>(p));
                }
            }
        }

        return products;
    }

    private List<Map<String, Object>> fallbackProducts(ExecutionContext context) {
        List<Map<String, Object>> products = new ArrayList<>();

        String productName = (String) context.getVariables().getOrDefault("productName",
                context.getVariables().getOrDefault("product", "默认产品"));
        String category = (String) context.getVariables().getOrDefault("category", "默认分类");
        Number price = context.getVariables().get("price") instanceof Number
                ? (Number) context.getVariables().get("price") : null;
        Number cvr = context.getVariables().get("cvr") instanceof Number
                ? (Number) context.getVariables().get("cvr") : null;
        Number gmv = context.getVariables().get("gmv") instanceof Number
                ? (Number) context.getVariables().get("gmv") : null;

        Map<String, Object> product = new HashMap<>();
        product.put("name", productName);
        product.put("category", category);
        if (price != null) product.put("price", price.doubleValue());
        if (cvr != null) product.put("cvr", cvr.doubleValue());
        if (gmv != null) product.put("gmv", gmv);
        
        products.add(product);
        return products;
    }

    private Map<String, Object> calculateScoresFromRealData(Map<String, Object> product, String[] dimensions) {
        Map<String, Object> scores = new HashMap<>();
        
        Double cvr = getDoubleValue(product, "cvr");
        Object gmvObj = product.get("gmv");
        Double price = getDoubleValue(product, "price");
        String qualityTag = (String) product.get("qualityTag");
        String category = (String) product.get("category");
        
        for (String dim : dimensions) {
            double score = calculateDimensionScore(dim, cvr, gmvObj, price, qualityTag, category, product);
            scores.put(dim, Math.min(100, Math.max(0, Math.round(score * 10.0) / 10.0)));
        }
        
        return scores;
    }
    
    private double calculateDimensionScore(String dimension, Double cvr, Object gmv, Double price, 
                                          String qualityTag, String category, Map<String, Object> product) {
        double baseScore = 50.0;
        
        switch (dimension) {
            case "salesVolume":
                if (cvr != null && cvr > 0) {
                    if (cvr >= 0.05) baseScore = 90 + (cvr - 0.05) * 100;
                    else if (cvr >= 0.03) baseScore = 80 + (cvr - 0.03) * 500;
                    else if (cvr >= 0.01) baseScore = 70 + (cvr - 0.01) * 500;
                    else baseScore = 50 + cvr * 2000;
                } else if ("SUCCESS".equals(qualityTag)) {
                    baseScore = 75.0;
                } else if ("FAIL".equals(qualityTag)) {
                    baseScore = 30.0;
                }
                break;
                
            case "growthRate":
                if (cvr != null && cvr > 0) {
                    if (cvr >= 0.04) baseScore = 85 + (cvr - 0.04) * 200;
                    else if (cvr >= 0.02) baseScore = 70 + (cvr - 0.02) * 750;
                    else baseScore = 50 + cvr * 1000;
                } else {
                    baseScore = 60.0;
                }
                break;
                
            case "profitMargin":
                if (price != null && price > 0) {
                    if (price >= 500) baseScore = 85.0;
                    else if (price >= 200) baseScore = 75.0;
                    else if (price >= 100) baseScore = 65.0;
                    else if (price >= 50) baseScore = 55.0;
                    else baseScore = 45.0;
                } else {
                    baseScore = 60.0;
                }
                break;
                
            case "competition":
                if (category != null && !category.isEmpty()) {
                    if (category.contains("美妆") || category.contains("护肤")) {
                        baseScore = 40.0;
                    } else if (category.contains("服装") || category.contains("鞋包")) {
                        baseScore = 50.0;
                    } else if (category.contains("食品") || category.contains("家居")) {
                        baseScore = 65.0;
                    } else if (category.contains("数码") || category.contains("家电")) {
                        baseScore = 55.0;
                    } else {
                        baseScore = 60.0;
                    }
                } else {
                    baseScore = 55.0;
                }
                break;
                
            case "marketDemand":
                if (cvr != null && cvr > 0) {
                    baseScore = 50 + cvr * 800;
                } else if ("SUCCESS".equals(qualityTag)) {
                    baseScore = 70.0;
                } else {
                    baseScore = 55.0;
                }
                break;
                
            case "supplyStability":
                if (gmv != null) {
                    double gmvValue = 0;
                    if (gmv instanceof Number) {
                        gmvValue = ((Number) gmv).doubleValue();
                    } else if (gmv instanceof BigDecimal) {
                        gmvValue = ((BigDecimal) gmv).doubleValue();
                    }
                    
                    if (gmvValue >= 100000) baseScore = 90.0;
                    else if (gmvValue >= 50000) baseScore = 80.0;
                    else if (gmvValue >= 10000) baseScore = 70.0;
                    else if (gmvValue >= 5000) baseScore = 60.0;
                    else baseScore = 50.0;
                } else {
                    baseScore = 55.0;
                }
                break;
                
            default:
                baseScore = 50.0;
        }
        
        return baseScore;
    }
    
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private double calculateWeightedTotal(Map<String, Object> scores, double[] weights) {
        String[] dims = scores.keySet().toArray(new String[0]);
        double total = 0.0;
        for (int i = 0; i < Math.min(dims.length, weights.length); i++) {
            Object val = scores.get(dims[i]);
            double score = val instanceof Number ? ((Number) val).doubleValue() : 50.0;
            total += score * weights[i];
        }
        return total;
    }

    private String assignGrade(double score, double sTh, double aTh, double bTh) {
        if (score >= sTh) return "S";
        if (score >= aTh) return "A";
        if (score >= bTh) return "B";
        if (score >= 45) return "C";
        return "D";
    }

    private double[] extractWeights(Map<String, Object> params) {
        Object w = params.get("weights");
        if (w instanceof double[]) return (double[]) w;
        if (w instanceof Object[]) {
            Object[] arr = (Object[]) w;
            double[] result = new double[arr.length];
            for (int i = 0; i < arr.length; i++)
                result[i] = arr[i] instanceof Number ? ((Number) arr[i]).doubleValue() : 0.0;
            return result;
        }
        return new double[]{0.25, 0.20, 0.15, 0.15, 0.15, 0.10};
    }

    private String getCurrentVersion() {
        try {
            var skill = skillConfigService.getActiveSkill("scorer_6d");
            return skill != null ? skill.getVersion() : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }
}
