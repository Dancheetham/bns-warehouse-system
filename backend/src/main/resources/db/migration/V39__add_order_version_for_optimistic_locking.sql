-- Optimistic locking for order edits - two people opening the same order
-- and both saving shouldn't silently let the second save overwrite the
-- first's changes. Every save has to say "I'm saving based on version N";
-- if someone else has already saved since, that number's moved on and the
-- second save is rejected with a clear "changed since you loaded it, please
-- reload" error instead - never a lock that can get stuck if someone's
-- session just dies mid-edit.
ALTER TABLE orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
