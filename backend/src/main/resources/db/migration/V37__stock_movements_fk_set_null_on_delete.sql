-- No ON DELETE clause meant Postgres defaulted to blocking any delete of a
-- stock_items row that still had movement history against it - which is
-- almost every real stock item. stock_item_id is already nullable by design,
-- so ON DELETE SET NULL matches that intent: the movement record itself
-- (product, location, quantity, timestamp) survives, just detached from the
-- now-gone item. Needed for the bulk stock import to be able to clear out
-- superseded stock at all.
ALTER TABLE stock_movements DROP CONSTRAINT fk_movement_stock_item;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_movement_stock_item
        FOREIGN KEY (stock_item_id)
        REFERENCES stock_items(id)
        ON DELETE SET NULL;
