CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    -- Null means no credit account - a straightforward customer with no terms,
    -- not tracked against a limit at all.
    credit_limit NUMERIC(12,2),
    -- Set manually once the corresponding Shopify Company exists, so a future
    -- sync can push the credit figures back as metafields on it.
    shopify_company_id VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE orders
    ADD COLUMN company_id BIGINT REFERENCES companies(id);

CREATE INDEX idx_orders_company ON orders (company_id);

-- Payments are recorded against a specific order (mirrors OrderWise) rather than
-- as a general running balance against the company - "Generate Invoices" already
-- produces one invoice per order, so payment allocation naturally follows that.
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    amount NUMERIC(12,2) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    reference VARCHAR(255),
    notes TEXT,
    recorded_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_payments_order ON payments (order_id);
