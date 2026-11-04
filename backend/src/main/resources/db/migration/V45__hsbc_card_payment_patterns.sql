-- ADR-075 addendum. Card payments also arrive as PAYMENT - THANK YOU.
-- Leave a pattern Finance has already changed.

UPDATE general_config
SET config_value = 'CREDIT CARD PAYMENT,PAYMENT - THANK YOU'
WHERE config_key = 'hsbc_card_payment_pattern'
  AND config_value = 'CREDIT CARD PAYMENT';
