package com.cognologix.fpa.revenue.repository;

import com.cognologix.fpa.revenue.domain.RevenueCreditNote;
import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @Query("""
            SELECT c FROM RevenueCreditNote c
            JOIN c.revenueUpload u
            WHERE c.periodMonth = :periodMonth
              AND c.periodYear = :periodYear
              AND u.status = :status
            """)
    List<RevenueCreditNote> findOnActiveUploadsByEffectivePeriod(
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT COUNT(c) FROM RevenueCreditNote c
            JOIN c.revenueUpload u
            WHERE c.periodMonth = :periodMonth
              AND c.periodYear = :periodYear
              AND u.status = :status
            """)
    long countOnActiveUploadsByEffectivePeriod(
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT DISTINCT c.periodMonth, c.periodYear
            FROM RevenueCreditNote c
            JOIN c.revenueUpload u
            WHERE u.status = :status
            """)
    List<Object[]> findEffectivePeriodsOnActiveUploads(@Param("status") RevenueUploadStatus status);

    @Query("""
            SELECT c FROM RevenueCreditNote c
            JOIN FETCH c.revenueUpload u
            WHERE u.status = :status
              AND u.importType = :importType
              AND c.creditNoteNumber IN :numbers
              AND (u.periodMonth <> :periodMonth OR u.periodYear <> :periodYear)
            """)
    List<RevenueCreditNote> findOnOtherActivePeriods(
            @Param("importType") RevenueImportType importType,
            @Param("status") RevenueUploadStatus status,
            @Param("periodMonth") int periodMonth,
            @Param("periodYear") int periodYear,
            @Param("numbers") Collection<String> numbers);
}
