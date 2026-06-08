package com.choiceproduct.agentcenter.compliance;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ComplianceControlAgent {
    private final List<String> blockedKeywords = List.of("绕过风控", "盗号", "恶意攻击");

    public ComplianceCheckResult checkCompliance(String message, String scope) {
        String text = message == null ? "" : message;
        for (String keyword : blockedKeywords) {
            if (text.contains(keyword)) {
                return ComplianceCheckResult.blocked("命中安全合规关键词：" + keyword);
            }
        }
        return ComplianceCheckResult.allowed();
    }
}
