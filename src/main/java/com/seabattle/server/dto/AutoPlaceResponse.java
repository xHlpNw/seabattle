package com.seabattle.server.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AutoPlaceResponse {
    private int[][] grid;
    private List<ShipDTO> ships;
}