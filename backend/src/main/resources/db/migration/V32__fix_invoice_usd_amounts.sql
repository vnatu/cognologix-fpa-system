-- Clear incorrectly populated amount_usd for INR invoices/credit notes.
-- amount_usd is only valid when AmountUsd was explicitly mapped (typically USD-billed rows).

UPDATE revenue_invoice SET amount_usd = NULL WHERE currency = 'INR';
UPDATE revenue_credit_note SET amount_usd = NULL WHERE currency = 'INR';
