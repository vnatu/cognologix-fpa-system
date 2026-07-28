package com.cognologix.fpa.expenses.repository;

import com.cognologix.fpa.expenses.domain.ExpenseActual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseActualRepository extends JpaRepository<ExpenseActual, UUID> {

    @Query("""
            SELECT a FROM ExpenseActual a
            JOIN FETCH a.expenseCategory
            WHERE a.expenseMonth = :month AND a.expenseYear = :year
            """)
    List<ExpenseActual> findByMonthAndYearWithCategory(int month, int year);

    Optional<ExpenseActual> findByExpenseMonthAndExpenseYearAndExpenseCategoryId(
            int expenseMonth, int expenseYear, UUID expenseCategoryId);

    List<ExpenseActual> findByExpenseMonthAndExpenseYear(int expenseMonth, int expenseYear);

    @Query("""
            SELECT a.expenseYear, a.expenseMonth, SUM(a.amount)
            FROM ExpenseActual a
            GROUP BY a.expenseYear, a.expenseMonth
            ORDER BY a.expenseYear DESC, a.expenseMonth DESC
            """)
    List<Object[]> findMonthYearTotals();
}
