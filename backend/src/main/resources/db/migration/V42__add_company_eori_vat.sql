-- BNS ships as a distributor on behalf of the companies it invoices, so for
-- customs declarations (currently Ireland) the DPD "importer of record" is
-- the order's Company, not BNS itself - see DpdShippingService for the full
-- reasoning on why exporterDetails stays Settings-based while importerDetails
-- needs to come from here. EORI is mandatory (and VAT commonly required) for
-- B2B customs clearance, confirmed from a real DPD commercial invoice.
ALTER TABLE companies ADD COLUMN eori_number VARCHAR(45);
ALTER TABLE companies ADD COLUMN vat_number VARCHAR(45);
