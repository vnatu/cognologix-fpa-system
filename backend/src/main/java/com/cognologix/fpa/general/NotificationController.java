package com.cognologix.fpa.general;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Unread notifications for the current user")
    public List<NotificationResponse> unread(Authentication auth) {
        return notificationService.unreadFor(currentUserId(auth));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications read for the current user")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        notificationService.markAllRead(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth) {
        notificationService.markRead(id, currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new GeneralBadRequestException("Authenticated user is required");
        }
        return userService.findByEmail(auth.getName())
                .orElseThrow(() -> new GeneralBadRequestException("User not found"))
                .getId();
    }
}
