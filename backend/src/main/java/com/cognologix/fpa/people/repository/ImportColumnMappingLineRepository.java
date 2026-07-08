package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.domain.ImportColumnMappingLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportColumnMappingLineRepository extends JpaRepository<ImportColumnMappingLine, UUID> {
}
