CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    stock_item_id BIGINT,
    product_id BIGINT NOT NULL,
    from_location_id BIGINT,
    to_location_id BIGINT,
    movement_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    reference VARCHAR(255),
    notes VARCHAR(500),
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_movement_stock_item
        FOREIGN KEY (stock_item_id)
        REFERENCES stock_items(id),

    CONSTRAINT fk_movement_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT fk_movement_from_location
        FOREIGN KEY (from_location_id)
        REFERENCES locations(id),

    CONSTRAINT fk_movement_to_location
        FOREIGN KEY (to_location_id)
        REFERENCES locations(id)
);

CREATE INDEX idx_stock_movements_stock_item ON stock_movements (stock_item_id);
