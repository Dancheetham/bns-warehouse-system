-- Payment Tracking: per-company payment terms/auto-hold, a credit balance
-- wallet (overpayments and RMA credits that aren't auto-applied to a
-- specific invoice), invoice-level paid/chaser/auto-hold bookkeeping, and
-- moving Payments to link against an Invoice (multiple orders can now share
-- one consolidated invoice, so "which order does this payment belong to"
-- stopped being a well-defined question - see InvoiceService/Company.invoiceGrouping).

ALTER TABLE companies ADD COLUMN payment_terms_days INTEGER;
ALTER TABLE companies ADD COLUMN auto_hold_on_overdue BOOLEAN NOT NULL DEFAULT FALSE;
-- True only while the company's current on_hold=true was set BY THE
-- SCHEDULED JOB (not by a member of staff) - lets the job auto-clear the
-- hold once every overdue invoice is cleared, while never touching a hold a
-- human set or removed by hand. See PaymentChaserService.
ALTER TABLE companies ADD COLUMN auto_held BOOLEAN NOT NULL DEFAULT FALSE;
-- Unapplied credit - built up from overpayments and from credit notes that
-- weren't auto-applied to a specific RMA replacement invoice. Can be applied
-- by staff to any invoice from Payment Tracking. Counted as available credit
-- immediately (see CompanyService.creditUsed), which is how a customer can
-- end up with more available credit than their raw credit limit once they've
-- effectively paid ahead.
ALTER TABLE companies ADD COLUMN credit_balance NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE invoices ADD COLUMN paid_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN chaser_warning_sent_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN chaser_overdue_sent_at TIMESTAMP;
-- Set the one time this invoice caused its company to be auto-held, so the
-- job never re-fires for the same invoice even if staff take the company
-- back off hold while it's still outstanding.
ALTER TABLE invoices ADD COLUMN auto_hold_triggered BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payments ADD COLUMN invoice_id BIGINT REFERENCES invoices(id);
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
-- order_id was NOT NULL until now - a Payment Tracking payment links to an
-- invoice instead (see invoice_id/order_id comments on the Payment entity).
ALTER TABLE payments ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE payments ADD COLUMN from_credit_balance BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('payment_terms_days', '30'),
    ('payment_terms_warning_days', '5'),
    ('chaser_warning_subject', 'Payment reminder - Invoice {invoiceNumber} due in {daysRemaining} days'),
    ('chaser_warning_body', 'Hi,

This is a friendly reminder that Invoice {invoiceNumber} for {amount} (dated {invoiceDate}) is due for payment in {daysRemaining} days, in line with your agreed payment terms.

If this has already been paid, please disregard this email. If you have any queries about this invoice, please contact finance@bnsdistribution.co.uk.

Kind regards,
BNS Distribution UK Ltd'),
    ('chaser_overdue_subject', 'Overdue - Invoice {invoiceNumber} is now past payment terms'),
    ('chaser_overdue_body', 'Hi,

Invoice {invoiceNumber} for {amount} (dated {invoiceDate}) has now passed your agreed payment terms and remains outstanding.

Please arrange payment as soon as possible. If this has already been paid, please disregard this email. If you have any queries about this invoice, please contact finance@bnsdistribution.co.uk.

Kind regards,
BNS Distribution UK Ltd');
