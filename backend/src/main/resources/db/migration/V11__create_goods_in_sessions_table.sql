CREATE TABLE goods_in_sessions (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    started_by VARCHAR(255),
    started_at TIMESTAMP NOT NULL,
    saved_by VARCHAR(255),
    saved_at TIMESTAMP,

    CONSTRAINT fk_goods_in_session_po
        FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_orders(id),

    CONSTRAINT fk_goods_in_session_location
        FOREIGN KEY (location_id)
        REFERENCES locations(id)
);
