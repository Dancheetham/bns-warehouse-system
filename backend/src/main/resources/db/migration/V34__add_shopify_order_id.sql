-- ecommerce_order_number already holds Shopify's display name (e.g. "#1005"),
-- but pushing fulfillment status back needs the actual GraphQL id
-- (gid://shopify/Order/...) - that's what this column is for.
ALTER TABLE orders
    ADD COLUMN shopify_order_id VARCHAR(50);
