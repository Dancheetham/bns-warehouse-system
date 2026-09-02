CREATE TABLE cartons (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    carton_number INTEGER NOT NULL,
    weight_kg NUMERIC(10,3),
    tracking_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_carton_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_carton_order_number
        UNIQUE (order_id, carton_number)
);

ALTER TABLE stock_items
    ADD COLUMN carton_id BIGINT REFERENCES cartons(id);

CREATE INDEX idx_stock_items_carton ON stock_items (carton_id);
