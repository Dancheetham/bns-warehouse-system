-- Links a carton to the specific Delivery it went out on, so the packing
-- screen can tell "already despatched on an earlier delivery for this
-- order" apart from "currently being packed". Null until the despatch that
-- carton belongs to is actually confirmed (DespatchService.confirmDespatch) -
-- see PackingService, which now excludes any carton with this set.
ALTER TABLE cartons ADD COLUMN delivery_id BIGINT NULL REFERENCES deliveries(id);
CREATE INDEX idx_cartons_delivery_id ON cartons(delivery_id);
