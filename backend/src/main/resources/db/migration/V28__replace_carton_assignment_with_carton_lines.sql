-- Packing turned out to be about splitting an order line's quantity across
-- cartons (e.g. 32 required -> split into 30 + 2, or split-by-quantity into
-- 4 lots of 8), not about assigning individual serial numbers to boxes -
-- StockItem.carton_id was the wrong shape for that.

DROP INDEX IF EXISTS idx_stock_items_carton;
ALTER TABLE stock_items DROP COLUMN IF EXISTS carton_id;

CREATE TABLE carton_lines (
    id BIGSERIAL PRIMARY KEY,
    order_line_id BIGINT NOT NULL,
    carton_id BIGINT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_carton_line_order_line
        FOREIGN KEY (order_line_id)
        REFERENCES order_lines(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_carton_line_carton
        FOREIGN KEY (carton_id)
        REFERENCES cartons(id)
);

CREATE INDEX idx_carton_lines_order_line ON carton_lines (order_line_id);
CREATE INDEX idx_carton_lines_carton ON carton_lines (carton_id);
