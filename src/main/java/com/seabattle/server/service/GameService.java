package com.seabattle.server.service;

import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.engine.BotAiService;
import com.seabattle.server.entity.*;
import com.seabattle.server.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
                .build();
        gameRepo.save(g);

        return g;
    }

    @Transactional
    public int[][] placeShipsAuto(UUID gameId, UUID playerId) throws Exception {
        Game g = gameRepo.findById(gameId).orElseThrow();
        Board board = boardRepo.findByGameIdAndPlayerId(gameId, playerId)
                .orElseGet(() -> {
                    Board newBoard = Board.builder()
                            .game(gameRepo.findById(gameId).orElseThrow(() -> new RuntimeException("Game not found")))
                            .player(userRepo.findById(playerId).orElseThrow(() -> new RuntimeException("User not found")))
                            .cells("empty") // или ваш формат пустого поля
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

        return bm.toIntArray();
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

        // player missed -> bot moves (bot may hit and continue while hitting)
        BoardModel playerBm = BoardModel.fromJson(playerBoard.getCells());
        boolean botKeepsShooting = true;
        while (botKeepsShooting) {
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

            if (botOutcome.already) {
                // already tried cell — pick next
                botKeepsShooting = true;
            } else if (botOutcome.hit) {
                // bot hit -> by rule A bot continues to shoot
                botKeepsShooting = true;
            } else {
                // bot missed -> stop
                botKeepsShooting = false;
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
        }

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

}
