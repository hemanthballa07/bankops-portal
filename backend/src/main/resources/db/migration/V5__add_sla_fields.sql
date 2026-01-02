-- Add SLA fields to cases table
ALTER TABLE cases ADD COLUMN priority VARCHAR(10);
ALTER TABLE cases ADD COLUMN sla_due_at TIMESTAMP;
ALTER TABLE cases ADD COLUMN sla_status VARCHAR(20);
ALTER TABLE cases ADD COLUMN paused_at TIMESTAMP;
ALTER TABLE cases ADD COLUMN total_paused_duration_seconds BIGINT DEFAULT 0;

-- Create SLA status change events table for audit trail
CREATE TABLE sla_status_change_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    correlation_id VARCHAR(100),
    actor VARCHAR(100) NOT NULL,
    FOREIGN KEY (case_id) REFERENCES cases(id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_sla_events_case_id ON sla_status_change_events(case_id);
CREATE INDEX idx_sla_events_correlation_id ON sla_status_change_events(correlation_id);
CREATE INDEX idx_cases_state ON cases(state);
CREATE INDEX idx_cases_sla_status ON cases(sla_status);
