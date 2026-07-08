package com.cognologix.fpa.people.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "classification_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"config_type", "value"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false, length = 20)
    private ClassificationConfigType configType;

    @Column(name = "value", nullable = false)
    private String value;
}
