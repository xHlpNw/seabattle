package com.seabattle.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserDto(
        UUID id,
        String username,
        int rating,
        int wins,
        int losses,
        String role,
        String status,
        OffsetDateTime createdAt
) {
}
