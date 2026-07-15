package com.cognologix.fpa.revenue.repository;

import com.cognologix.fpa.revenue.domain.RevenueInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RevenueInvoiceRepository extends JpaRepository<RevenueInvoice, UUID> {

    List<RevenueInvoice> findByRevenueUploadId(UUID revenueUploadId);

    @Query("""
            SELECT i FROM RevenueInvoice i
            WHERE i.revenueUpload.id = :uploadId
              AND (:customerId IS NULL OR i.customerId = :customerId)
              AND (:status IS NULL OR LOWER(i.status) = LOWER(:status))
            """)
    Page<RevenueInvoice> findFiltered(
            @Param("uploadId") UUID uploadId,
            @Param("customerId") String customerId,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            SELECT i FROM RevenueInvoice i
            WHERE i.revenueUpload.id IN :uploadIds
              AND (:customerId IS NULL OR i.customerId = :customerId)
              AND (:periodMonth IS NULL OR i.periodMonth = :periodMonth)
              AND (:periodYear IS NULL OR i.periodYear = :periodYear)
              AND (:status IS NULL OR LOWER(i.status) = LOWER(:status))
            """)
    Page<RevenueInvoice> findByActiveUploadsFiltered(
            @Param("uploadIds") List<UUID> uploadIds,
            @Param("customerId") String customerId,
            @Param("periodMonth") Integer periodMonth,
            @Param("periodYear") Integer periodYear,
            @Param("status") String status,
            Pageable pageable);
}
