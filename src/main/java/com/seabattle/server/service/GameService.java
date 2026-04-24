package com.seabattle.server.service;

import com.seabattle.server.config.GameWebSocketHandler;
import com.seabattle.server.dto.AttackResult;
import com.seabattle.server.dto.AutoPlaceResponse;
import com.seabattle.server.dto.ShipDTO;
import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.engine.BotAiService;
import com.seabattle.server.entity.*;
import com.seabattle.server.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameRepository gameRepo;
    private final BoardRepository boardRepo;
    private final MoveRepository moveRepo;
    private final GameHistoryRepository historyRepo;
    private final UserRepository userRepo;
    private final BotAiService botAi;
    private final GameWebSocketHandler gameWebSocketHandler;

    @Transactional
    public Game createBotGame(User host) throws Exception {
        Game g = Game.builder()
                .type(Game.GameType.BOT)
                .host(host)
                .isBot(true)
                .status(Game.GameStatus.WAITING)
                .currentTurn(Game.Turn.HOST)
                .build();
        gameRepo.save(g);

        Board playerBoard = Board.builder()
                .game(g)
                .player(host)
                .cells(new BoardModel().toJson())
                .build();
        boardRepo.save(playerBoard);

        Board botBoard = Board.builder()
                .game(g)
                .player(null)
                .cells(BoardModel.autoPlaceRandom().toJson())
                .build();
        boardRepo.save(botBoard);

        return g;
    }

    @Transactional
    public AutoPlaceResponse placeShipsAuto(UUID gameId, UUID playerId) throws Exception {
        Game g = gameRepo.findById(gameId).orElseThrow();
        Board board = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, playerId)
                .orElseGet(() -> {
                    Board newBoard = Board.builder()
                            .game(g)
                            .player(userRepo.findById(playerId).orElseThrow())
                            .cells("empty")
                            .build();
                    return boardRepo.save(newBoard);
                });

        BoardModel bm = BoardModel.autoPlaceRandom();
        board.setCells(bm.toJson());
        boardRepo.save(board);

        if (g.getStatus() == Game.GameStatus.WAITING) {
            g.setStatus(Game.GameStatus.IN_PROGRESS);
            g.setStartedAt(OffsetDateTime.now());
            gameRepo.save(g);
        }

        List<ShipDTO> ships = bm.getShips().stream()
                .map(s -> ShipDTO.builder()
                        .length(s.getLength())
                        .cells(s.getCells())
                        .build())
                .toList();

        return new AutoPlaceResponse(bm.toIntArray(true), ships);
    }


    @Transactional
    public void placeShipsManual(UUID gameId, UUID playerId, String cellsJson) throws Exception {
        Board board = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, playerId).orElseThrow();
        BoardModel bm = BoardModel.fromJson(cellsJson);
        board.setCells(bm.toJson());
        boardRepo.save(board);

        Game g = gameRepo.findById(gameId).orElseThrow();
        if (g.getStatus() == Game.GameStatus.WAITING) {
            g.setStatus(Game.GameStatus.IN_PROGRESS);
            g.setStartedAt(OffsetDateTime.now());
            gameRepo.save(g);
        }
    }

    /**
     * Player shot -> if hit, player keeps shooting; if miss -> bot moves.
     * Returns ShotResultDto with details.
     */
    @Transactional
    public ShotResultDto playerShot(UUID gameId, UUID playerId, int x, int y) throws Exception {
        Game game = gameRepo.findById(gameId).orElseThrow();
        if (game.getType() != Game.GameType.BOT || !game.isBot()) {
            throw new IllegalStateException("Not a bot game");
        }
        if (game.getStatus() != Game.GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("Game not in progress");
        }
        if (game.getCurrentTurn() != Game.Turn.HOST) {
            throw new IllegalStateException("Not your turn");
        }

        Board playerBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, playerId).orElseThrow();
        Board botBoard = boardRepo.findByGameIdAndPlayerIsNull(gameId).orElseThrow();

        BoardModel botBm = BoardModel.fromJson(botBoard.getCells());
        BoardModel.ShotOutcome playerOutcome = botBm.shoot(x, y);
        if (playerOutcome.already) {
            ShotResultDto dto = ShotResultDto.builder()
                    .hit(false)
                    .sunk(false)
                    .gameOver(false)
                    .message("This cell was already shot")
                    .build();
            return dto;
        }
        botBoard.setCells(botBm.toJson());
        boardRepo.save(botBoard);

        Move playerMove = Move.builder()
                .game(game)
                .player(playerBoard.getPlayer())
                .x((short)x)
                .y((short)y)
                .hit(playerOutcome.hit)
                .build();
        moveRepo.save(playerMove);

        ShotResultDto dto = new ShotResultDto();
        dto.setHit(playerOutcome.hit);
        dto.setSunk(playerOutcome.sunk);

        if (botBm.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setResult(Game.GameResult.HOST_WIN);
            game.setFinishedAt(OffsetDateTime.now());
            gameRepo.save(game);

            persistHistoryAndStats(game, playerBoard.getPlayer(), null, "WIN", +10);
            dto.setGameOver(true);
            dto.setResult("HOST_WIN");
            dto.setMessage("You won!");
            return dto;
        }

        // При попадании игрок продолжает ход (правило "ход при попадании")
        if (playerOutcome.hit) {
            dto.setGameOver(false);
            dto.setMessage("Hit — it's still your turn");
            return dto;
        }

        BoardModel playerBm = BoardModel.fromJson(playerBoard.getCells());
        BotAiService.BotMove botMove = botAi.nextMove(playerBm);
        BoardModel.ShotOutcome botOutcome = playerBm.shoot(botMove.x(), botMove.y());

        log.debug("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

        Move botMoveEntity = Move.builder()
                .game(game)
                .player(null)
                .x((short)botMove.x())
                .y((short)botMove.y())
                .hit(botOutcome.hit)
                .build();
        moveRepo.save(botMoveEntity);

        if (!botOutcome.already) {
            playerBoard.setCells(playerBm.toJson());
            boardRepo.save(playerBoard);
        }

        if (playerBm.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setResult(Game.GameResult.GUEST_WIN);
            game.setFinishedAt(OffsetDateTime.now());
            gameRepo.save(game);

            persistHistoryAndStats(game, playerBoard.getPlayer(), null, "LOSS", -10);
            dto.setGameOver(true);
            dto.setResult("GUEST_WIN");
            dto.setMessage("Bot won.");
            return dto;
        }

        // Бот сохраняет ход только при попадании без потопления
        if (botOutcome.hit && !botOutcome.sunk) {
            game.setCurrentTurn(Game.Turn.GUEST);
            dto.setMessage("Bot hit — bot's turn again.");
        } else {
            game.setCurrentTurn(Game.Turn.HOST);
            dto.setMessage("Bot " + (botOutcome.hit ? "sunk a ship" : "missed") + " — your turn.");
        }
        gameRepo.save(game);

        dto.setGameOver(false);
        dto.setMessage("Miss — bot moved.");
        return dto;
    }

    @Transactional
    public void surrender(UUID gameId, UUID playerId) {
        Game game = gameRepo.findById(gameId).orElseThrow();
        if (game.getType() != Game.GameType.BOT) throw new IllegalStateException("Not a bot game");
        var player = userRepo.findById(playerId).orElseThrow();

        game.setStatus(Game.GameStatus.FINISHED);
        game.setResult(Game.GameResult.SURRENDER);
        game.setFinishedAt(OffsetDateTime.now());
        gameRepo.save(game);

        persistHistoryAndStats(game, player, null, "LOSS", -5);
    }

    public void surrenderOnline(UUID gameId, User player) {
        Game game = gameRepo.findById(gameId).orElseThrow();
        if (game.getType() != Game.GameType.ONLINE) throw new IllegalStateException("Not an online game");

        game.setStatus(Game.GameStatus.FINISHED);

        boolean isHostSurrendering = game.getHost().equals(player);
        game.setResult(isHostSurrendering ? Game.GameResult.GUEST_WIN : Game.GameResult.HOST_WIN);
        game.setFinishedAt(OffsetDateTime.now());
        gameRepo.save(game);

        User winner = isHostSurrendering ? game.getGuest() : game.getHost();
        persistHistoryAndStats(game, winner, player, "WIN", +5);
        persistHistoryAndStats(game, player, winner, "LOSS", -5);

        try {
            Board hostBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, game.getHost().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Host board not found"));
            BoardModel hostModel = BoardModel.fromJson(hostBoard.getCells());

            Board guestBoard = null;
            BoardModel guestModel = null;
            if (game.getGuest() != null) {
                guestBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, game.getGuest().getId())
                        .orElse(null);
                if (guestBoard != null) {
                    guestModel = BoardModel.fromJson(guestBoard.getCells());
                }
            }

            AttackResult finalResult = new AttackResult();
            finalResult.setGameFinished(true);
            finalResult.setWinner(game.getResult() != null ? game.getResult().name() : null);
            finalResult.setCurrentTurn(game.getCurrentTurn() != null ? game.getCurrentTurn().name() : null);
            broadcastGameStateUpdate(gameId, finalResult, winner != null ? winner : player);
        } catch (Exception e) {
            log.error("Error sending final game state", e);
        }

        broadcastGameFinished(gameId, game);
    }

    private void persistHistoryAndStats(Game game, User player, User opponent, String result, int delta) {
        GameHistory gh = GameHistory.builder()
                .game(game)
                .player(player)
                .opponent(opponent)
                .result(result)
                .deltaRating(delta)
                .build();
        historyRepo.save(gh);

        if ("WIN".equals(result)) {
            player.setWins((player.getWins() == null ? 0 : player.getWins()) + 1);
            int currentRating = player.getRating() == null ? 0 : player.getRating();
            player.setRating(currentRating + delta);
        } else if ("LOSS".equals(result)) {
            player.setLosses((player.getLosses() == null ? 0 : player.getLosses()) + 1);
            int currentRating = player.getRating() == null ? 0 : player.getRating();
            int newRating = Math.max(0, currentRating + delta); // Ensure rating doesn't go below 0
            player.setRating(newRating);
        }
        player.setUpdatedAt(OffsetDateTime.now());
        userRepo.save(player);
    }

    public Game getGameById(UUID id){
        return gameRepo.getReferenceById(id);
    }


    @Transactional
    public AttackResult attack(UUID gameId, String username, int x, int y) {
        User player = getPlayer(username);
        Game game = getGame(gameId);
        validateTurn(game, player);

        Board enemyBoard = getEnemyBoard(game, player);
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        BoardModel.ShotOutcome playerOutcome = enemyModel.shoot(x, y);
        enemyBoard.setCells(enemyModel.toJson());
        boardRepo.save(enemyBoard);

        if (playerOutcome.already) {
            AttackResult result = buildAttackResult(
                    BoardModel.fromJson(boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, player.getId()).get().getCells()),
                    enemyModel,
                    playerOutcome,
                    null, game
            );
            
            if (game.getType() == Game.GameType.ONLINE && !game.isBot()) {
                broadcastGameStateUpdate(gameId, result, player);
            }
            return result;
        }

        if (enemyModel.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setFinishedAt(OffsetDateTime.now());

            boolean isHostWinner = game.getHost().equals(player);
            game.setResult(isHostWinner ? Game.GameResult.HOST_WIN : Game.GameResult.GUEST_WIN);
            gameRepo.save(game);

            User opponent = isHostWinner ? game.getGuest() : game.getHost();
            persistHistoryAndStats(game, player, opponent, "WIN", +10);
            // У бота нет User, поэтому статистику проигравшего пишем только для онлайн-игры
            if (opponent != null) {
                persistHistoryAndStats(game, opponent, player, "LOSS", -10);
            }

            AttackResult result = buildAttackResult(
                    BoardModel.fromJson(boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, player.getId()).get().getCells()),
                    enemyModel,
                    playerOutcome,
                    null, game
            );
            if (game.getType() == Game.GameType.ONLINE && !game.isBot()) {
                broadcastGameStateUpdate(gameId, result, player);
            }
            return result;
        }

        BotMove lastBotMove = null;

        if (game.isBot() && (!playerOutcome.hit)) {
            Board playerBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(game.getId(), player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
            BoardModel playerModel = BoardModel.fromJson(playerBoard.getCells());

            BotAiService.BotMove botMove = botAi.nextMove(playerModel);
            BoardModel.ShotOutcome botOutcome = playerModel.shoot(botMove.x(), botMove.y());

            log.debug("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

            lastBotMove = new BotMove(botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

            if (playerModel.allShipsSunk()) {
                game.setStatus(Game.GameStatus.FINISHED);
                game.setResult(Game.GameResult.GUEST_WIN);
                game.setFinishedAt(OffsetDateTime.now());
                persistHistoryAndStats(game, player, null, "LOSS", -10);
            } else if (!botOutcome.hit) {
                game.setCurrentTurn(Game.Turn.HOST);
            } else {
                // Бот сохраняет ход при попадании (даже при потоплении)
                game.setCurrentTurn(Game.Turn.GUEST);
            }

            playerBoard.setCells(playerModel.toJson());
            boardRepo.save(playerBoard);
        } else if (!playerOutcome.hit) {
            switchTurn(game);
        }

        gameRepo.save(game);

        Board playerBoardEntity = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, player.getId())
                .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
        BoardModel playerModel = BoardModel.fromJson(playerBoardEntity.getCells());

        AttackResult result = buildAttackResult(playerModel, enemyModel, playerOutcome, lastBotMove, game);

        if (game.getType() == Game.GameType.ONLINE && !game.isBot()) {
            broadcastGameStateUpdate(gameId, result, player);
        }

        return result;
    }

    private User getPlayer(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private Game getGame(UUID gameId) {
        return gameRepo.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Game not found"));
    }

    private void validateTurn(Game game, User player) {
        Game.Turn playerTurn = game.getHost().getId().equals(player.getId()) ? Game.Turn.HOST : Game.Turn.GUEST;
        if (!playerTurn.equals(game.getCurrentTurn())) {
            throw new IllegalStateException("Сейчас не ваш ход");
        }
    }

    private Board getEnemyBoard(Game game, User player) {
        if (game.isBot()) {
            return boardRepo.findByGameIdAndPlayerIsNull(game.getId())
                    .orElseGet(() -> {
                        BoardModel botModel = BoardModel.autoPlaceRandom();
                        Board botBoard = Board.builder()
                                .game(game)
                                .player(null)
                                .cells(botModel.toJson())
                                .build();
                        return boardRepo.save(botBoard);
                    });
        } else {
            return boardRepo.findByGameIdAndPlayerIdNot(game.getId(), player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Противник ещё не подключился"));
        }
    }

    private void switchTurn(Game game) {
        game.setCurrentTurn(game.getCurrentTurn() == Game.Turn.HOST ? Game.Turn.GUEST : Game.Turn.HOST);
    }

    public AttackResult botMove(UUID gameId) {
        Game game = getGame(gameId);
        if (!game.isBot() || game.getCurrentTurn() != Game.Turn.GUEST) {
            throw new IllegalStateException("Not bot's turn");
        }

        Board playerBoard = boardRepo.findByGameIdAndPlayerIdIsNotNull(gameId)
                .orElseThrow(() -> new RuntimeException("Несколько досок с одинаковым id игры и без игрока"));
        BoardModel playerModel = BoardModel.fromJson(playerBoard.getCells());

        BotAiService.BotMove botMove = botAi.nextMove(playerModel);
        BoardModel.ShotOutcome botOutcome = playerModel.shoot(botMove.x(), botMove.y());

        log.debug("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

        if (playerModel.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setResult(Game.GameResult.GUEST_WIN);
            game.setFinishedAt(OffsetDateTime.now());
            persistHistoryAndStats(game, playerBoard.getPlayer(), null, "LOSS", -10);
        } else if (!botOutcome.hit) {
            game.setCurrentTurn(Game.Turn.HOST);
        } else {
            // Бот сохраняет ход при попадании (даже при потоплении)
            game.setCurrentTurn(Game.Turn.GUEST);
        }

        playerBoard.setCells(playerModel.toJson());
        boardRepo.save(playerBoard);
        gameRepo.save(game);

        Board enemyBoard = boardRepo.findByGameIdAndPlayerIsNull(gameId).orElseThrow();
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        BotMove gameServiceBotMove = new BotMove(botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

        return buildAttackResult(playerModel, enemyModel, null, gameServiceBotMove, game);
    }


    private AttackResult buildAttackResult(BoardModel playerModel, BoardModel enemyModel,
                                           BoardModel.ShotOutcome outcome, BotMove botMove,
                                           Game game) {
        AttackResult result = new AttackResult();

        result.setPlayerBoard(playerModel.toIntArray(true));
        result.setEnemyBoard(enemyModel.toIntArray(false));

        if (outcome != null) {
            result.setHit(outcome.hit);
            result.setSunk(outcome.sunk);
            result.setAlready(outcome.already);
        } else {
            result.setHit(false);
            result.setSunk(false);
            result.setAlready(false);
        }

        if (botMove != null) {
            result.setBotX(botMove.getX());
            result.setBotY(botMove.getY());
            result.setBotHit(botMove.isHit());
            result.setBotSunk(botMove.isSunk());
        }

        result.setGameFinished(game.getStatus() == Game.GameStatus.FINISHED);
        result.setWinner(
                game.getResult() == null
                        ? null
                        : game.getResult().name()
        );
        result.setCurrentTurn(game.getCurrentTurn() != null ? game.getCurrentTurn().name() : null);

        return result;
    }

    public static class BotMove {
        private final int x, y;
        private final boolean hit, sunk;

        public BotMove(int x, int y, boolean hit, boolean sunk) {
            this.x = x;
            this.y = y;
            this.hit = hit;
            this.sunk = sunk;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public boolean isHit() { return hit; }
        public boolean isSunk() { return sunk; }
    }

    /**
     * Broadcast game state update to all players in an online game via WebSocket
     * AttackResult contains boards from attacker's perspective:
     * - playerBoard: attacker's own board (no hit mark)
     * - enemyBoard: defender's board (with hit mark if hit occurred)
     * @param attacker The player who made the attack
     */
    private void broadcastGameStateUpdate(UUID gameId, AttackResult result, User attacker) {
        try {
            Game game = gameRepo.findById(gameId).orElse(null);
            if (game == null || game.getType() != Game.GameType.ONLINE) {
                return;
            }

            Map<String, Object> baseMessage = new HashMap<>();
            baseMessage.put("type", "gameStateUpdate");
            baseMessage.put("gameId", gameId.toString());
            baseMessage.put("currentTurn", game.getCurrentTurn() != null ? game.getCurrentTurn().name() : null);
            baseMessage.put("gameFinished", game.getStatus() == Game.GameStatus.FINISHED);
            baseMessage.put("winner", game.getResult() != null ? game.getResult().name() : null);
            baseMessage.put("hit", result.isHit());
            baseMessage.put("sunk", result.isSunk());
            baseMessage.put("already", result.isAlready());

            Board hostBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, game.getHost().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Host board not found"));
            BoardModel hostModel = BoardModel.fromJson(hostBoard.getCells());

            Board guestBoard = null;
            BoardModel guestModel = null;
            if (game.getGuest() != null) {
                guestBoard = boardRepo.findFirstByGameIdAndPlayerIdOrderByIdAsc(gameId, game.getGuest().getId())
                        .orElse(null);
                if (guestBoard != null) {
                    guestModel = BoardModel.fromJson(guestBoard.getCells());
                }
            }

            Map<String, Object> hostMessage = new HashMap<>(baseMessage);
            hostMessage.put("playerBoard", convertToLists(hostModel.toIntArray(true)));
            if (guestModel != null) {
                hostMessage.put("enemyBoard", convertToLists(guestModel.toIntArray(false)));
            }

            Map<String, Object> guestMessage = new HashMap<>(baseMessage);
            if (guestModel != null) {
                guestMessage.put("playerBoard", convertToLists(guestModel.toIntArray(true)));
            }
            guestMessage.put("enemyBoard", convertToLists(hostModel.toIntArray(false)));

            gameWebSocketHandler.sendToUser(gameId, game.getHost().getUsername(), hostMessage);
            if (game.getGuest() != null) {
                gameWebSocketHandler.sendToUser(gameId, game.getGuest().getUsername(), guestMessage);
            }
        } catch (Exception e) {
            log.error("Error broadcasting game state update", e);
        }
    }

    private void broadcastGameFinished(UUID gameId, Game game) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "gameFinished");
            message.put("gameId", gameId.toString());
            message.put("winner", game.getResult() != null ? game.getResult().name() : null);
            message.put("gameFinished", true);

            gameWebSocketHandler.broadcastToGame(gameId, message);
        } catch (Exception e) {
            log.error("Error broadcasting game finished", e);
        }
    }

    private List<List<Integer>> convertToLists(int[][] array) {
        List<List<Integer>> result = new java.util.ArrayList<>();
        for (int[] row : array) {
            List<Integer> rowList = new java.util.ArrayList<>();
            for (int cell : row) {
                rowList.add(cell);
            }
            result.add(rowList);
        }
        return result;
    }
}
