INSERT INTO orders (order_number, order_date, customer_name, order_reference, ordered_by, delivery_name, delivery_town, delivery_country, delivery_postcode, delivery_country_code, status, order_type, created_at)
VALUES
    ('SO-10001', NOW() - INTERVAL '3 days', 'Northfield IT Solutions', 'PO-8842', 'J. Carter', 'Northfield IT Solutions', 'Manchester', 'United Kingdom', 'M1 4BT', 'GB', 'COMPLETED', 'ORDER', NOW() - INTERVAL '3 days'),
    ('SO-10002', NOW() - INTERVAL '2 days', 'Riverside Networks Ltd', 'RN-2291', 'A. Patel', 'Riverside Networks Ltd - Goods In', 'Leeds', 'United Kingdom', 'LS1 2AB', 'GB', 'ON_HOLD', 'ORDER', NOW() - INTERVAL '2 days'),
    ('SO-10003', NOW() - INTERVAL '1 days', 'Castle Comms', NULL, 'S. Byrne', 'Castle Comms Warehouse', 'Bristol', 'United Kingdom', 'BS1 5TR', 'GB', 'PARTIALLY_DESPATCHED', 'ORDER', NOW() - INTERVAL '1 days'),
    ('SO-10004', NOW(), 'Harbor Telecom', 'HT-QUOTE-014', 'M. Ahmed', 'Harbor Telecom', 'Southampton', 'United Kingdom', 'SO14 3JP', 'GB', 'AWAITING_CONVERSION', 'QUOTE', NOW());
