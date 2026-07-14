package com.cognologix.fpa.people.repository;

import com.cognologix.fpa.people.EmployeeRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRegistryRepository extends JpaRepository<EmployeeRegistry, UUID> {

    Optional<EmployeeRegistry> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<EmployeeRegistry> findByLastUpdatedByUpload_IdOrderByEmployeeIdAsc(UUID uploadId);
}
