package com.cognologix.fpa.people;

import com.cognologix.fpa.people.domain.ExitDatePrecision;
import com.cognologix.fpa.people.domain.ExitStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persistent employee identity — public API surface of the People module (Module 1 §7.1).
 * EmployeeID is the sole unique key; name is display-only.
 */
@Entity
@Table(name = "employee_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false, unique = true, length = 100)
    private String employeeId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_status", nullable = false, length = 10)
    @Builder.Default
    private ExitStatus exitStatus = ExitStatus.ACTIVE;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_date_precision", length = 12)
    private ExitDatePrecision exitDatePrecision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
}
