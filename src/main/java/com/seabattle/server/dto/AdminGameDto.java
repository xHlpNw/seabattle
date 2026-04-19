package com.seabattle.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminGameDto(
        UUID id,
        String type,
        String status,
        String hostUsername,
        String guestUsername,
        boolean botGame,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
