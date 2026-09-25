-- Set once, the first time an order is actually despatched (confirmDespatch),
-- and never cleared again afterwards (not even by Reverse to Despatch) - a
-- stable marker of "this order has genuinely gone out at least once" and its
-- despatch date, independent of the dpd_* fields which DO get cleared on a
-- reversal. Backs the new Delivery History page (Sales).
ALTER TABLE orders ADD COLUMN despatched_at TIMESTAMP;

-- Backfill for orders that have already despatched under the old schema -
-- dpd_shipped_at is the closest existing signal for DPD-booked shipments;
-- for anything despatched without DPD (or where dpd_shipped_at was cleared
-- by an old reversal, since that field is still reset by
-- OrderReversalService), fall back to order_date so it still shows up on
-- Delivery History with a reasonable date rather than being invisible
-- because the column happens to be null.
UPDATE orders
SET despatched_at = COALESCE(dpd_shipped_at, order_date)
WHERE status IN ('COMPLETED', 'PARTIALLY_DESPATCHED', 'INVOICE_PENDING');
