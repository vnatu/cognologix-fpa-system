package com.cognologix.fpa.expenses.repository;

import com.cognologix.fpa.expenses.domain.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    List<ExpenseCategory> findByActiveTrueOrderBySortOrderAsc();

    List<ExpenseCategory> findAllByOrderBySortOrderAsc();

    Optional<ExpenseCategory> findByLineCodeIgnoreCase(String lineCode);

    boolean existsByLineCodeIgnoreCase(String lineCode);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM ExpenseCategory c")
    int findMaxSortOrder();
}
