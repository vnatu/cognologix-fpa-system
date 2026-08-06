-- Expense Entry previously stored full-rupee amounts; system unit is Rs Lakhs (ADR-054).
-- Guard: only convert rows clearly in full rupees (> 1000 Lakhs = Rs 10 Crore per line/month is implausible).
UPDATE expense_actual SET amount = amount / 100000 WHERE amount > 1000;

-- Trim whitespace on category groups so casing/spacing orphans can consolidate on next write.
UPDATE expense_category SET category_group = TRIM(category_group);
