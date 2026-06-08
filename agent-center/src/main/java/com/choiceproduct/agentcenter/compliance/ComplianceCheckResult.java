package com.choiceproduct.agentcenter.compliance;

public class ComplianceCheckResult {
    private boolean allowed;
    private String reason;

    public static ComplianceCheckResult allowed() {
        ComplianceCheckResult result = new ComplianceCheckResult();
        result.setAllowed(true);
        return result;
    }

    public static ComplianceCheckResult blocked(String reason) {
        ComplianceCheckResult result = new ComplianceCheckResult();
        result.setAllowed(false);
        result.setReason(reason);
        return result;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
