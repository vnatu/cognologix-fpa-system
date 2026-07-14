package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.ImportColumnMapping;
import com.cognologix.fpa.people.domain.ImportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportColumnMappingRepository extends JpaRepository<ImportColumnMapping, UUID> {

    List<ImportColumnMapping> findByImportType(ImportType importType);

    Optional<ImportColumnMapping> findByImportTypeAndActiveTrue(ImportType importType);

    List<ImportColumnMapping> findByActiveTrue();
}
