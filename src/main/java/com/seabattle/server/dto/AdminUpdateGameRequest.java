package com.seabattle.server.dto;

public record AdminUpdateGameRequest(
        String status,
        String result,
        String currentTurn
) {
}
