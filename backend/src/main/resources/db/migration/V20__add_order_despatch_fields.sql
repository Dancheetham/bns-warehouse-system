ALTER TABLE orders
    ADD COLUMN shipping_cost NUMERIC(12,2),
    ADD COLUMN courier_method VARCHAR(255),
    ADD COLUMN credit_checked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN acknowledgement_sent_at TIMESTAMP,
    ADD COLUMN customer_email VARCHAR(255);
