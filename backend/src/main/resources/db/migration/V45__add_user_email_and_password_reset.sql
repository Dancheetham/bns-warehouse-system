-- Optional email per user - lets an existing login send itself a "forgot
-- password" reset link. Nullable and NOT unique at the DB level (Postgres
-- allows multiple NULLs through a unique index, but two users genuinely
-- sharing one email address would collide during password-reset lookup -
-- that uniqueness is instead enforced in application code, at creation and
-- when adding an email to an existing user, so the error message can stay
-- friendly ("A login already exists with that email") rather than a raw
-- constraint violation).
ALTER TABLE users ADD COLUMN email VARCHAR(255);

-- One-time password reset tokens. Only the SHA-256 hash of the token is
-- stored (same approach as api_keys.key_hash) - a database leak alone never
-- exposes a usable reset link. A token is single-use (used_at) and
-- short-lived (expires_at, set by the application - see PasswordResetService).
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
