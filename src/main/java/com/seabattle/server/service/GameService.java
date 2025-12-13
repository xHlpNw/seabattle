package com.seabattle.server.service;

import com.seabattle.server.dto.AttackResult;
import com.seabattle.server.dto.AutoPlaceResponse;
import com.seabattle.server.dto.ShipDTO;
import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.engine.BotAiService;
import com.seabattle.server.entity.*;
import com.seabattle.server.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepo;
    private final BoardRepository boardRepo;
    private final MoveRepository moveRepo;
    private final GameHistoryRepository historyRepo;
    private final UserRepository userRepo;
    private final BotAiService botAi;

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

        return g;
    }

    @Transactional
    public AutoPlaceResponse placeShipsAuto(UUID gameId, UUID playerId) throws Exception {
        Game g = gameRepo.findById(gameId).orElseThrow();
        Board board = boardRepo.findByGameIdAndPlayerId(gameId, playerId)
                .orElseGet(() -> {
                    Board newBoard = Board.builder()
                            .game(g)
                            .player(userRepo.findById(playerId).orElseThrow())
                            .cells("empty")
                            .build();
                    return boardRepo.save(newBoard);
                });

        // Генерируем новую доску с кораблями
        BoardModel bm = BoardModel.autoPlaceRandom();

        // Сохраняем в БД
        board.setCells(bm.toJson());
        boardRepo.save(board);

        // Если игра только ждёт — запускаем
        if (g.getStatus() == Game.GameStatus.WAITING) {
            g.setStatus(Game.GameStatus.IN_PROGRESS);
            g.setStartedAt(OffsetDateTime.now());
            gameRepo.save(g);
        }

        // Возвращаем список кораблей с их shipId
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
        Board board = boardRepo.findByGameIdAndPlayerId(gameId, playerId).orElseThrow();
        BoardModel bm = BoardModel.fromJson(cellsJson); // validate
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

        Board playerBoard = boardRepo.findByGameIdAndPlayerId(gameId, playerId).orElseThrow();
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

        // if player sunk all -> player wins
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

        // If player hit -> per rule A, player continues (we return without bot move)
        if (playerOutcome.hit) {
            dto.setGameOver(false);
            dto.setMessage("Hit — it's still your turn");
            return dto;
        }

        // player missed -> bot moves (only one shot per turn)
        BoardModel playerBm = BoardModel.fromJson(playerBoard.getCells());
        BotAiService.BotMove botMove = botAi.nextMove(playerBm);
        BoardModel.ShotOutcome botOutcome = playerBm.shoot(botMove.x(), botMove.y());

        System.out.println(String.format("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk));

        Move botMoveEntity = Move.builder()
                .game(game)
                .player(null)
                .x((short)botMove.x())
                .y((short)botMove.y())
                .hit(botOutcome.hit)
                .build();
        moveRepo.save(botMoveEntity);

        if (!botOutcome.already) {
            // update board
            playerBoard.setCells(playerBm.toJson());
            boardRepo.save(playerBoard);
        }

        // Check if bot won
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

        // Turn management: if bot hit and didn't sink, bot keeps turn; otherwise player gets turn back
        if (botOutcome.hit && !botOutcome.sunk) {
            game.setCurrentTurn(Game.Turn.GUEST); // bot's turn
            dto.setMessage("Bot hit — bot's turn again.");
        } else {
            game.setCurrentTurn(Game.Turn.HOST); // player's turn
            dto.setMessage("Bot " + (botOutcome.hit ? "sunk a ship" : "missed") + " — your turn.");
        }
        gameRepo.save(game);

        // game continues
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

    @Transactional
    public Game rematch(UUID gameId, UUID playerId) throws Exception {
        var host = userRepo.findById(playerId).orElseThrow();
        return createBotGame(host);
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
            player.setRating((player.getRating() == null ? 1000 : player.getRating()) + delta);
        } else if ("LOSS".equals(result)) {
            player.setLosses((player.getLosses() == null ? 0 : player.getLosses()) + 1);
            player.setRating((player.getRating() == null ? 1000 : player.getRating()) + delta);
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

        // Получаем доску противника
        Board enemyBoard = getEnemyBoard(game, player);
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        // Ход игрока
        BoardModel.ShotOutcome playerOutcome = enemyModel.shoot(x, y);
        enemyBoard.setCells(enemyModel.toJson());
        boardRepo.save(enemyBoard);

        if (playerOutcome.already) {
            return buildAttackResult(
                    BoardModel.fromJson(boardRepo.findByGameIdAndPlayerId(gameId, player.getId()).get().getCells()),
                    enemyModel,
                    playerOutcome,
                    null, game
            );
        }

        // Проверяем победу игрока
        if (enemyModel.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setResult(Game.GameResult.HOST_WIN);
            game.setFinishedAt(OffsetDateTime.now());
            gameRepo.save(game);

            persistHistoryAndStats(game, player, null, "WIN", +10);

            return buildAttackResult(
                    BoardModel.fromJson(boardRepo.findByGameIdAndPlayerId(gameId, player.getId()).get().getCells()),
                    enemyModel,
                    playerOutcome,
                    null, game
            );
        }

        BotMove lastBotMove = null;

        // Передача хода боту, если игра с ботом и игрок промахнулся или потопил корабль
        if (game.isBot() && (!playerOutcome.hit)) {
            Board playerBoard = boardRepo.findByGameIdAndPlayerId(game.getId(), player.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
            BoardModel playerModel = BoardModel.fromJson(playerBoard.getCells());

            // Bot makes intelligent shot using probability map AI
            BotAiService.BotMove botMove = botAi.nextMove(playerModel);
            BoardModel.ShotOutcome botOutcome = playerModel.shoot(botMove.x(), botMove.y());

            System.out.println(String.format("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk));

            // Convert to GameService.BotMove for compatibility
            lastBotMove = new BotMove(botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

            // Проверка победы бота
            if (playerModel.allShipsSunk()) {
                game.setStatus(Game.GameStatus.FINISHED);
                game.setResult(Game.GameResult.GUEST_WIN);
                game.setFinishedAt(OffsetDateTime.now());
                persistHistoryAndStats(game, player, null, "LOSS", -10);
            } else if (!botOutcome.hit) {
                // Bot missed - player's turn
                game.setCurrentTurn(Game.Turn.HOST);
            } else {
                // Bot hit (even if sunk) - bot keeps turn
                game.setCurrentTurn(Game.Turn.GUEST);
            }

            playerBoard.setCells(playerModel.toJson());
            boardRepo.save(playerBoard);
        } else if (!playerOutcome.hit) {
            // Игра с человеком — переключаем ход
            switchTurn(game);
        }

        gameRepo.save(game);

        // Возвращаем доску игрока
        Board playerBoardEntity = boardRepo.findByGameIdAndPlayerId(gameId, player.getId())
                .orElseThrow(() -> new EntityNotFoundException("Доска игрока не найдена"));
        BoardModel playerModel = BoardModel.fromJson(playerBoardEntity.getCells());

        return buildAttackResult(playerModel, enemyModel, playerOutcome, lastBotMove, game);
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

    // Переписанный метод makeBotMove для поддержки повторных ходов
    private BotMove makeBotMove(BoardModel playerModel, Board playerBoard) {
        Random rnd = new Random();
        BoardModel.ShotOutcome botOutcome;
        int botX, botY;

        do {
            botX = rnd.nextInt(BoardModel.SIZE);
            botY = rnd.nextInt(BoardModel.SIZE);
            botOutcome = playerModel.shoot(botX, botY);
        } while (botOutcome.already);

        playerBoard.setCells(playerModel.toJson());
        return new BotMove(botX, botY, botOutcome.hit, botOutcome.sunk);
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

        System.out.println(String.format("Bot shoots at ({}, {}), hit: {}, sunk: {}", botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk));

        // Проверка победы
        if (playerModel.allShipsSunk()) {
            game.setStatus(Game.GameStatus.FINISHED);
            game.setResult(Game.GameResult.GUEST_WIN);
        } else if (!botOutcome.hit) {
            // бот промахнулся — ход возвращается игроку
            game.setCurrentTurn(Game.Turn.HOST);
        } else {
            // бот попал (даже если потопил корабль) — остаётся его ход для следующего запроса
            game.setCurrentTurn(Game.Turn.GUEST);
        }

        playerBoard.setCells(playerModel.toJson());
        boardRepo.save(playerBoard);
        gameRepo.save(game);

        Board enemyBoard = boardRepo.findByGameIdAndPlayerIsNull(gameId).orElseThrow();
        BoardModel enemyModel = BoardModel.fromJson(enemyBoard.getCells());

        // Convert BotAiService.BotMove to GameService.BotMove
        BotMove gameServiceBotMove = new BotMove(botMove.x(), botMove.y(), botOutcome.hit, botOutcome.sunk);

        return buildAttackResult(playerModel, enemyModel, null, gameServiceBotMove, game);
    }


    private AttackResult buildAttackResult(BoardModel playerModel, BoardModel enemyModel,
                                           BoardModel.ShotOutcome outcome, BotMove botMove,
                                           Game game) {
        AttackResult result = new AttackResult();

        result.setPlayerBoard(playerModel.toIntArray(true)); // кастомный метод для List<List<Integer>>
        result.setEnemyBoard(enemyModel.toIntArray(false)); // enemy — скрываем корабли

        // Only set outcome fields if outcome is not null (for player shots)
        if (outcome != null) {
            result.setHit(outcome.hit);
            result.setSunk(outcome.sunk);
            result.setAlready(outcome.already);
        } else {
            // For bot-only moves, set defaults
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

    @Getter
    @Setter
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
}
