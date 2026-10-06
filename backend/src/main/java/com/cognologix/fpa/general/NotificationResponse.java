package com.cognologix.fpa.general;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String message,
        String link,
        Instant createdAt
) {}
