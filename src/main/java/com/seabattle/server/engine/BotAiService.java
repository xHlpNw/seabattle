package com.seabattle.server.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class BotAiService {

    private final Random rnd = new Random();

    /**
     * Advanced Probability Map AI:
     * 1. Build probability heatmaps around hits based on possible ship configurations
     * 2. Score cells by how many ship continuations they enable
     * 3. Exclude impossible cells (misses) and reduce off-axis weights
     * 4. For random shooting, use parity pattern
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

        if (!hits.isEmpty()) {
            // Build probability map around all hits
            Map<BoardModel.Coord, Integer> probabilityMap = buildProbabilityMap(playerBoard, hits);

            // Find the highest-scoring cell that hasn't been shot at
            BotMove bestTarget = findBestProbabilityTarget(probabilityMap, playerBoard);
            if (bestTarget != null) {
                return bestTarget;
            }
        }

        // No hits - use true random shooting for better coverage
        return findRandomTarget(playerBoard);
    }

    /**
     * Build probability map around all hits based on possible ship configurations
     */
    private Map<BoardModel.Coord, Integer> buildProbabilityMap(BoardModel playerBoard, List<BoardModel.Coord> hits) {
        Map<BoardModel.Coord, Integer> probabilityMap = new HashMap<>();

        // Build local probability maps around each hit and merge them
        for (BoardModel.Coord hit : hits) {
            Map<BoardModel.Coord, Integer> localMap = buildLocalProbabilityMap(playerBoard, hit);
            mergeProbabilityMaps(probabilityMap, localMap);
        }

        return probabilityMap;
    }

    /**
     * Build local probability map in 5x5 area around a single hit
     */
    private Map<BoardModel.Coord, Integer> buildLocalProbabilityMap(BoardModel playerBoard, BoardModel.Coord hit) {
        Map<BoardModel.Coord, Integer> localProbabilities = new HashMap<>();
        int centerX = hit.getX();
        int centerY = hit.getY();

        // Consider all standard ship lengths (2-5 cells)
        int[] shipLengths = {2, 3, 4, 5};

        for (int length : shipLengths) {
            // Horizontal ships that could pass through this hit
            addHorizontalShipProbabilities(playerBoard, centerX, centerY, length, localProbabilities);

            // Vertical ships that could pass through this hit
            addVerticalShipProbabilities(playerBoard, centerX, centerY, length, localProbabilities);
        }

        return localProbabilities;
    }

    /**
     * Add probabilities for horizontal ships passing through the hit cell
     */
    private void addHorizontalShipProbabilities(BoardModel playerBoard, int hitX, int hitY, int shipLength,
                                              Map<BoardModel.Coord, Integer> probabilities) {
        // Calculate possible ship positions that include the hit cell
        for (int startCol = hitY - shipLength + 1; startCol <= hitY; startCol++) {
            if (startCol < 0 || startCol + shipLength > BoardModel.SIZE) continue;

            // Check if this ship placement is possible (doesn't hit known misses)
            boolean validPlacement = true;
            for (int col = startCol; col < startCol + shipLength; col++) {
                BoardModel.Cell cell = playerBoard.getCells()[hitX][col];
                if (BoardModel.isNoShipZone(cell.getState())) {
                    validPlacement = false;
                    break;
                }
            }

            if (validPlacement) {
                // Score all cells in this possible ship placement
                for (int col = startCol; col < startCol + shipLength; col++) {
                    BoardModel.Coord coord = new BoardModel.Coord(hitX, col);
                    probabilities.put(coord, probabilities.getOrDefault(coord, 0) + 1);
                }
            }
        }
    }

    /**
     * Add probabilities for vertical ships passing through the hit cell
     */
    private void addVerticalShipProbabilities(BoardModel playerBoard, int hitX, int hitY, int shipLength,
                                            Map<BoardModel.Coord, Integer> probabilities) {
        // Calculate possible ship positions that include the hit cell
        for (int startRow = hitX - shipLength + 1; startRow <= hitX; startRow++) {
            if (startRow < 0 || startRow + shipLength > BoardModel.SIZE) continue;

            // Check if this ship placement is possible (doesn't hit known misses)
            boolean validPlacement = true;
            for (int row = startRow; row < startRow + shipLength; row++) {
                BoardModel.Cell cell = playerBoard.getCells()[row][hitY];
                if (BoardModel.isNoShipZone(cell.getState())) {
                    validPlacement = false;
                    break;
                }
            }

            if (validPlacement) {
                // Score all cells in this possible ship placement
                for (int row = startRow; row < startRow + shipLength; row++) {
                    BoardModel.Coord coord = new BoardModel.Coord(row, hitY);
                    probabilities.put(coord, probabilities.getOrDefault(coord, 0) + 1);
                }
            }
        }
    }

    /**
     * Merge local probability maps into global map
     */
    private void mergeProbabilityMaps(Map<BoardModel.Coord, Integer> globalMap,
                                    Map<BoardModel.Coord, Integer> localMap) {
        for (Map.Entry<BoardModel.Coord, Integer> entry : localMap.entrySet()) {
            BoardModel.Coord coord = entry.getKey();
            int localScore = entry.getValue();
            globalMap.put(coord, globalMap.getOrDefault(coord, 0) + localScore);
        }
    }

    /**
     * Find the best target from probability map (highest score, valid cell)
     */
    private BotMove findBestProbabilityTarget(Map<BoardModel.Coord, Integer> probabilityMap,
                                            BoardModel playerBoard) {
        BoardModel.Coord bestCoord = null;
        int bestScore = -1;

        for (Map.Entry<BoardModel.Coord, Integer> entry : probabilityMap.entrySet()) {
            BoardModel.Coord coord = entry.getKey();
            int score = entry.getValue();

            // Skip cells that have already been shot at
            BoardModel.Cell cell = playerBoard.getCells()[coord.getX()][coord.getY()];
            if (BoardModel.isAlreadyRevealed(cell.getState())) {
                continue;
            }

            // Find highest scoring valid cell
            if (score > bestScore) {
                bestScore = score;
                bestCoord = coord;
            }
        }

        return bestCoord != null ? new BotMove(bestCoord.getX(), bestCoord.getY()) : null;
    }

    /**
     * Random targeting: use true random shots for initial exploration
     */
    private BotMove findRandomTarget(BoardModel playerBoard) {
        // Create list of all available cells
        List<int[]> availableCells = new ArrayList<>();
        for (int i = 0; i < BoardModel.SIZE; i++) {
            for (int j = 0; j < BoardModel.SIZE; j++) {
                BoardModel.Cell c = playerBoard.getCells()[i][j];
                if (!BoardModel.isAlreadyRevealed(c.getState())) {
                    availableCells.add(new int[]{i, j});
                }
            }
        }

        // Pick random cell from available ones
        if (!availableCells.isEmpty()) {
            int[] randomCell = availableCells.get(rnd.nextInt(availableCells.size()));
            return new BotMove(randomCell[0], randomCell[1]);
        }

        return new BotMove(0, 0); // Should never reach here in a proper game
    }

    public record BotMove(int x, int y) {}
}







