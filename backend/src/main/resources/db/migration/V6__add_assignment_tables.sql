-- V6: Add assignment tables and agent relationship

-- Create agents table
CREATE TABLE agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    max_active_cases INT NOT NULL DEFAULT 10,
    skills JSON,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create assignment events table
CREATE TABLE assignment_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    previous_assignee_id BIGINT,
    new_assignee_id BIGINT,
    decision_policy_version VARCHAR(10) DEFAULT 'v1',
    decision_reason VARCHAR(500),
    decision_inputs JSON,
    timestamp TIMESTAMP NOT NULL,
    correlation_id VARCHAR(100),
    actor VARCHAR(100) NOT NULL,
    FOREIGN KEY (case_id) REFERENCES cases(id),
    FOREIGN KEY (previous_assignee_id) REFERENCES agents(id),
    FOREIGN KEY (new_assignee_id) REFERENCES agents(id)
);

-- Update cases table to add assignee relationship
ALTER TABLE cases ADD COLUMN assignee_id BIGINT;
ALTER TABLE cases ADD COLUMN assigned_at TIMESTAMP;
ALTER TABLE cases ADD FOREIGN KEY (assignee_id) REFERENCES agents(id);

-- Create indexes for performance
CREATE INDEX idx_agents_active ON agents(active);
CREATE INDEX idx_assignment_events_case_id ON assignment_events(case_id);
CREATE INDEX idx_assignment_events_correlation_id ON assignment_events(correlation_id);
CREATE INDEX idx_cases_assignee_id ON cases(assignee_id);

-- Seed data: Sample agents for testing
INSERT INTO agents (name, email, active, max_active_cases, skills) VALUES
('Alice Johnson', 'alice@bankops.com', TRUE, 10, '["high_severity", "fraud"]'),
('Bob Smith', 'bob@bankops.com', TRUE, 10, '["general"]'),
('Carol White', 'carol@bankops.com', TRUE, 15, '["high_severity", "technical"]'),
('David Brown', 'david@bankops.com', FALSE, 10, '["general"]');
