package com.cognologix.fpa.expenses.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expense_month_lock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"expense_month", "expense_year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseMonthLock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expense_month", nullable = false)
    private Integer expenseMonth;

    @Column(name = "expense_year", nullable = false)
    private Integer expenseYear;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @Column(name = "unlocked_by")
    private String unlockedBy;

    @Column(name = "unlock_reason", columnDefinition = "TEXT")
    private String unlockReason;

    @PrePersist
    private void prePersist() {
        if (lockedAt == null) {
            lockedAt = Instant.now();
        }
    }

    public boolean isCurrentlyLocked() {
        return unlockedAt == null;
    }
}
