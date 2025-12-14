package com.seabattle.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomResponseDTO(
        UUID token,
        String status,
        String hostUsername,
        boolean isHost,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        boolean expired
) {}


