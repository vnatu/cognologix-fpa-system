package com.cognologix.fpa.revenue.repository;

import com.cognologix.fpa.revenue.domain.RevenueImportType;
import com.cognologix.fpa.revenue.domain.RevenueUpload;
import com.cognologix.fpa.revenue.domain.RevenueUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RevenueUploadRepository extends JpaRepository<RevenueUpload, UUID> {

    Optional<RevenueUpload> findByImportTypeAndPeriodMonthAndPeriodYearAndStatus(
            RevenueImportType importType, int periodMonth, int periodYear, RevenueUploadStatus status);

    List<RevenueUpload> findByPeriodMonthAndPeriodYearOrderByUploadedAtDesc(
            int periodMonth, int periodYear);

    Optional<RevenueUpload> findFirstByImportTypeAndPeriodMonthAndPeriodYearOrderByVersionNumberDesc(
            RevenueImportType importType, int periodMonth, int periodYear);
}
