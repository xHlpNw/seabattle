package com.seabattle.server.controller;

import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final BoardRepository boardRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    @PostMapping("/{gameId}/place-ships")
    public ResponseEntity<?> placeShips(
            @PathVariable UUID gameId,
            @RequestBody GridRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) return ResponseEntity.badRequest().body("Игра не найдена");
        Game game = gameOpt.get();

        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() ->new EntityNotFoundException(
                        "Имя пользователя не найдено"));

        // создаём boardModel...
        BoardModel boardModel = new BoardModel();
        int[][] grid = request.getGrid();
        for (int i = 0; i < grid.length; i++) {
            for (int j = 0; j < grid[i].length; j++) {
                if (grid[i][j] == 1) {
                    boardModel.getCells()[i][j].setState(BoardModel.CellState.SHIP);
                }
            }
        }

        Board board = boardRepository.findByGameIdAndPlayerId(game.getId(), player.getId())
                .orElse(Board.builder().game(game).player(player).build());

        board.setCells(boardModel.toJson());
        boardRepository.save(board);

        return ResponseEntity.ok(Map.of("message", "Доска сохранена"));
    }

    @GetMapping("/{gameId}/board")
    public ResponseEntity<?> getBoard(@PathVariable UUID gameId,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Board board = boardRepository.findByGameIdAndPlayerId(gameId, player.getId())
                .orElse(Board.builder()
                        .game(gameRepository.findById(gameId).orElseThrow())
                        .player(player)
                        .cells(new BoardModel().toJson()) // пустая доска
                        .build());

        boardRepository.save(board);

        BoardModel model = BoardModel.fromJson(board.getCells());
        List<List<Integer>> grid =
                Arrays.stream(model.toIntArray())
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList();

        return ResponseEntity.ok(Map.of("grid", grid));
    }

    // DTO для запроса
    @Getter
    @Setter
    public static class GridRequest {
        private int[][] grid;

        public String getGridAsString() {
            // Преобразуем массив в JSON строку
            // Можно использовать Jackson ObjectMapper, если подключен
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(grid);
            } catch (Exception e) {
                throw new RuntimeException("Ошибка сериализации сетки", e);
            }
        }
    }
}
