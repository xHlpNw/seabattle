package com.seabattle.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaceShipsRequest {
    private Integer size;            // опционально: размер сетки (фронт присылает 10)
    private int[][] cells;           // сетка
    private List<ShipDTO> ships;     // список кораблей
}