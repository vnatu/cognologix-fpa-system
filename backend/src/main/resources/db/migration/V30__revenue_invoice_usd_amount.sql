-- ADR-061: optional raw USD amount on revenue invoices/credit notes (informational only).

ALTER TABLE revenue_invoice
    ADD COLUMN amount_usd NUMERIC(14,2);

ALTER TABLE revenue_credit_note
    ADD COLUMN amount_usd NUMERIC(14,2);
