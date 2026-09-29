-- GDMS end-of-day channel assignment. A company already has a plain `gdms`
-- boolean (V44) but nothing recording WHICH GDMS "channel" (reseller
-- account under BNS in GDMS's own hierarchy) its despatched devices should
-- be assigned to - that's what gdms_channel_id/gdms_channel_name add here.
-- Both nullable: gdms=true with no channel set is a valid, visible "not
-- finished setting this company up yet" state rather than an error.
ALTER TABLE companies ADD COLUMN gdms_channel_id VARCHAR(45);
ALTER TABLE companies ADD COLUMN gdms_channel_name VARCHAR(255);

-- Marks a despatched StockItem as already pushed to GDMS, so the end-of-day
-- job (and the per-order manual re-run) only ever sends a given MAC once -
-- GDMS itself doesn't need to reject a duplicate /assign call for this to
-- matter, it's really about not re-sending the same hundreds of MACs to the
-- API every single night forever.
ALTER TABLE stock_items ADD COLUMN gdms_synced_at TIMESTAMP;
