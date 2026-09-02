CREATE TABLE rma_requests (
    id BIGSERIAL PRIMARY KEY,
    public_reference VARCHAR(50) NOT NULL UNIQUE,
    rma_number VARCHAR(50) UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',

    customer_name VARCHAR(255) NOT NULL,
    customer_company VARCHAR(255),
    customer_address TEXT,
    contact_name VARCHAR(255),
    contact_phone VARCHAR(100),
    contact_email VARCHAR(255),

    -- Only needed when the RMA includes a faulty item that'll get an advance
    -- replacement shipped out.
    delivery_name VARCHAR(255),
    delivery_town VARCHAR(255),
    delivery_country VARCHAR(255),
    delivery_postcode VARCHAR(100),
    delivery_country_code VARCHAR(10),

    -- Best-effort match to the original sale - may be null if nothing matched
    -- at submission (accepted anyway, flagged for staff to verify by hand).
    original_order_id BIGINT REFERENCES orders(id),
    -- Created on approval, only if the RMA has at least one faulty item - an
    -- ON_HOLD order for the advance replacement, referenced RMA<number>.
    replacement_order_id BIGINT REFERENCES orders(id),
    -- Created on receipt - a single CREDIT_REFUND order covering everything
    -- credited at that point (faulty items priced from the replacement order,
    -- non-faulty items priced from the original sale, less RSF if applied).
    credit_order_id BIGINT REFERENCES orders(id),

    notes TEXT,

    submitted_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP,
    approved_by VARCHAR(255),
    rejected_at TIMESTAMP,
    rejected_by VARCHAR(255),
    rejection_reason TEXT,
    received_at TIMESTAMP,
    received_by VARCHAR(255)
);

CREATE TABLE rma_items (
    id BIGSERIAL PRIMARY KEY,
    rma_request_id BIGINT NOT NULL REFERENCES rma_requests(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),

    identifier VARCHAR(255),
    quantity INTEGER NOT NULL DEFAULT 1,
    faulty BOOLEAN NOT NULL DEFAULT FALSE,
    grandstream_ticket_number VARCHAR(100),
    reason_for_return TEXT,

    -- Populated by the live lookup at submission time (server-side, not trusted
    -- from the client) - null if the identifier didn't match anything.
    matched_order_id BIGINT REFERENCES orders(id),
    matched_order_line_id BIGINT REFERENCES order_lines(id),
    matched_unit_price NUMERIC(12,2),
    -- BNS's own 1-year return-to-base warranty - computed from the matched
    -- order's date, not Grandstream's (that one still needs a human to check
    -- their portal, since there's no API for it).
    rtb_warranty_expires_at DATE,
    grandstream_warranty_checked BOOLEAN NOT NULL DEFAULT FALSE,

    received BOOLEAN NOT NULL DEFAULT FALSE,
    rsf_applied BOOLEAN NOT NULL DEFAULT FALSE,
    credited BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_rma_items_request ON rma_items (rma_request_id);
CREATE INDEX idx_rma_requests_status ON rma_requests (status);
