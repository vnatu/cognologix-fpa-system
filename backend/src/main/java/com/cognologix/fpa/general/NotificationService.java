package com.cognologix.fpa.general;

import com.cognologix.fpa.general.domain.AppNotification;
import com.cognologix.fpa.general.repository.AppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.cognologix.fpa.general.BackupGridHelper.cell;
import static com.cognologix.fpa.general.BackupGridHelper.parseBoolean;
import static com.cognologix.fpa.general.BackupGridHelper.parseInstant;
import static com.cognologix.fpa.general.BackupGridHelper.requireCell;
import static com.cognologix.fpa.general.BackupGridHelper.row;
import static com.cognologix.fpa.general.BackupGridHelper.str;

@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String BACKUP_FILE = "app_notifications.xlsx";

    private static final String[] BACKUP_HEADERS = {
            "id", "user_id", "user_email", "title", "message", "link", "is_read", "created_at"
    };

    private final AppNotificationRepository appNotificationRepository;
    private final UserService userService;

    @Transactional
    public void notifyUser(UUID userId, String title, String message, String link) {
        var notification = new AppNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        appNotificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> unreadFor(UUID userId) {
        return appNotificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        AppNotification notification = appNotificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        appNotificationRepository.markAllRead(userId);
    }

    @Transactional
    public void wipeForRestore() {
        appNotificationRepository.deleteAllInBatch();
    }

    public BackupSheet exportBackupSheet() {
        List<String[]> rows = new ArrayList<>();
        for (AppNotification notification : appNotificationRepository.findAll()) {
            rows.add(row(
                    str(notification.getId()),
                    str(notification.getUserId()),
                    userEmail(notification.getUserId()),
                    notification.getTitle(),
                    notification.getMessage(),
                    str(notification.getLink()),
                    String.valueOf(notification.isRead()),
                    str(notification.getCreatedAt())));
        }
        return new BackupSheet(BACKUP_FILE, BACKUP_HEADERS, rows);
    }

    @Transactional
    public int restoreBackupSheet(List<String[]> rows) {
        int count = 0;
        for (String[] cells : rows) {
            var notification = new AppNotification();
            notification.setId(UUID.fromString(requireCell(cells, 0, "id")));
            notification.setUserId(resolveUser(cells));
            notification.setTitle(requireCell(cells, 3, "title"));
            notification.setMessage(requireCell(cells, 4, "message"));
            notification.setLink(cell(cells, 5));
            notification.setRead(parseBoolean(cell(cells, 6)));
            var created = parseInstant(cell(cells, 7), "created_at");
            notification.setCreatedAt(created == null ? Instant.now() : created);
            appNotificationRepository.save(notification);
            count++;
        }
        return count;
    }

    private UUID resolveUser(String[] cells) {
        UUID userId = UUID.fromString(requireCell(cells, 1, "user_id"));
        try {
            userService.requireById(userId);
            return userId;
        } catch (GeneralBadRequestException ex) {
            String email = cell(cells, 2);
            if (email == null) {
                throw new GeneralBadRequestException("Notification user not found during restore");
            }
            return userService.findByEmail(email)
                    .map(AppUser::getId)
                    .orElseThrow(() -> new GeneralBadRequestException("Notification user not found during restore"));
        }
    }

    private String userEmail(UUID userId) {
        try {
            return userService.requireById(userId).getEmail();
        } catch (GeneralBadRequestException ex) {
            return "";
        }
    }

    private static NotificationResponse toResponse(AppNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.getCreatedAt());
    }
}
