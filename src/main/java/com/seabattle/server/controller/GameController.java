package com.seabattle.server.controller;

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

        Map<String, List<List<Integer>>> response = Map.of(
                "playerBoard", Arrays.stream(playerModel.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList(),
                "enemyBoard", Arrays.stream(enemyModel.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{gameId}/attack")
    public ResponseEntity<?> attack(
            @PathVariable UUID gameId,
            @RequestBody AttackRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found"));

        // проверяем ход
        Game.Turn playerTurn = (game.getHost().getId().equals(player.getId())) ? Game.Turn.HOST : Game.Turn.GUEST;
        if (!playerTurn.equals(game.getCurrentTurn())) {
            return ResponseEntity.status(403).body("Сейчас не ваш ход");
        }

        // получаем доску противника
        Board enemyBoard;

        if (game.isBot()) {
            // Ищем доску бота
            enemyBoard = boardRepository.findByGameIdAndPlayerIsNull(gameId)
                    .orElseGet(() -> {
                        BoardModel botBoardModel = BoardModel.autoPlaceRandom();
                        Board b = Board.builder()
                                .game(game)
                                .player(null)
                                .cells(botBoardModel.toJson())
                                .build();
                        return boardRepository.save(b);
                    });
        } else {
            // Ищем доску другого игрока
            enemyBoard = boardRepository.findByGameIdAndPlayerIdNot(gameId, player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Противник ещё не подключился"));
        }
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        // ход игрока
        BoardModel.ShotOutcome outcome = enemyModel.shoot(request.getX(), request.getY());
        enemyBoard.setCells(enemyModel.toJson());
        boardRepository.save(enemyBoard);

        // если промах или потоплен корабль, передаём ход
        if (!outcome.hit || outcome.sunk) {
            if (!game.isBot()) {
                game.setCurrentTurn(game.getCurrentTurn() == Game.Turn.HOST ? Game.Turn.GUEST : Game.Turn.HOST);
            }
        }

        // если игра с ботом и передаем ход боту
        BoardModel.ShotOutcome botOutcome = null;
        int botX = -1, botY = -1;
        if (game.isBot() && (!outcome.hit || outcome.sunk)) {
            Board playerBoardEntity = boardRepository.findByGameIdAndPlayerId(gameId, player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
            BoardModel playerModel = BoardModel.fromJson(playerBoardEntity.getCells());

            Random rnd = new Random();
            do {
                botX = rnd.nextInt(BoardModel.SIZE);
                botY = rnd.nextInt(BoardModel.SIZE);
                botOutcome = playerModel.shoot(botX, botY);
            } while (botOutcome.already);

            playerBoardEntity.setCells(playerModel.toJson());
            boardRepository.save(playerBoardEntity);
        }

        gameRepository.save(game);

        // возвращаем результат
        Board playerBoardEntity = boardRepository.findByGameIdAndPlayerId(gameId, player.getId())
                .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
        BoardModel playerModel = BoardModel.fromJson(playerBoardEntity.getCells());

        Map<String, Object> response = new HashMap<>();
        response.put("playerBoard", Arrays.stream(playerModel.toIntArray(true))
                .map(row -> Arrays.stream(row).boxed().toList())
                .toList());
        response.put("enemyBoard", Arrays.stream(enemyModel.toIntArray(true))
                .map(row -> Arrays.stream(row).boxed().toList())
                .toList());
        response.put("hit", outcome.hit);
        response.put("sunk", outcome.sunk);
        response.put("already", outcome.already);

        if (botOutcome != null) {
            response.put("botX", botX);
            response.put("botY", botY);
            response.put("botHit", botOutcome.hit);
            response.put("botSunk", botOutcome.sunk);
        }

        return ResponseEntity.ok(response);
    }

    // DTO для запроса атаки
    @Getter
    @Setter
    public static class AttackRequest {
        private int x;
        private int y;
    }



}
