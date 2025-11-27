package com.seabattle.server.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BoardResponseDTO {
    private List<List<Integer>> playerBoard;
    private List<List<Integer>> enemyBoard;
}
