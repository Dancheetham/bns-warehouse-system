CREATE TABLE expected_stock_items (
    id BIGSERIAL PRIMARY KEY,
    expected_carton_id BIGINT NOT NULL,
    mac_address VARCHAR(100),
    serial_number VARCHAR(255),
    wifi_mac_address VARCHAR(100),
    received BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_expected_stock_item_carton
        FOREIGN KEY (expected_carton_id)
        REFERENCES expected_cartons(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_expected_stock_items_mac ON expected_stock_items (mac_address) WHERE mac_address IS NOT NULL;
CREATE UNIQUE INDEX uq_expected_stock_items_serial ON expected_stock_items (serial_number) WHERE serial_number IS NOT NULL;
