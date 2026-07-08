package com.cognologix.fpa.customer.repository;

import com.cognologix.fpa.customer.domain.CommercialTerms;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommercialTermsRepository extends JpaRepository<CommercialTerms, UUID> {
}
