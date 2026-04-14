package com.seabattle.server.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BoardModel {

    public static final int SIZE = 10;

    public enum CellState { EMPTY, SHIP, MISS, HIT, ADJACENT_SUNK }

    /** Промах по выстрелу или авто-отметка вокруг потопленного — корабля там быть не может. */
    public static boolean isNoShipZone(CellState s) {
        return s == CellState.MISS || s == CellState.ADJACENT_SUNK;
    }

    /** Уже «открытая» клетка: стрелять снова нельзя (как в промах/попадание/вокруг потопленного). */
    public static boolean isAlreadyRevealed(CellState s) {
        return s == CellState.MISS || s == CellState.HIT || s == CellState.ADJACENT_SUNK;
    }

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

    @SneakyThrows
    public static BoardModel fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return new BoardModel();
        return MAPPER.readValue(json, BoardModel.class);
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации BoardModel", e);
        }
    }

    /** Расставляет корабль без проверки соседства. Возвращает true при успехе. */
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
        if (isAlreadyRevealed(c.state)) {
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
                    if (allHit) {
                        target.sunk = true;
                        sunk = true;
                        markMissesAroundShip(target);
                    }
            }
            return new ShotOutcome(true, sunk, false);
        } else {
            c.state = CellState.MISS;
            return new ShotOutcome(false, false, false);
        }
    }

    public static BoardModel autoPlaceRandom() {
        BoardModel bm = new BoardModel();
        Random rnd = new Random();
        int[] shipLengths = {4,3,3,2,2,2,1,1,1,1}; // 1x4, 2x3, 3x2, 4x1
        int id = 1;

        for (int len : shipLengths) {
            boolean placed = false;
            int tries = 0;
            while (!placed && tries++ < 1000) {
                boolean horiz = rnd.nextBoolean();
                int x = rnd.nextInt(SIZE);
                int y = rnd.nextInt(SIZE);
                if (canPlaceShip(bm, x, y, horiz, len)) {
                    bm.placeShip(id++, x, y, horiz, len);
                    placed = true;
                }
            }
            if (!placed) throw new IllegalStateException("Cannot place ship of length " + len);
        }
        return bm;
    }

    private static boolean canPlaceShip(BoardModel bm, int x, int y, boolean horiz, int length) {
        int startX = Math.max(0, x - 1);
        int startY = Math.max(0, y - 1);
        int endX = horiz ? Math.min(SIZE - 1, x + 1) : Math.min(SIZE - 1, x + length);
        int endY = horiz ? Math.min(SIZE - 1, y + length) : Math.min(SIZE - 1, y + 1);

        for (int i = startX; i <= endX; i++) {
            for (int j = startY; j <= endY; j++) {
                if (bm.getCells()[i][j].getState() == CellState.SHIP) return false;
            }
        }

        if (horiz && y + length > SIZE) return false;
        if (!horiz && x + length > SIZE) return false;

        return true;
    }

    public int[][] toIntArray(boolean revealShips) {
        int[][] grid = new int[SIZE][SIZE];

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                switch (cells[i][j].state) {
                    case EMPTY:
                        grid[i][j] = 0;
                        break;
                    case SHIP:
                        grid[i][j] = revealShips ? 1 : 0;
                        break;
                    case MISS:
                        grid[i][j] = 2;
                        break;
                    case ADJACENT_SUNK:
                        grid[i][j] = 4;
                        break;
                    case HIT:
                        grid[i][j] = 3;
                        break;
                }
            }
        }
        return grid;
    }

    /**
     * Отмечает соседние клетки вокруг потопленного корабля (отдельно от промаха по выстрелу — для UI).
     */
    private void markMissesAroundShip(Ship ship) {
        for (Coord coord : ship.cells) {
            int x = coord.x;
            int y = coord.y;

            // Check all 8 adjacent cells
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = x + dx;
                    int ny = y + dy;

                    if ((dx == 0 && dy == 0) || nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) {
                        continue;
                    }

                    if (cells[nx][ny].state == CellState.EMPTY) {
                        cells[nx][ny].state = CellState.ADJACENT_SUNK;
                    }
                }
            }
        }
    }

}
