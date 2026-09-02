INSERT INTO locations (code, description, active, created_at) VALUES
    ('GOODS-IN', 'Goods In holding area', TRUE, NOW()),
    ('1A', 'Bin 1A', TRUE, NOW()),
    ('1B', 'Bin 1B', TRUE, NOW()),
    ('QUARANTINE', 'Quarantine area', TRUE, NOW());

INSERT INTO suppliers (name, account_number, contact_email, active, created_at) VALUES
    ('Grandstream', 'GS-001', 'sales@grandstream.example', TRUE, NOW());

INSERT INTO products (sku, name, description, tracking_type, active, created_at) VALUES
    ('GWN7802P', 'GWN7802P Wireless Access Point', 'PoE indoor access point', 'MAC', TRUE, NOW()),
    ('GRP2615', 'GRP2615 IP Phone', 'Carrier-grade IP phone', 'MAC', TRUE, NOW()),
    ('SFP-1G', 'SFP 1G Transceiver', 'Single-mode SFP module', 'SERIAL', TRUE, NOW()),
    ('PATCH-CAT6-1M', 'Cat6 Patch Cable 1m', 'Quantity-only consumable', 'NONE', TRUE, NOW());
