package com.seabattle.server.dto;

import com.seabattle.server.engine.BoardModel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ShipDTO {
    private int size;
    private List<BoardModel.Coord> cells;
}
