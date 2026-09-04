-- Default passwords are per physical unit, not per product - e.g. every
-- GRP2601 has its own distinct default password. The product-level column
-- was wrong from the start (same value shown for every unit of a product);
-- this moves it to where a unit actually lives, and the intermediate
-- "expected" record it's received against.
ALTER TABLE stock_items
    ADD COLUMN default_password VARCHAR(100);

ALTER TABLE expected_stock_items
    ADD COLUMN default_password VARCHAR(100);

ALTER TABLE products
    DROP COLUMN default_password;
