-- Settings' setting_value was capped at VARCHAR(500) (V23) - fine for the
-- original handful of short config strings, but DpdShippingService/
-- ApcShippingService's "available services" live-lookup cache (added
-- pre-v0.135) stores a growing JSON array of every service code+description
-- ever seen, which easily exceeds 500 characters once a few postcodes'
-- worth of services accumulate. This is the actual cause of Dan's "the
-- sweep runs with no errors now, but the list still isn't updating" report
-- (2026-10-01, v0.139's fix): cacheLastKnownServices() wraps the save in a
-- try/catch that only logs a server-side warning, so Postgres's own
-- "value too long for type character varying(500)" exception on the save
-- was being silently swallowed - no error ever reached the sweep's own
-- warnings list or the frontend. Widened to TEXT so no setting value can
-- ever hit a silent ceiling like this again, for this or any future use of
-- the settings table.
ALTER TABLE app_settings ALTER COLUMN setting_value TYPE TEXT;

-- Replaces the JSON-blob-in-a-setting approach entirely for courier
-- available services (dpd_last_known_services/apc_last_known_services +
-- dpd_disabled_services/apc_disabled_services) with a real table, per Dan's
-- request - one row per courier+code, `enabled` replacing the separate
-- disabled-codes CSV setting. Besides fixing the overflow above at the root
-- (a real column per field, not a size-capped blob to serialize a growing
-- list into), this also means a single code's enabled flag can be flipped
-- without rewriting the whole list, and the data is queryable/inspectable
-- directly in the database rather than opaque JSON text.
--
-- `label`/`extra_label` cover both couriers' shapes: DPD's networkDesc goes
-- in `label` and serviceDesc in `extra_label` (null for APC, which has only
-- one description field).
-- label/extra_label are TEXT, not a capped VARCHAR, deliberately - that's
-- exactly the mistake this table exists to stop repeating (see above).
CREATE TABLE courier_service_options (
    id BIGSERIAL PRIMARY KEY,
    courier VARCHAR(10) NOT NULL,
    code VARCHAR(50) NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    extra_label TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (courier, code)
);

-- Best-effort carry-over of whatever's already cached, so known services
-- aren't lost outright by this change - wrapped so a malformed/truncated
-- existing value (exactly the kind of value this migration exists to stop
-- happening again) can't fail the migration itself, just skip that courier
-- and start it fresh from the next live lookup/sweep instead.
DO $$
DECLARE
    raw_services TEXT;
    raw_disabled TEXT;
    disabled_codes TEXT[];
BEGIN
    -- DPD
    SELECT setting_value INTO raw_services FROM app_settings WHERE setting_key = 'dpd_last_known_services';
    SELECT setting_value INTO raw_disabled FROM app_settings WHERE setting_key = 'dpd_disabled_services';
    SELECT COALESCE(array_agg(trim(x)), ARRAY[]::TEXT[]) INTO disabled_codes
        FROM unnest(CASE WHEN raw_disabled IS NOT NULL AND raw_disabled <> ''
            THEN string_to_array(raw_disabled, ',') ELSE ARRAY[]::TEXT[] END) AS x;
    IF raw_services IS NOT NULL AND raw_services <> '' THEN
        BEGIN
            INSERT INTO courier_service_options (courier, code, label, extra_label, enabled)
            SELECT 'DPD',
                   item->>'networkKey',
                   COALESCE(item->>'networkDesc', ''),
                   item->>'serviceDesc',
                   NOT (trim(item->>'networkKey') = ANY (disabled_codes))
            FROM json_array_elements(raw_services::json) AS item
            WHERE item->>'networkKey' IS NOT NULL
            ON CONFLICT (courier, code) DO NOTHING;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Skipping DPD available-services migration - stored value was not valid/complete JSON (%)', SQLERRM;
        END;
    END IF;

    -- APC
    SELECT setting_value INTO raw_services FROM app_settings WHERE setting_key = 'apc_last_known_services';
    SELECT setting_value INTO raw_disabled FROM app_settings WHERE setting_key = 'apc_disabled_services';
    SELECT COALESCE(array_agg(trim(x)), ARRAY[]::TEXT[]) INTO disabled_codes
        FROM unnest(CASE WHEN raw_disabled IS NOT NULL AND raw_disabled <> ''
            THEN string_to_array(raw_disabled, ',') ELSE ARRAY[]::TEXT[] END) AS x;
    IF raw_services IS NOT NULL AND raw_services <> '' THEN
        BEGIN
            INSERT INTO courier_service_options (courier, code, label, extra_label, enabled)
            SELECT 'APC',
                   item->>'code',
                   COALESCE(item->>'description', ''),
                   NULL,
                   NOT (trim(item->>'code') = ANY (disabled_codes))
            FROM json_array_elements(raw_services::json) AS item
            WHERE item->>'code' IS NOT NULL
            ON CONFLICT (courier, code) DO NOTHING;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Skipping APC available-services migration - stored value was not valid/complete JSON (%)', SQLERRM;
        END;
    END IF;
END $$;

-- Superseded by the table above - removed rather than left behind as dead,
-- confusing leftovers once the Java side no longer reads or writes them.
DELETE FROM app_settings WHERE setting_key IN (
    'dpd_last_known_services', 'apc_last_known_services',
    'dpd_disabled_services', 'apc_disabled_services'
);
