CREATE TABLE order_lines (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_ordered INTEGER NOT NULL,
    quantity_despatched INTEGER NOT NULL DEFAULT 0,
    unit_price NUMERIC(12,2),
    notes VARCHAR(500),

    CONSTRAINT fk_order_line_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_order_line_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
);
