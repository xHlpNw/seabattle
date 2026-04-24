package com.seabattle.server.dto;

public record AdminUpdateUserRequest(
        Integer rating,
        Integer wins,
        Integer losses,
        String role,
        String status
) {
}
