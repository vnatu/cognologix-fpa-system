package com.cognologix.fpa.expenses.repository;

import com.cognologix.fpa.expenses.domain.ExpenseMonthLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseMonthLockRepository extends JpaRepository<ExpenseMonthLock, UUID> {

    Optional<ExpenseMonthLock> findByExpenseMonthAndExpenseYear(int expenseMonth, int expenseYear);
}
