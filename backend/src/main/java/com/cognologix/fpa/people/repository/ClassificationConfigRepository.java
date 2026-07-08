package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.ClassificationConfig;
import com.cognologix.fpa.people.domain.ClassificationConfigType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassificationConfigRepository extends JpaRepository<ClassificationConfig, UUID> {

    List<ClassificationConfig> findByConfigType(ClassificationConfigType configType);
}
