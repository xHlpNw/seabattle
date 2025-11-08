package com.seabattle.server.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class BotAiService {

    private final Random rnd = new Random();

    /**
     * Простая стратегия: если есть незатопленные попадания (HIT) — пробуем соседей (hunt/target).
     * Иначе — случайная клетка, не проверённая ранее.
     */
    public BotMove nextMove(BoardModel playerBoard) {
        List<BoardModel.Coord> hits = new ArrayList<>();
        for (int i = 0; i < BoardModel.SIZE; i++) {
            for (int j = 0; j < BoardModel.SIZE; j++) {
                if (playerBoard.getCells()[i][j].getState() == BoardModel.CellState.HIT) {
                    hits.add(new BoardModel.Coord(i,j));
                }
            }
        }

        // try neighbors of last hits
        for (int k = hits.size() - 1; k >= 0; k--) {
            BoardModel.Coord h = hits.get(k);
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nx = h.getX() + d[0], ny = h.getY() + d[1];
                if (nx >= 0 && nx < BoardModel.SIZE && ny >= 0 && ny < BoardModel.SIZE) {
                    BoardModel.Cell c = playerBoard.getCells()[nx][ny];
                    if (c.getState() != BoardModel.CellState.MISS && c.getState() != BoardModel.CellState.HIT) {
                        return new BotMove(nx, ny);
                    }
                }
            }
        }

        // fallback random untried cell
        int tries = 0;
        while (tries++ < 5000) {
            int x = rnd.nextInt(BoardModel.SIZE);
            int y = rnd.nextInt(BoardModel.SIZE);
            BoardModel.Cell c = playerBoard.getCells()[x][y];
            if (c.getState() != BoardModel.CellState.MISS && c.getState() != BoardModel.CellState.HIT) {
                return new BotMove(x, y);
            }
        }

        // final fallback linear scan
        for (int i = 0; i < BoardModel.SIZE; i++) {
            for (int j = 0; j < BoardModel.SIZE; j++) {
                BoardModel.Cell c = playerBoard.getCells()[i][j];
                if (c.getState() != BoardModel.CellState.MISS && c.getState() != BoardModel.CellState.HIT) {
                    return new BotMove(i, j);
                }
            }
        }
        return new BotMove(0, 0);
    }

    public record BotMove(int x, int y) {}
}
