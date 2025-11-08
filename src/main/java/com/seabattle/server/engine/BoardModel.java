package com.seabattle.server.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoardModel {

    public static final int SIZE = 10;

    public enum CellState { EMPTY, SHIP, MISS, HIT }

    @Data
    public static class Cell {
        private CellState state = CellState.EMPTY;
        private Integer shipId;
    }

    @Data
    public static class Ship {
        private int id;
        private int length;
        private List<Coord> cells = new ArrayList<>();
        private boolean sunk = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coord {
        private int x;
        private int y;
    }

    private int size = SIZE;
    private Cell[][] cells = new Cell[SIZE][SIZE];
    private List<Ship> ships = new ArrayList<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BoardModel() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) cells[i][j] = new Cell();
        }
    }

    public static BoardModel fromJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) return new BoardModel();
        return MAPPER.readValue(json, BoardModel.class);
    }

    public String toJson() throws Exception {
        return MAPPER.writeValueAsString(this);
    }

    // simple ship placer (no adjacency checks). Returns true if placed
    public boolean placeShip(int shipId, int x, int y, boolean horizontal, int length) {
        if (horizontal) {
            if (y + length > SIZE) return false;
            for (int c = y; c < y + length; c++) if (cells[x][c].state != CellState.EMPTY) return false;
            Ship s = new Ship();
            s.id = shipId; s.length = length;
            for (int c = y; c < y + length; c++) {
                cells[x][c].state = CellState.SHIP;
                cells[x][c].shipId = shipId;
                s.cells.add(new Coord(x, c));
            }
            ships.add(s);
            return true;
        } else {
            if (x + length > SIZE) return false;
            for (int r = x; r < x + length; r++) if (cells[r][y].state != CellState.EMPTY) return false;
            Ship s = new Ship();
            s.id = shipId; s.length = length;
            for (int r = x; r < x + length; r++) {
                cells[r][y].state = CellState.SHIP;
                cells[r][y].shipId = shipId;
                s.cells.add(new Coord(r, y));
            }
            ships.add(s);
            return true;
        }
    }

    public static final int[] STANDARD_SHIPS = {4,3,3,2,2,1,1,1,1};

    public boolean allShipsSunk() {
        for (Ship s : ships) if (!s.sunk) return false;
        return true;
    }

    public static class ShotOutcome {
        public final boolean hit;
        public final boolean sunk;
        public final boolean already;

        public ShotOutcome(boolean hit, boolean sunk, boolean already) {
            this.hit = hit; this.sunk = sunk; this.already = already;
        }
    }

    public ShotOutcome shoot(int x, int y) {
        Cell c = cells[x][y];
        if (c.state == CellState.MISS || c.state == CellState.HIT) {
            return new ShotOutcome(false, false, true);
        }
        if (c.state == CellState.SHIP) {
            c.state = CellState.HIT;
            int shipId = c.shipId;
            Ship target = null;
            for (Ship s : ships) if (s.id == shipId) { target = s; break; }
            boolean sunk = false;
            if (target != null) {
                boolean allHit = true;
                for (Coord coord : target.cells) {
                    if (cells[coord.x][coord.y].state != CellState.HIT) { allHit = false; break; }
                }
                if (allHit) { target.sunk = true; sunk = true; }
            }
            return new ShotOutcome(true, sunk, false);
        } else {
            c.state = CellState.MISS;
            return new ShotOutcome(false, false, false);
        }
    }

    // simple auto-placement
    public static BoardModel autoPlaceRandom() {
        BoardModel bm = new BoardModel();
        Random rnd = new Random();
        int id = 1;
        for (int len : STANDARD_SHIPS) {
            boolean placed = false;
            int tries = 0;
            while (!placed && tries++ < 2000) {
                boolean horiz = rnd.nextBoolean();
                int x = rnd.nextInt(SIZE);
                int y = rnd.nextInt(SIZE);
                placed = bm.placeShip(id++, x, y, horiz, len);
            }
        }
        return bm;
    }
}
