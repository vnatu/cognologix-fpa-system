package com.cognologix.fpa.revenue.repository;

import com.cognologix.fpa.revenue.domain.RevenueCreditNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RevenueCreditNoteRepository extends JpaRepository<RevenueCreditNote, UUID> {

    List<RevenueCreditNote> findByRevenueUploadId(UUID revenueUploadId);

    @Query("""
            SELECT c FROM RevenueCreditNote c
            WHERE c.revenueUpload.id IN :uploadIds
              AND (:customerId IS NULL OR c.customerId = :customerId)
              AND (:periodMonth IS NULL OR c.periodMonth = :periodMonth)
              AND (:periodYear IS NULL OR c.periodYear = :periodYear)
              AND (:status IS NULL OR LOWER(c.status) = LOWER(:status))
            """)
    Page<RevenueCreditNote> findByActiveUploadsFiltered(
            @Param("uploadIds") List<UUID> uploadIds,
            @Param("customerId") String customerId,
            @Param("periodMonth") Integer periodMonth,
            @Param("periodYear") Integer periodYear,
            @Param("status") String status,
            Pageable pageable);
}
