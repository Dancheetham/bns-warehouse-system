ALTER TABLE order_lines
    ADD COLUMN quantity_picked INTEGER NOT NULL DEFAULT 0;

ALTER TABLE stock_items
    ADD COLUMN order_line_id BIGINT REFERENCES order_lines(id);

ALTER TABLE orders
    ADD COLUMN picking_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN picked_by VARCHAR(255),
    ADD COLUMN picking_started_at TIMESTAMP,
    ADD COLUMN picking_completed_at TIMESTAMP;

CREATE INDEX idx_stock_items_order_line ON stock_items (order_line_id);
