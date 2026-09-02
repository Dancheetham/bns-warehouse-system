-- Serial packing (assign each individual unit to a carton) turns out to still be
-- wanted as an option alongside quantity-split packing - a settings toggle
-- (packing_mode) picks which one is in play. Both models can coexist on the
-- cartons table; only one is populated per order depending on the mode used.

ALTER TABLE stock_items
    ADD COLUMN carton_id BIGINT REFERENCES cartons(id);

CREATE INDEX idx_stock_items_carton ON stock_items (carton_id);
