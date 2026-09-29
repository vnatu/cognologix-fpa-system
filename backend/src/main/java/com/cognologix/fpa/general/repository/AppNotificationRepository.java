package com.cognologix.fpa.general.repository;

import com.cognologix.fpa.general.domain.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("update AppNotification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(UUID userId);
}
