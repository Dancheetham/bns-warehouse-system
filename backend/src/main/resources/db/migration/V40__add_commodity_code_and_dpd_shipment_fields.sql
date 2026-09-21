-- Prerequisite for DPD shipping: DPD's customs declaration for parcels
-- travelling to Ireland needs an 8-digit HS/commodity code and country of
-- origin per product line. country_of_origin defaults to GB since that's
-- true for almost everything in the catalogue.
ALTER TABLE products
    ADD COLUMN commodity_code VARCHAR(20),
    ADD COLUMN country_of_origin VARCHAR(2) NOT NULL DEFAULT 'GB';

-- Where the result of a successful DPD shipment booking is recorded against
-- the order it was booked for - shipment_id is DPD's own shipment UUID,
-- consignment_number/parcel_numbers are what gets shown to staff and
-- customers for tracking, and shipped_at records when the booking happened
-- (distinct from despatch time, which is a separate warehouse-process step).
ALTER TABLE orders
    ADD COLUMN dpd_shipment_id VARCHAR(64),
    ADD COLUMN dpd_consignment_number VARCHAR(32),
    ADD COLUMN dpd_parcel_numbers VARCHAR(500),
    ADD COLUMN dpd_shipped_at TIMESTAMP;

-- A street address and phone number were never captured on the order before -
-- fine while delivery labels/addresses were only ever handled outside this
-- system, but DPD's API requires a street address for every shipment (and a
-- phone number is strongly recommended for delivery updates/OTP-on-delivery).
ALTER TABLE orders
    ADD COLUMN delivery_address_line1 VARCHAR(255),
    ADD COLUMN delivery_address_line2 VARCHAR(255),
    ADD COLUMN delivery_phone VARCHAR(50);
