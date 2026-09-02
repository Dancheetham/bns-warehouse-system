CREATE TABLE stock_items (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    mac_address VARCHAR(100),
    serial_number VARCHAR(255),
    wifi_mac_address VARCHAR(100),
    batch_code VARCHAR(255),
    location_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    quarantined BOOLEAN NOT NULL DEFAULT FALSE,
    quarantine_reason VARCHAR(500),
    purchase_order_line_id BIGINT,
    received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_stock_item_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT fk_stock_item_location
        FOREIGN KEY (location_id)
        REFERENCES locations(id),

    CONSTRAINT fk_stock_item_po_line
        FOREIGN KEY (purchase_order_line_id)
        REFERENCES purchase_order_lines(id)
);

CREATE UNIQUE INDEX uq_stock_items_mac ON stock_items (mac_address) WHERE mac_address IS NOT NULL;
CREATE UNIQUE INDEX uq_stock_items_serial ON stock_items (serial_number) WHERE serial_number IS NOT NULL;
