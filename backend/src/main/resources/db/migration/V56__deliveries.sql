-- One row per actual despatch confirmation (DespatchService.confirmDespatch) -
-- i.e. one row per real courier consignment, not per order. An order
-- despatched in two separate trips (e.g. partially despatched, then the rest
-- later) gets two rows here, each with its own human-readable delivery_number
-- so staff can track/search by the specific consignment rather than only by
-- order number. See Delivery.java.
--
-- Deliberately NOT tagged onto individual StockItems/Cartons - Dan's call
-- (2026-10-02): Delivery History only needs to know a delivery happened and
-- what it was (date, courier, consignment, partial or not), not a full
-- per-MAC/serial breakdown per delivery. The item/carton breakdown on the
-- Delivery History detail page stays combined across the whole order, as
-- before.
--
-- Rows are deleted outright by OrderReversalService if the order they belong
-- to is later reversed (Reverse to Despatch / Cancel & Return to Stock) -
-- in practice that only happens pre-collection/testing, since both actions
-- are blocked once anything on the order has actually been invoiced, so a
-- row surviving in this table always represents a delivery that genuinely,
-- irreversibly went out.
CREATE TABLE deliveries (
    id                     BIGSERIAL PRIMARY KEY,
    delivery_number        VARCHAR(20) NOT NULL,
    order_id               BIGINT NOT NULL REFERENCES orders(id),
    despatched_at          TIMESTAMP NOT NULL,
    partial                BOOLEAN NOT NULL DEFAULT FALSE,
    courier_type           VARCHAR(20),
    courier_method         VARCHAR(255),
    collection_courier_name VARCHAR(255),
    consignment_number     VARCHAR(255),
    shipping_cost          NUMERIC(12, 2),
    created_at             TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_deliveries_delivery_number ON deliveries(delivery_number);
CREATE INDEX idx_deliveries_order_id ON deliveries(order_id);
