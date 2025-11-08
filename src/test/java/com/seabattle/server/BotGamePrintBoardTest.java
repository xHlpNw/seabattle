package com.seabattle.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BotGamePrintBoardTest {
    @Autowired
    private GameService gameService;

    @Autowired
    private com.seabattle.server.repository.BoardRepository boardRepo;

    @Autowired
    private com.seabattle.server.repository.UserRepository userRepo;

    @WithMockUser(username = "player1", roles = {"PLAYER"})
    @Test
    void testFullBotGameWithPrint() throws Exception {
        // Берём тестового пользователя
        User testUser = userRepo.findAll().get(0);

        // Создаём игру с ботом
        Game game = gameService.createBotGame(testUser);

        // Авторасстановка кораблей
        gameService.placeShipsAuto(game.getId(), testUser.getId());

        // Получаем доски
        Board hostBoardEntity = boardRepo.findByGameIdAndPlayerId(game.getId(), testUser.getId()).orElseThrow();
        Board botBoardEntity = boardRepo.findByGameIdAndPlayerIsNull(game.getId()).orElseThrow();
        BoardModel hostBoard = BoardModel.fromJson(hostBoardEntity.getCells());
        BoardModel botBoard = BoardModel.fromJson(botBoardEntity.getCells());

        System.out.println("=== Initial Boards ===");
        printBoard(hostBoard, "Player Board");
        printBoard(botBoard, "Bot Board");

        boolean gameOver = false;

        // Симулируем выстрелы по всей доске до конца игры
        for (int x = 0; x < BoardModel.SIZE && !gameOver; x++) {
            for (int y = 0; y < BoardModel.SIZE && !gameOver; y++) {
                ShotResultDto result = gameService.playerShot(game.getId(), testUser.getId(), x, y);

                // Обновляем доски после хода
                hostBoard = BoardModel.fromJson(boardRepo.findByGameIdAndPlayerId(game.getId(), testUser.getId()).orElseThrow().getCells());
                botBoard = BoardModel.fromJson(boardRepo.findByGameIdAndPlayerIsNull(game.getId()).orElseThrow().getCells());

                System.out.println("Player shoots at (" + x + "," + y + ")");
                printBoard(hostBoard, "Player Board");
                printBoard(botBoard, "Bot Board");

                if (result.isGameOver()) {
                    System.out.println("Game Over: " + result.getMessage());
                    gameOver = true;
                }
            }
        }
    }

    // Метод печати доски
    private void printBoard(BoardModel board, String title) {
        System.out.println("=== " + title + " ===");
        for (int i = 0; i < BoardModel.SIZE; i++) {
            for (int j = 0; j < BoardModel.SIZE; j++) {
                BoardModel.Cell cell = board.getCells()[i][j];
                char c = switch (cell.getState()) {
                    case EMPTY -> '.';
                    case SHIP -> 'S';
                    case HIT -> 'X';
                    case MISS -> 'o';
                };
                System.out.print(c + " ");
            }
            System.out.println();
        }
        System.out.println();
    }
}
