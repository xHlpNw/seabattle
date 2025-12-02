package com.seabattle.server.dto;

public record UserProfileDTO(
        String username,
        int totalGames,
        int wins,
        int losses,
        double winrate,
        int rating,
        int position
) {}