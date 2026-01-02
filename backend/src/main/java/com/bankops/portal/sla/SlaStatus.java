package com.bankops.portal.sla;

/**
 * SLA status values indicating case urgency.
 */
public enum SlaStatus {
    /**
     * Case is on track to meet SLA (< 80% elapsed)
     */
    ON_TRACK,

    /**
     * Case is at risk of breaching SLA (>= 80% elapsed)
     */
    AT_RISK,

    /**
     * Case has breached SLA (past due time)
     */
    BREACHED
}
