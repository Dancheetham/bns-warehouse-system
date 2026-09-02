ALTER TABLE products
    ADD COLUMN default_location_id BIGINT REFERENCES locations(id);
