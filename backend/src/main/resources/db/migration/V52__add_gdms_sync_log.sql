-- Per-MAC audit log for every GDMS channel-assignment/recall attempt, so
-- staff can see exactly what happened to a specific device without digging
-- through application logs. One row per MAC per attempt (not per batch/
-- company) - Dan's explicit ask: "needs a row for every mac attempted."
CREATE TABLE gdms_sync_log (
    id BIGSERIAL PRIMARY KEY,
    attempted_at TIMESTAMP NOT NULL,
    operation VARCHAR(20) NOT NULL,       -- ASSIGN or RECALL
    source VARCHAR(100) NOT NULL,         -- Scheduled / Manual / Manual (Order BNS-1234) / Auto (Reverse to Despatch) / Auto (Cancelled) / Auto (RMA)
    order_number VARCHAR(50),
    mac_address VARCHAR(45) NOT NULL,
    channel_id VARCHAR(45),
    channel_name VARCHAR(255),
    status VARCHAR(10) NOT NULL,          -- SUCCESS or FAILURE
    error_reason VARCHAR(500)
);

-- The log page's default sort (and only server-side query pattern used
-- anywhere in this codebase for a "log" list - see PaymentTracking/
-- BugReports - is newest-first with everything else filtered client-side).
CREATE INDEX idx_gdms_sync_log_attempted_at ON gdms_sync_log (attempted_at DESC);
