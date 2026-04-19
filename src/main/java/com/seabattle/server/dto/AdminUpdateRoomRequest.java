package com.seabattle.server.dto;

public record AdminUpdateRoomRequest(
        String status,
        String expiresAt
) {
}
