package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "import_column_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportColumnMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 30)
    private ImportType importType;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "mapping", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ImportColumnMappingLine> lines = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
}
