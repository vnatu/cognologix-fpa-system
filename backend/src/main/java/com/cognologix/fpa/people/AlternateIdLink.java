package com.cognologix.fpa.people;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent alternate payroll ID → EmployeeID mapping (Module 1 §7.3).
 * Public API surface — other modules may reference EmployeeRegistry via this link.
 */
@Entity
@Table(name = "alternate_id_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlternateIdLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_registry_id", nullable = false)
    private EmployeeRegistry employeeRegistry;

    @Column(name = "alternate_employee_no", nullable = false, unique = true, length = 100)
    private String alternateEmployeeNo;

    @Column(name = "mapped_by", nullable = false)
    private String mappedBy;

    @Column(name = "mapped_at", nullable = false, updatable = false)
    private Instant mappedAt;

    @PrePersist
    private void prePersist() {
        mappedAt = Instant.now();
    }
}
