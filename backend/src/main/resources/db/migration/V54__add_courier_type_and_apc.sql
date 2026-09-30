-- Part A: explicit courier-type discriminator (previously inferred purely
-- from whether dpd_shipment_id was set - see Order.java/CourierType.java),
-- plus a "Collection" path (external couriers BNS doesn't book itself, just
-- records which one was used) and Part B's APC Overnight integration.

ALTER TABLE orders ADD COLUMN courier_type VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- Backfill: any order that already has SOMETHING DPD-shaped on it was, in
-- practice, a DPD order under the old implicit scheme - everything else
-- genuinely had no courier booking, so it stays NONE.
UPDATE orders
SET courier_type = 'DPD'
WHERE dpd_shipment_id IS NOT NULL
   OR dpd_network_key IS NOT NULL
   OR courier_method IS NOT NULL;

-- Which extensible "Collection" option (Customer/UKI/InXpress/Seabridge/...)
-- was picked, captured as plain text at selection time - deliberately NOT a
-- foreign key to collection_courier_options, so renaming or deleting an
-- option later never orphans a historical order.
ALTER TABLE orders ADD COLUMN collection_courier_name VARCHAR(100);

-- Part B: APC Overnight (Hypaship) fields, mirroring the existing dpd_* ones.
ALTER TABLE orders ADD COLUMN apc_service_code VARCHAR(50);
ALTER TABLE orders ADD COLUMN apc_order_number VARCHAR(30);
ALTER TABLE orders ADD COLUMN apc_waybill VARCHAR(30);
ALTER TABLE orders ADD COLUMN apc_shipped_at TIMESTAMP;

-- Mirror the same fields onto shipments (Shipment.java) so a shipment
-- archived when an order is reopened for an extra shipment keeps its
-- courier-type/Collection/APC data too, not just the original dpd_* fields.
ALTER TABLE shipments ADD COLUMN courier_type VARCHAR(20);
ALTER TABLE shipments ADD COLUMN collection_courier_name VARCHAR(100);
ALTER TABLE shipments ADD COLUMN apc_service_code VARCHAR(50);
ALTER TABLE shipments ADD COLUMN apc_order_number VARCHAR(30);
ALTER TABLE shipments ADD COLUMN apc_waybill VARCHAR(30);
ALTER TABLE shipments ADD COLUMN apc_shipped_at TIMESTAMP;

-- Extensible list of "Collection" couriers shown on the order screen when
-- Courier = Collection - plain labels, not integrated with any carrier API.
-- Referenced from orders.collection_courier_name by name, not id (see
-- above), so removing/renaming a row here never touches historical orders.
CREATE TABLE collection_courier_options (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    active      BOOLEAN NOT NULL DEFAULT true,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO collection_courier_options (name, active, sort_order) VALUES
    ('Customer', true, 0),
    ('UKI', true, 1),
    ('InXpress', true, 2),
    ('Seabridge', true, 3);
