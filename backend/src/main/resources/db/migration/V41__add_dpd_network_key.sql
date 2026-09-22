-- Stores the DPD service (networkKey) picked from the live service dropdown
-- at release-for-despatch time, e.g. "1^12" - so despatch uses exactly the
-- service the sales/order screen showed the customer, rather than
-- re-resolving it from scratch (which could pick a different one if
-- availability changed in between).
ALTER TABLE orders ADD COLUMN dpd_network_key VARCHAR(50);
