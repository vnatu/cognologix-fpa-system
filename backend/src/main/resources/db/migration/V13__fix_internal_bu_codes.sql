-- Correct Leadership code where V11 mistakenly used LND before LDSP existed.
-- No-op when LDSP is already present (Learning & Development keeps LND).
UPDATE customer
    SET customer_code = 'LDSP'
    WHERE customer_code = 'LND' AND is_internal = true
      AND NOT EXISTS (
          SELECT 1 FROM customer c2 WHERE c2.customer_code = 'LDSP' AND c2.is_internal = true
      );

UPDATE customer
    SET customer_code = 'BEFN', customer_name = 'Business Enabler Functions'
    WHERE customer_code = 'BEF' AND is_internal = true;
