CREATE TABLE bug_reports (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    error_code VARCHAR(20),
    description TEXT NOT NULL,
    context VARCHAR(500),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_bug_reports_occurred_at ON bug_reports (occurred_at);
