package com.seabattle.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRoomDto(
        UUID id,
        UUID token,
        String status,
        String hostUsername,
        String guestUsername,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}
