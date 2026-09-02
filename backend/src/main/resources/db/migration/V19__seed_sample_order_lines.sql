INSERT INTO order_lines (order_id, product_id, quantity_ordered, quantity_despatched, unit_price)
SELECT o.id, p.id, 5, 5, 89.00
FROM orders o, products p
WHERE o.order_number = 'SO-10001' AND p.sku = 'GWN7802P';

INSERT INTO order_lines (order_id, product_id, quantity_ordered, quantity_despatched, unit_price)
SELECT o.id, p.id, 2, 0, 149.00
FROM orders o, products p
WHERE o.order_number = 'SO-10002' AND p.sku = 'GRP2615';

INSERT INTO order_lines (order_id, product_id, quantity_ordered, quantity_despatched, unit_price)
SELECT o.id, p.id, 10, 4, 12.50
FROM orders o, products p
WHERE o.order_number = 'SO-10003' AND p.sku = 'SFP-1G';

INSERT INTO order_lines (order_id, product_id, quantity_ordered, quantity_despatched, unit_price)
SELECT o.id, p.id, 3, 0, 89.00
FROM orders o, products p
WHERE o.order_number = 'SO-10004' AND p.sku = 'GWN7802P';
