-- "Invoice Pending" is a new OrderStatus value (enum, no DB constraint to
-- update - status is stored as a plain VARCHAR with no CHECK constraint,
-- same as every other OrderStatus value before it).

ALTER TABLE order_lines ADD COLUMN quantity_invoiced INTEGER NOT NULL DEFAULT 0;

ALTER TABLE companies ADD COLUMN invoice_email VARCHAR(255);
ALTER TABLE companies ADD COLUMN vat_rate NUMERIC(5,2);
ALTER TABLE companies ADD COLUMN invoice_grouping VARCHAR(20) NOT NULL DEFAULT 'PER_ORDER';

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number INTEGER NOT NULL UNIQUE,
    invoice_type VARCHAR(20) NOT NULL,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    generation_date DATE NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    net_total NUMERIC(12,2) NOT NULL,
    vat_total NUMERIC(12,2) NOT NULL,
    grand_total NUMERIC(12,2) NOT NULL,
    vat_rate NUMERIC(5,2) NOT NULL,
    pdf_path VARCHAR(500),
    emailed_to VARCHAR(255),
    email_sent_at TIMESTAMP,
    email_error TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255)
);

CREATE INDEX idx_invoices_company_id ON invoices(company_id);

CREATE TABLE invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    order_line_id BIGINT NOT NULL REFERENCES order_lines(id),
    order_id BIGINT NOT NULL REFERENCES orders(id),
    sku VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    net_amount NUMERIC(12,2) NOT NULL,
    vat_amount NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines(invoice_id);

-- Settings (app_settings, Settings > Invoicing) - next_invoice_number starts
-- one above the last number OrderWise actually issued (#36966, per Dan), so
-- the two systems' numbers never collide. vat_rate is the global default
-- (20%) - a company's own Company.vatRate overrides it when set (e.g. 0 for
-- an Irish/zero-rated account).
INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('next_invoice_number', '36967'),
    ('vat_rate', '20');
