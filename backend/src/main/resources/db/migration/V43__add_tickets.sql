-- Support tickets - phone/email enquiries, optionally linked to a Company
-- and/or a specific Order (e.g. "SO-10019 hasn't turned up"), each with a
-- dated, user-attributed timeline of notes (ticket_entries) rather than one
-- free-text field, so a ticket built up over several calls stays readable.
--
-- ticket_number is generated from a real Postgres sequence rather than the
-- count()+1/existsBy retry loop Order.generateOrderNumber() uses - that's
-- fine for orders, created one at a time from the UI, but not safe enough
-- here: two tickets can genuinely be opened in the same second by two
-- different people answering two different calls. nextval() is atomic under
-- concurrent transactions, no retry/locking needed.
CREATE SEQUENCE ticket_number_seq START WITH 10001;

CREATE TABLE tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    caller_name VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(255),
    company_id BIGINT REFERENCES companies(id),
    order_id BIGINT REFERENCES orders(id),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    -- Total minutes on the phone across the ticket's life - manually
    -- editable, shown in the UI split as hours/minutes.
    talk_time_minutes INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE INDEX idx_tickets_company_id ON tickets(company_id);
CREATE INDEX idx_tickets_order_id ON tickets(order_id);

CREATE TABLE ticket_entries (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author VARCHAR(255) NOT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ticket_entries_ticket_id ON ticket_entries(ticket_id);
