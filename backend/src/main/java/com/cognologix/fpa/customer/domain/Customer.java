package com.cognologix.fpa.customer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "zoho_books_customer_ref", unique = true, length = 100)
    private String zohoBooksCustomerRef;

    /**
     * Soft reference to People & Payroll's Employee Registry by EmployeeID.
     * No FK — cross-module data access is via in-process service call only (ADR-008).
     * Name is display-only; EmployeeID is the identity key (Module 1 §7 identity-key principle).
     */
    @Column(name = "relationship_owner_employee_id", length = 50)
    private String relationshipOwnerEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private LifecycleStatus lifecycleStatus;

    /** Internal BU codes (MGMT, LDSP, POOL, LND, BEF) — not external billing clients (ADR-029). */
    @Column(name = "is_internal", nullable = false)
    @Builder.Default
    private boolean internal = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CustomerProjectCode> projectCodes = new ArrayList<>();

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private CommercialTerms commercialTerms;

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
}
