package com.seabattle.server.dto;

import com.seabattle.server.engine.BoardModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class ShipDTO {
    private int id;
    private int length;
    private List<BoardModel.Coord> cells;
    private boolean sunk;
}
