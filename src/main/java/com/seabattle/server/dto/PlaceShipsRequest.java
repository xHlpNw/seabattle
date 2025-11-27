package com.seabattle.server.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PlaceShipsRequest {
    private int[][] cells;           // сетка
    private List<ShipDTO> ships;     // список кораблей
}