CREATE TABLE goods_in_session_cartons (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    expected_carton_id BIGINT NOT NULL,
    scanned_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_session_carton_session
        FOREIGN KEY (session_id)
        REFERENCES goods_in_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_session_carton_expected_carton
        FOREIGN KEY (expected_carton_id)
        REFERENCES expected_cartons(id),

    CONSTRAINT uq_session_carton
        UNIQUE (session_id, expected_carton_id)
);
