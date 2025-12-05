package com.seabattle.server.controller;

import com.seabattle.server.dto.AttackResult;
import com.seabattle.server.dto.BoardResponseDTO;
import com.seabattle.server.dto.PlaceShipsRequest;
import com.seabattle.server.dto.ShipDTO;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final BoardRepository boardRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameService gameService;

    @PostMapping("/{gameId}/place-ships")
    public ResponseEntity<?> placeShips(
            @PathVariable UUID gameId,
            @RequestBody PlaceShipsRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Игра не найдена"));

        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        BoardModel boardModel = new BoardModel();

        int[][] cells = request.getCells();
        if (cells != null) {
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    if (cells[i][j] == 1) {
                        boardModel.getCells()[i][j].setState(BoardModel.CellState.SHIP);
                    }
                }
            }
        }

        if (request.getShips() != null) {
            for (ShipDTO ship : request.getShips()) {
                BoardModel.Ship s = new BoardModel.Ship();
                s.setId(ship.getId());
                s.setLength(ship.getLength());
                s.setSunk(ship.isSunk());
                s.setCells(ship.getCells());
                boardModel.getShips().add(s);

                // ставим shipId в клетки
                for (BoardModel.Coord c : ship.getCells()) {
                    BoardModel.Cell cell = boardModel.getCells()[c.getX()][c.getY()];
                    cell.setState(BoardModel.CellState.SHIP);
                    cell.setShipId(ship.getId());
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
                Arrays.stream(model.toIntArray(true))
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

    @GetMapping("/{gameId}/boards")
    public ResponseEntity<?> getBoards(@PathVariable UUID gameId,
                                       @AuthenticationPrincipal UserDetails userDetails) {

        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found"));

        // Доска игрока
        Board playerBoard = boardRepository.findByGameIdAndPlayerId(gameId, player.getId())
                .orElseGet(() -> Board.builder()
                        .game(game)
                        .player(player)
                        .cells(new BoardModel().toJson())
                        .build());

        // Доска противника
        Board enemyBoard;
        if (game.isBot()) {
            enemyBoard = boardRepository.findByGameIdAndPlayerIsNull(gameId)
                    .orElseThrow(() -> new EntityNotFoundException("Бот ещё не создан"));
        } else {
            enemyBoard = boardRepository.findByGameIdAndPlayerIdNot(gameId, player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Противник ещё не подключился"));
        }

        boardRepository.save(playerBoard);

        BoardModel playerModel = BoardModel.fromJson(playerBoard.getCells());
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        Map<String, Object> response = Map.ofEntries(
                Map.entry("playerBoard", Arrays.stream(playerModel.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList()),
                Map.entry("enemyBoard", Arrays.stream(enemyModel.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList()),
                Map.entry("gameFinished", game.getStatus() == Game.GameStatus.FINISHED),
                Map.entry("winner", game.getResult() != null ? game.getResult().name() : "NONE"),
                Map.entry("currentTurn", game.getCurrentTurn() != null ? game.getCurrentTurn().name() : "HOST")
        );

        return ResponseEntity.ok(response);
    }


    @PostMapping("/{gameId}/attack")
    public ResponseEntity<?> attack(
            @PathVariable UUID gameId,
            @RequestBody AttackRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            AttackResult result = gameService.attack(gameId, userDetails.getUsername(), request.getX(), request.getY());
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @PostMapping("/{gameId}/bot-move")
    public ResponseEntity<?> botMove(@PathVariable UUID gameId) {
        try {
            AttackResult result = gameService.botMove(gameId);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    // DTO для запроса атаки
    @Getter
    @Setter
    public static class AttackRequest {
        private int x;
        private int y;
    }



}
