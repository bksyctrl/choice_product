package com.ecommerce.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "evolution")
public class EvolutionConfig {
    
    private int analysisDays = 7;
    private int minSampleSize = 10;
    private double lowCvrThreshold = 1.5;
    private double highCvrThreshold = 3.0;
    private double successRateDropThreshold = 0.8;
    private double weightBoostFactor = 1.15;
    private double weightBoostSecondary = 1.10;
    private double maxWeightLimit = 0.35;
    private double maxSecondaryWeight = 0.20;
    private double maxMarginWeight = 0.22;
    private int minFailCountForAction = 3;
    private int maxScriptLength = 400;
    private int minScriptLength = 200;
    private int scriptLengthReduction = 50;
    private double lowVideoCvrThreshold = 1.0;
    private int abTestMinSample = 3;
    private int abTestDays = 30;
    private double anomalyZScoreThreshold = 2.0;
    private int anomalyMinSample = 5;
    
    public int getAnalysisDays() { return analysisDays; }
    public void setAnalysisDays(int analysisDays) { this.analysisDays = analysisDays; }
    
    public int getMinSampleSize() { return minSampleSize; }
    public void setMinSampleSize(int minSampleSize) { this.minSampleSize = minSampleSize; }
    
    public double getLowCvrThreshold() { return lowCvrThreshold; }
    public void setLowCvrThreshold(double lowCvrThreshold) { this.lowCvrThreshold = lowCvrThreshold; }
    
    public double getHighCvrThreshold() { return highCvrThreshold; }
    public void setHighCvrThreshold(double highCvrThreshold) { this.highCvrThreshold = highCvrThreshold; }
    
    public double getSuccessRateDropThreshold() { return successRateDropThreshold; }
    public void setSuccessRateDropThreshold(double successRateDropThreshold) { this.successRateDropThreshold = successRateDropThreshold; }
    
    public double getWeightBoostFactor() { return weightBoostFactor; }
    public void setWeightBoostFactor(double weightBoostFactor) { this.weightBoostFactor = weightBoostFactor; }
    
    public double getWeightBoostSecondary() { return weightBoostSecondary; }
    public void setWeightBoostSecondary(double weightBoostSecondary) { this.weightBoostSecondary = weightBoostSecondary; }
    
    public double getMaxWeightLimit() { return maxWeightLimit; }
    public void setMaxWeightLimit(double maxWeightLimit) { this.maxWeightLimit = maxWeightLimit; }
    
    public double getMaxSecondaryWeight() { return maxSecondaryWeight; }
    public void setMaxSecondaryWeight(double maxSecondaryWeight) { this.maxSecondaryWeight = maxSecondaryWeight; }
    
    public double getMaxMarginWeight() { return maxMarginWeight; }
    public void setMaxMarginWeight(double maxMarginWeight) { this.maxMarginWeight = maxMarginWeight; }
    
    public int getMinFailCountForAction() { return minFailCountForAction; }
    public void setMinFailCountForAction(int minFailCountForAction) { this.minFailCountForAction = minFailCountForAction; }
    
    public int getMaxScriptLength() { return maxScriptLength; }
    public void setMaxScriptLength(int maxScriptLength) { this.maxScriptLength = maxScriptLength; }
    
    public int getMinScriptLength() { return minScriptLength; }
    public void setMinScriptLength(int minScriptLength) { this.minScriptLength = minScriptLength; }
    
    public int getScriptLengthReduction() { return scriptLengthReduction; }
    public void setScriptLengthReduction(int scriptLengthReduction) { this.scriptLengthReduction = scriptLengthReduction; }
    
    public double getLowVideoCvrThreshold() { return lowVideoCvrThreshold; }
    public void setLowVideoCvrThreshold(double lowVideoCvrThreshold) { this.lowVideoCvrThreshold = lowVideoCvrThreshold; }
    
    public int getAbTestMinSample() { return abTestMinSample; }
    public void setAbTestMinSample(int abTestMinSample) { this.abTestMinSample = abTestMinSample; }
    
    public int getAbTestDays() { return abTestDays; }
    public void setAbTestDays(int abTestDays) { this.abTestDays = abTestDays; }
    
    public double getAnomalyZScoreThreshold() { return anomalyZScoreThreshold; }
    public void setAnomalyZScoreThreshold(double anomalyZScoreThreshold) { this.anomalyZScoreThreshold = anomalyZScoreThreshold; }
    
    public int getAnomalyMinSample() { return anomalyMinSample; }
    public void setAnomalyMinSample(int anomalyMinSample) { this.anomalyMinSample = anomalyMinSample; }
}
