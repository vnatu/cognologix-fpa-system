package com.cognologix.fpa.revenue.repository;

import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueInvoice;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            SELECT i FROM RevenueInvoice i
            JOIN i.revenueUpload u
            WHERE i.periodMonth = :periodMonth
              AND i.periodYear = :periodYear
              AND u.status = :status
            """)
    List<RevenueInvoice> findOnActiveUploadsByEffectivePeriod(
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT COUNT(i) FROM RevenueInvoice i
            JOIN i.revenueUpload u
            WHERE i.periodMonth = :periodMonth
              AND i.periodYear = :periodYear
              AND u.status = :status
            """)
    long countOnActiveUploadsByEffectivePeriod(
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT DISTINCT i.periodMonth, i.periodYear
            FROM RevenueInvoice i
            JOIN i.revenueUpload u
            WHERE u.status = :status
            """)
    List<Object[]> findEffectivePeriodsOnActiveUploads(@Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT i FROM RevenueInvoice i
            JOIN FETCH i.revenueUpload u
            WHERE u.status = :status
              AND u.importType = :importType
              AND i.invoiceNumber IN :numbers
              AND (u.periodMonth <> :periodMonth OR u.periodYear <> :periodYear)
            """)
    List<RevenueInvoice> findOnOtherActivePeriods(
            @Param("importType") RevenueImportType importType,
            @Param("status") RevenueUploadStatus status,
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("numbers") Collection<String> numbers);
}
