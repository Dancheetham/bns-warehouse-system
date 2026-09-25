-- History of superseded shipments on an order, written when an order that's
-- already despatched at least once is reopened for an extra shipment (see
-- Shipment.java / OrderService.update()). No backfill needed - this only
-- starts recording from the first reopen that happens after this version
-- ships; before it, the "extra shipment" flow didn't exist at all.
CREATE TABLE shipments (
    id                     BIGSERIAL PRIMARY KEY,
    order_id               BIGINT NOT NULL REFERENCES orders(id),
    shipped_at             TIMESTAMP,
    courier_method         VARCHAR(255),
    dpd_network_key        VARCHAR(255),
    dpd_shipment_id        VARCHAR(255),
    dpd_consignment_number VARCHAR(255),
    dpd_parcel_numbers     VARCHAR(1000),
    shipping_cost          NUMERIC(12, 2),
    created_at             TIMESTAMP NOT NULL
);

CREATE INDEX idx_shipments_order_id ON shipments(order_id);
