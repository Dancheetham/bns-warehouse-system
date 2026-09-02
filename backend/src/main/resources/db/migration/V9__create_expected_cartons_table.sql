CREATE TABLE expected_cartons (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_line_id BIGINT NOT NULL,
    batch_code VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'EXPECTED',
    received_at TIMESTAMP,
    received_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_expected_carton_po_line
        FOREIGN KEY (purchase_order_line_id)
        REFERENCES purchase_order_lines(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_expected_cartons_batch_code ON expected_cartons (batch_code);
