package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "import_column_mapping_line",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mapping_id", "system_attribute"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportColumnMappingLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private ImportColumnMapping mapping;

    @Column(name = "excel_column_name", nullable = false)
    private String excelColumnName;

    @Column(name = "system_attribute", nullable = false, length = 100)
    private String systemAttribute;
}
