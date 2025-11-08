package com.seabattle.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShotResultDto {
    private boolean hit;
    private boolean sunk;
    private boolean gameOver;
    private String result;
    private String message;
}
