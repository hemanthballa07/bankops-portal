package com.bankops.portal.sla;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SLA calculations.
 */
@Configuration
public class SlaConfiguration {

    /**
     * Threshold for AT_RISK status (0.8 = 80% of SLA elapsed)
     */
    @Value("${sla.at-risk-threshold:0.8}")
    private double atRiskThreshold;

    public double getAtRiskThreshold() {
        return atRiskThreshold;
    }

    /**
     * Check if elapsed percentage crosses AT_RISK threshold
     */
    public boolean isAtRisk(double elapsedPercentage) {
        return elapsedPercentage >= atRiskThreshold;
    }
}
