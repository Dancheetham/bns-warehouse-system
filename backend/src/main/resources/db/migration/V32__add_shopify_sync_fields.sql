ALTER TABLE products
    ADD COLUMN shopify_product_id VARCHAR(50),
    ADD COLUMN shopify_variant_id VARCHAR(50),
    -- Set true for anything a Shopify sync creates - Shopify has no concept of
    -- MAC/serial tracking, default bin, or a default password, so a freshly
    -- synced product needs a human to confirm those before it's really usable.
    ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_synced_at TIMESTAMP;

CREATE INDEX idx_products_shopify_variant ON products (shopify_variant_id);
