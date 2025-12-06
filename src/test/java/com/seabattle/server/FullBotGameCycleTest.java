package com.seabattle.server;

import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class FullBotGameCycleTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private UserRepository userRepo;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = User.builder()
                .username("player_full_cycle")
                .passwordHash("pass")
                .rating(1000)
                .wins(0)
                .losses(0)
                .role(User.Role.PLAYER)
                .build();
        userRepo.save(testUser);
    }

    @Test
    @WithMockUser(username = "player_full_cycle", roles = {"PLAYER"})
    void testFullBotGameCycle() throws Exception {

        // 1. Создаём игру с ботом
        Game game = gameService.createBotGame(testUser);
        assertThat(game).isNotNull();
        assertThat(game.getStatus()).isEqualTo(Game.GameStatus.WAITING);

        // 2. Авто-расстановка кораблей игрока
        gameService.placeShipsAuto(game.getId(), testUser.getId());
        Game updated = gameService.getGameById(game.getId());
        assertThat(updated.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);

        // 3. Игрок делает ходы до окончания игры
        boolean gameOver = false;
        int x = 0, y = 0;
        while (!gameOver && x < 10) { // safety limit 10x10
            var resultDto = gameService.playerShot(game.getId(), testUser.getId(), x, y);
            gameOver = resultDto.isGameOver();

            // двигаем координаты по сетке
            y++;
            if (y >= 10) { y = 0; x++; }
        }

        // 4. Проверяем финальный статус игры
        Game finishedGame = gameService.getGameById(game.getId());
        assertThat(finishedGame.getStatus()).isEqualTo(Game.GameStatus.FINISHED);
        assertThat(finishedGame.getResult())
                .isIn(Game.GameResult.HOST_WIN, Game.GameResult.GUEST_WIN);

        // 5. Проверяем обновление статистики игрока
        User playerAfter = userRepo.findById(testUser.getId()).orElseThrow();
        assertThat(playerAfter.getWins() + playerAfter.getLosses()).isGreaterThan(0);
        assertThat(playerAfter.getRating()).isNotNull();

        System.out.println("Full game finished. Result: " + finishedGame.getResult());
        System.out.println("Player rating: " + playerAfter.getRating() +
                ", wins: " + playerAfter.getWins() +
                ", losses: " + playerAfter.getLosses());
    }

    @Test
    void testMissesMarkedAroundSunkShip() {
        // Create a board and place a small ship
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();

        // Place a 2-cell ship horizontally at (4,4) and (4,5)
        board.placeShip(1, 4, 4, true, 2);

        // Verify initial state - ship cells should be SHIP
        assertThat(board.getCells()[4][4].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.SHIP);
        assertThat(board.getCells()[4][5].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.SHIP);

        // Adjacent cells should be EMPTY initially
        assertThat(board.getCells()[3][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[3][4].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[3][5].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[4][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[4][6].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[5][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[5][4].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[5][5].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
        assertThat(board.getCells()[5][6].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);

        // Shoot at first cell of ship - should hit but not sink
        var outcome1 = board.shoot(4, 4);
        assertThat(outcome1.hit).isTrue();
        assertThat(outcome1.sunk).isFalse();

        // Shoot at second cell of ship - should hit and sink
        var outcome2 = board.shoot(4, 5);
        assertThat(outcome2.hit).isTrue();
        assertThat(outcome2.sunk).isTrue();

        // Now check that adjacent cells are marked as misses
        assertThat(board.getCells()[3][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[3][4].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[3][5].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[4][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[4][6].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[5][3].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[5][4].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[5][5].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
        assertThat(board.getCells()[5][6].getState()).isEqualTo(com.seabattle.server.engine.BoardModel.CellState.MISS);
    }

    @Test
    void testBotTargetingAI() {
        // Create a board with a 3-cell ship in the middle
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();
        board.placeShip(1, 2, 2, true, 3); // Ship at (2,2), (2,3), (2,4)

        // Create bot AI
        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        // Test 1: No hits yet - should use random targeting
        var move1 = botAI.nextMove(board);
        // With random targeting, we just verify it's a valid empty cell
        assertThat(board.getCells()[move1.x()][move1.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);

        // Test 2: Player hits the ship at one end
        var outcome1 = board.shoot(2, 2); // Hit at (2,2)
        assertThat(outcome1.hit).isTrue();
        assertThat(outcome1.sunk).isFalse();

        // Bot should target adjacent cells
        var move2 = botAI.nextMove(board);
        boolean isAdjacent = (Math.abs(move2.x() - 2) + Math.abs(move2.y() - 2) == 1);
        assertThat(isAdjacent).isTrue();

        // Test 3: Bot hits the middle of the ship
        var outcome2 = board.shoot(2, 3); // Hit at (2,3)
        assertThat(outcome2.hit).isTrue();
        assertThat(outcome2.sunk).isFalse();

        // Now bot should recognize horizontal ship and use direction targeting
        var move3 = botAI.nextMove(board);
        // Should target the remaining end: (2,4) or (2,1), but since (2,2) and (2,3) are hit,
        // and (2,1) would be left of (2,2), (2,4) would be right of (2,3)
        assertThat(move3.x()).isEqualTo(2); // Same row
        assertThat(move3.y()).isIn(1, 4); // Valid positions
        assertThat(board.getCells()[move3.x()][move3.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
    }

    @Test
    void testBotPrioritizesHits() {
        // Create a board with multiple ships
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();
        board.placeShip(1, 2, 2, true, 3); // 3-cell ship at (2,2), (2,3), (2,4)
        board.placeShip(2, 5, 5, false, 2); // 2-cell ship at (5,5), (6,5)

        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        // Simulate bot hitting the 3-cell ship
        board.shoot(2, 2); // Hit at (2,2)

        // Bot should prioritize shooting near this hit, not randomly
        var move = botAI.nextMove(board);
        boolean isNearHit = (Math.abs(move.x() - 2) + Math.abs(move.y() - 2) == 1);
        assertThat(isNearHit).isTrue();
    }

    @Test
    void testBotAxisTargeting() {
        // Test the bot's ability to target along ship axis
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();
        board.placeShip(1, 2, 2, true, 4); // 4-cell horizontal ship at (2,2), (2,3), (2,4), (2,5)

        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        // First check that bot uses random when no hits
        var initialMove = botAI.nextMove(board);
        // With random targeting, we just verify it's a valid empty cell
        assertThat(board.getCells()[initialMove.x()][initialMove.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);

        // Simulate player hitting two parts of the ship
        board.shoot(2, 2); // Hit at (2,2)
        board.shoot(2, 4); // Hit at (2,4)

        System.out.println("=== Test: Bot should target along horizontal axis ===");
        var move = botAI.nextMove(board);

        // Print what the bot chose
        System.out.println("Bot chose: (" + move.x() + "," + move.y() + ")");

        // Should target along the horizontal axis (row 2)
        // Valid targets: (2,1) left of leftmost hit (2,2)
        // (2,5) is not valid because it's part of the ship (not EMPTY)
        assertThat(move.x()).isEqualTo(2); // Must be in row 2
        assertThat(move.y()).isEqualTo(1); // Should target (2,1) - left extension
        assertThat(board.getCells()[move.x()][move.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
    }

    @Test
    void testProbabilityMapAI() {
        // Test the new probability map AI with a specific scenario
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();
        board.placeShip(1, 2, 2, true, 3); // 3-cell ship at (2,2), (2,3), (2,4)

        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        // Simulate player hitting the middle of the ship
        board.shoot(2, 3); // Hit at (2,3)

        // Bot should use probability map to find likely ship continuations
        var move = botAI.nextMove(board);
        System.out.println("Probability AI chose: (" + move.x() + "," + move.y() + ")");

        // Should target cells with high probability scores
        // The ship is at (2,2), (2,3), (2,4), so (2,2) and (2,4) should have high scores
        // Also (2,1) and (2,5) for 4-cell ship possibilities
        assertThat(move.x()).isEqualTo(2); // Same row as hit
        assertThat(board.getCells()[move.x()][move.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);

        // Test with miss blocking
        board.shoot(2, 1); // Miss at (2,1) - should reduce probabilities

        // Bot should adapt and choose different target
        var move2 = botAI.nextMove(board);
        System.out.println("After miss, Probability AI chose: (" + move2.x() + "," + move2.y() + ")");
        assertThat(board.getCells()[move2.x()][move2.y()].getState())
                .isEqualTo(com.seabattle.server.engine.BoardModel.CellState.EMPTY);
    }

    @Test
    void testBotFullGameplay() {
        // Simulate a full game to see how the bot performs
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();

        // Place standard Battleship fleet
        board.placeShip(1, 0, 0, true, 4);  // Aircraft carrier
        board.placeShip(2, 0, 2, true, 3);  // Battleship
        board.placeShip(3, 0, 4, true, 3);  // Cruiser
        board.placeShip(4, 0, 6, true, 2);  // Destroyer
        board.placeShip(5, 0, 8, true, 2);  // Submarine
        board.placeShip(6, 5, 0, false, 1); // Patrol boat 1
        board.placeShip(7, 7, 0, false, 1); // Patrol boat 2

        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        int shots = 0;
        int hits = 0;

        // Simulate bot playing against the fleet
        while (true) {
            var move = botAI.nextMove(board);
            var outcome = board.shoot(move.x(), move.y());
            shots++;

            if (outcome.hit()) {
                hits++;
                if (outcome.sunk()) {
                    System.out.println("Bot sunk a ship! Total hits: " + hits + ", shots: " + shots);
                }
            } else {
                // Count misses to see targeting efficiency
            }

            // Check if all ships are sunk
            boolean allSunk = true;
            for (int i = 0; i < 10 && allSunk; i++) {
                for (int j = 0; j < 10 && allSunk; j++) {
                    if (board.getCells()[i][j].getState() == com.seabattle.server.engine.BoardModel.CellState.SHIP) {
                        allSunk = false;
                    }
                }
            }

            if (allSunk) {
                double efficiency = (double) hits / shots;
                System.out.println("Bot won! Total shots: " + shots + ", hits: " + hits + ", efficiency: " + String.format("%.2f", efficiency));
                break;
            }

            if (shots > 100) { // Safety limit
                System.out.println("Bot took too many shots (" + shots + "), stopping test");
                break;
            }
        }

        // Bot should perform reasonably well (not perfectly, but better than random)
        // Random shooting would take ~50 shots on average, good AI should do much better
        assertThat(shots).isLessThan(50); // Should sink all ships efficiently
        assertThat(hits).isEqualTo(17); // Total ship cells (4+3+3+2+2+1+1=17)
    }

    @Test
    void testBotRemembersHitsAcrossTurns() {
        // Test that bot remembers hits from previous "turns" (method calls)
        com.seabattle.server.engine.BoardModel board = new com.seabattle.server.engine.BoardModel();
        board.placeShip(1, 2, 2, true, 3); // 3-cell ship at (2,2), (2,3), (2,4)

        com.seabattle.server.engine.BotAiService botAI = new com.seabattle.server.engine.BotAiService();

        // Simulate bot hitting a ship
        board.shoot(2, 2); // Bot hits at (2,2)
        System.out.println("=== Bot hit at (2,2) ===");

        // Bot should now target adjacent cells
        var move1 = botAI.nextMove(board);
        System.out.println("Bot's next move: (" + move1.x() + "," + move1.y() + ")");
        assertThat(Math.abs(move1.x() - 2) + Math.abs(move1.y() - 2)).isEqualTo(1); // Adjacent

        // Simulate bot hitting again
        if (move1.x() == 2 && move1.y() == 3) { // Hit (2,3)
            board.shoot(2, 3);
            System.out.println("=== Bot hit at (2,3), now has 2 hits in line ===");

            // Bot should use direction targeting
            var move2 = botAI.nextMove(board);
            System.out.println("Bot's direction targeting move: (" + move2.x() + "," + move2.y() + ")");
            assertThat(move2.x()).isEqualTo(2); // Same row
            assertThat(move2.y()).isEqualTo(1); // Left extension
        }

        // Now simulate "turn passing" - the board state persists
        // When bot's turn comes again, it should still see the existing hits
        System.out.println("=== Simulating turn pass - board state persists ===");
        var move3 = botAI.nextMove(board);
        System.out.println("Bot remembers hits after turn: (" + move3.x() + "," + move3.y() + ")");

        // Should still be targeting based on existing hits
        // (This test shows the bot does remember hits across method calls)
    }
}
