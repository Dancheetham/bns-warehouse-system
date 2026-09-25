-- Generate Invoices now bills an order's shipping cost as its own line on
-- the first invoice that touches that order, instead of it only ever being
-- reflected in the pre-invoice credit estimate and then dropping out
-- entirely once the order's product lines are invoiced (see
-- CompanyService.creditUsed's old "known gap" comment). shipping_invoiced
-- is the once-only guard, same pattern as Invoice.autoHoldTriggered - so an
-- order split across more than one invoice generation run never gets its
-- delivery charge billed twice.
ALTER TABLE orders ADD COLUMN shipping_invoiced BOOLEAN NOT NULL DEFAULT FALSE;

-- A shipping InvoiceLine doesn't correspond to a real OrderLine/product, so
-- order_line_id has to allow null for it - order_id is still always set
-- (which order's delivery this is), and the new "shipping" flag marks it
-- out from an ordinary product line for reporting/rendering.
ALTER TABLE invoice_lines ALTER COLUMN order_line_id DROP NOT NULL;
ALTER TABLE invoice_lines ADD COLUMN shipping BOOLEAN NOT NULL DEFAULT FALSE;
