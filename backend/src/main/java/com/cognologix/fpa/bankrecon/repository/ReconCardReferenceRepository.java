package com.cognologix.fpa.bankrecon.repository;

import com.cognologix.fpa.bankrecon.domain.ReconCardReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReconCardReferenceRepository extends JpaRepository<ReconCardReference, String> {

    List<ReconCardReference> findByBankReferenceIn(Collection<String> bankReferences);
}
