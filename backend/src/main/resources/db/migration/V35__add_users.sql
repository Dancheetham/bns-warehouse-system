CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Per-user preferences (e.g. status colours) - separate from app_settings,
-- which stays global (Shopify credentials, packing mode, RMA windows etc. -
-- things that describe how the whole warehouse operates, not one person's
-- taste). A key with no row here for a given user just falls back to nothing/
-- the frontend's own default, same pattern as app_settings already uses.
CREATE TABLE user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500),
    UNIQUE (user_id, setting_key)
);
