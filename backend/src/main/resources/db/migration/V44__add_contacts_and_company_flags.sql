-- Company-level flags imported from OrderWise, plus a permanent Contacts
-- feature (people at a company, reusable when opening a support ticket).
-- account_number is the OrderWise "Account number" code, kept as the match
-- key for the future bulk company/contact importer and any later Shopify
-- sync - not currently shown/edited anywhere else in the UI.

ALTER TABLE companies ADD COLUMN account_number VARCHAR(45);
ALTER TABLE companies ADD COLUMN on_hold BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN do_not_use BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN gaps BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN gdms BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_companies_account_number ON companies (account_number);

CREATE TABLE contacts (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies (id),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(45),
    position VARCHAR(255),
    main_contact BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Deliberately added now, ahead of the Shopify B2B sync feature itself
    -- (explicitly deferred), so that later work needs no further schema
    -- change: once a contact has been invited, shopify_customer_id and
    -- invited_to_shopify_at get set and stay set.
    shopify_customer_id VARCHAR(255),
    invited_to_shopify_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_contacts_company_id ON contacts (company_id);

ALTER TABLE tickets ADD COLUMN contact_id BIGINT REFERENCES contacts (id) ON DELETE SET NULL;
CREATE INDEX idx_tickets_contact_id ON tickets (contact_id);
