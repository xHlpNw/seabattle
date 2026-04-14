package com.seabattle.server.controller;

import com.seabattle.server.config.GameWebSocketHandler;
import com.seabattle.server.dto.AttackResult;
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

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final BoardRepository boardRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final GameService gameService;
    private final GameWebSocketHandler gameWebSocketHandler;

    @PostMapping("/{gameId}/ready")
    public ResponseEntity<?> markReady(@PathVariable UUID gameId,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Игра не найдена"));

        boolean isHost = game.getHost().equals(player);
        boolean isGuest = game.getGuest() != null && game.getGuest().equals(player);
        if (!isHost && !isGuest) {
            return ResponseEntity.status(403).body(Map.of("message", "Вы не участвуете в этой игре"));
        }

        if (isHost) {
            game.setHostReady(true);
        } else {
            game.setGuestReady(true);
        }

        // В игре с ботом достаточно готовности хоста
        boolean bothReady = game.isHostReady() && game.isGuestReady();
        boolean botGameReady = game.isBot() && isHost && game.isHostReady();
        if (bothReady || botGameReady) {
            game.setStatus(Game.GameStatus.IN_PROGRESS);
            game.setStartedAt(OffsetDateTime.now());
            game.setCurrentTurn(Game.Turn.HOST);
        }

        gameRepository.save(game);

        if (game.getType() == Game.GameType.ONLINE && !game.isBot()) {
            Map<String, Object> readyMessage = new HashMap<>();
            readyMessage.put("type", "playerReady");
            readyMessage.put("gameId", gameId.toString());
            readyMessage.put("isHost", game.getHost().equals(player));
            readyMessage.put("hostReady", game.isHostReady());
            readyMessage.put("guestReady", game.isGuestReady());
            readyMessage.put("bothReady", game.isHostReady() && game.isGuestReady());
            readyMessage.put("gameStarted", game.getStatus() == Game.GameStatus.IN_PROGRESS);
            if (game.getStatus() == Game.GameStatus.IN_PROGRESS) {
                readyMessage.put("currentTurn", game.getCurrentTurn() != null ? game.getCurrentTurn().name() : null);
            }
            gameWebSocketHandler.broadcastToGame(gameId, readyMessage);
        }

        boolean responseBothReady = (game.isHostReady() && game.isGuestReady())
                || (game.isBot() && game.getStatus() == Game.GameStatus.IN_PROGRESS);
        Map<String, Object> response = Map.of(
                "message", "Готовность отмечена",
                "bothReady", responseBothReady,
                "gameStarted", game.getStatus() == Game.GameStatus.IN_PROGRESS
        );

        return ResponseEntity.ok(response);
    }

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

                for (BoardModel.Coord c : ship.getCells()) {
                    BoardModel.Cell cell = boardModel.getCells()[c.getX()][c.getY()];
                    cell.setState(BoardModel.CellState.SHIP);
                    cell.setShipId(ship.getId());
                }
            }
        }

        Board board = boardRepository.findFirstByGameIdAndPlayerIdOrderByIdAsc(game.getId(), player.getId())
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

        Board board = boardRepository.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, player.getId())
                .orElse(Board.builder()
                        .game(gameRepository.findById(gameId).orElseThrow())
                        .player(player)
                        .cells(new BoardModel().toJson())
                        .build());

        boardRepository.save(board);

        BoardModel model = BoardModel.fromJson(board.getCells());
        List<List<Integer>> grid =
                Arrays.stream(model.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList();

        return ResponseEntity.ok(Map.of("grid", grid));
    }

    @GetMapping("/{gameId}/boards")
    public ResponseEntity<?> getBoards(@PathVariable UUID gameId,
                                       @AuthenticationPrincipal UserDetails userDetails) {

        User player = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found"));

        Board playerBoard = boardRepository.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, player.getId())
                .orElseGet(() -> Board.builder()
                        .game(game)
                        .player(player)
                        .cells(new BoardModel().toJson())
                        .build());

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

        boolean isBotGame = game.isBot();
        boolean isHost = game.getHost().equals(player);

        String opponentName;
        String opponentAvatar;
        if (isBotGame) {
            opponentName = "Bot";
            opponentAvatar = "/default_avatar.png";
        } else {
            User opponent = isHost ? game.getGuest() : game.getHost();
            opponentName = opponent != null ? opponent.getUsername() : "Waiting for opponent...";
            opponentAvatar = (opponent != null && opponent.getAvatar() != null)
                    ? opponent.getAvatar()
                    : "/default_avatar.png";
        }

        Map<String, Object> response = Map.ofEntries(
                Map.entry("playerBoard", Arrays.stream(playerModel.toIntArray(true))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList()),
                Map.entry("enemyBoard", Arrays.stream(enemyModel.toIntArray(false))
                        .map(row -> Arrays.stream(row).boxed().toList())
                        .toList()),
                Map.entry("gameFinished", game.getStatus() == Game.GameStatus.FINISHED),
                Map.entry("winner", game.getResult() != null ? game.getResult().name() : "NONE"),
                Map.entry("currentTurn", game.getStatus() == Game.GameStatus.IN_PROGRESS && game.getCurrentTurn() != null ? game.getCurrentTurn().name() : "NONE"),
                Map.entry("opponentName", opponentName),
                Map.entry("opponentAvatar", opponentAvatar),
                Map.entry("isBotGame", isBotGame),
                Map.entry("isHost", isHost)
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

    @PostMapping("/{gameId}/surrender")
    public ResponseEntity<String> surrender(@PathVariable UUID gameId,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            gameService.surrenderOnline(gameId, user);
            return ResponseEntity.ok("You surrendered.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    @Getter
    @Setter
    public static class AttackRequest {
        private int x;
        private int y;
    }
}
