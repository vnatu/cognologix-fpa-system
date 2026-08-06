-- Seed Other Administrative Expenses categories
INSERT INTO expense_category (line_code, category_group, display_name, sort_order, is_active)
VALUES
    ('housekeeping_expense', 'Other Administrative Expenses', 'Housekeeping Expense', 25, true),
    ('office_expenses', 'Other Administrative Expenses', 'Office Expenses', 26, true);
