package com.seabattle.server.dto;

public record AdminStatsDto(
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        long inProgressGames,
        long finishedGames,
        long waitingRooms
) {
}
