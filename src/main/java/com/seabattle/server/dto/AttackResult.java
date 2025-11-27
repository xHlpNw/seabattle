package com.seabattle.server.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AttackResult {
    private int[][] playerBoard;
    private int[][] enemyBoard;
    private boolean hit;
    private boolean sunk;
    private boolean already;

    private Integer botX;
    private Integer botY;
    private Boolean botHit;
    private Boolean botSunk;
}

