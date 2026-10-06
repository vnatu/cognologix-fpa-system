package com.cognologix.fpa.contracts.domain;

import com.cognologix.fpa.contracts.ContractStatus;
import com.cognologix.fpa.contracts.PaperType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "contract")
@Getter
@Setter
@NoArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_number", nullable = false, unique = true, length = 20)
    private String contractNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_type_id", nullable = false)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "paper_type", nullable = false, length = 15)
    private PaperType paperType;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "party_name", length = 500)
    private String partyName;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "is_evergreen", nullable = false)
    private boolean evergreen = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "parent_contract_id")
    private UUID parentContractId;

    @Column(name = "contract_value", precision = 14, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "billing_currency", length = 3)
    private String billingCurrency;

    @Column(name = "payment_terms", length = 255)
    private String paymentTerms;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "reminder_days_override", columnDefinition = "integer[]")
    private Integer[] reminderDaysOverride;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
