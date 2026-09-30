-- General-purpose notification bell (top-right icon in the app) - deliberately
-- not GDMS-specific, so other notification types (and a future reminder
-- system Dan's mentioned wanting) can use the same table without rework.
-- `type` is a short machine tag (e.g. "GDMS_FAILURE"), `link` is a relative
-- frontend path (with query string) to navigate to when clicked - for a GDMS
-- failure, the GDMS Log page pre-filtered to that day and FAILURE status.
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    link VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP
);

CREATE INDEX idx_notifications_read_at ON notifications (read_at);
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
