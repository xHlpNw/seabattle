package com.seabattle.server.service;

import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for rematch requests and creation of rematch games.
 * One pending request per finished game; requester is stored by username.
 */
@Service
@RequiredArgsConstructor
public class RematchService {

    private static final Logger log = LoggerFactory.getLogger(RematchService.class);

    private final GameRepository gameRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    private final Map<UUID, RematchRequest> pendingByGameId = new ConcurrentHashMap<>();

    public static final class RematchRequest {
        private final String requestedByUsername;

        public RematchRequest(String requestedByUsername) {
            this.requestedByUsername = requestedByUsername;
        }

        public String getRequestedByUsername() {
            return requestedByUsername;
        }
    }

    /**
     * Request a rematch for a finished online game. Caller must be a participant.
     * Overwrites any existing request for this game.
     *
     * @param finishedGameId id of the finished game
     * @param requesterUsername username of the player requesting rematch
     * @return username of the other player (to receive the proposal), or null if game invalid
     */
    public String requestRematch(UUID finishedGameId, String requesterUsername) {
        Game game = gameRepository.findById(finishedGameId).orElse(null);
        if (game == null || game.getType() != Game.GameType.ONLINE || game.getStatus() != Game.GameStatus.FINISHED) {
            log.warn("Rematch request for invalid game: {}", finishedGameId);
            return null;
        }
        User requester = userRepository.findByUsername(requesterUsername).orElse(null);
        if (requester == null) return null;
        boolean isHost = game.getHost().getId().equals(requester.getId());
        boolean isGuest = game.getGuest() != null && game.getGuest().getId().equals(requester.getId());
        if (!isHost && !isGuest) return null;

        pendingByGameId.put(finishedGameId, new RematchRequest(requesterUsername));
        String other = isHost ? (game.getGuest() != null ? game.getGuest().getUsername() : null) : game.getHost().getUsername();
        log.info("Rematch requested for game {} by {}, notifying {}", finishedGameId, requesterUsername, other);
        return other;
    }

    /**
     * Accept rematch: create a new game with same host/guest and two empty boards.
     * Caller must be the other player (not the requester).
     *
     * @param finishedGameId id of the finished game that had the request
     * @param accepterUsername username of the player accepting
     * @return new game id, or null if invalid
     */
    @Transactional
    public UUID acceptRematch(UUID finishedGameId, String accepterUsername) {
        RematchRequest request = pendingByGameId.remove(finishedGameId);
        if (request == null) {
            log.warn("No rematch request for game {}", finishedGameId);
            return null;
        }
        if (request.getRequestedByUsername().equals(accepterUsername)) {
            log.warn("Accepter cannot be the same as requester");
            pendingByGameId.put(finishedGameId, request); // restore
            return null;
        }

        Game oldGame = gameRepository.findById(finishedGameId).orElseThrow(() -> new EntityNotFoundException("Game not found"));
        if (oldGame.getType() != Game.GameType.ONLINE || oldGame.getStatus() != Game.GameStatus.FINISHED || oldGame.getGuest() == null) {
            return null;
        }

        User accepter = userRepository.findByUsername(accepterUsername).orElse(null);
        if (accepter == null) return null;
        boolean accepterIsGuest = oldGame.getGuest().getId().equals(accepter.getId());
        if (!accepterIsGuest && !oldGame.getHost().getId().equals(accepter.getId())) {
            return null;
        }

        // Как в RoomController.startGame: currentTurn не задаём — выставится в markReady() когда оба нажмут «Готов»
        Game newGame = Game.builder()
                .type(Game.GameType.ONLINE)
                .host(oldGame.getHost())
                .guest(oldGame.getGuest())
                .status(Game.GameStatus.IN_PROGRESS)
                .roomToken(oldGame.getRoomToken())
                .startedAt(OffsetDateTime.now())
                .build();
        gameRepository.save(newGame);

        Board hostBoard = Board.builder()
                .game(newGame)
                .player(oldGame.getHost())
                .cells(new BoardModel().toJson())
                .build();
        Board guestBoard = Board.builder()
                .game(newGame)
                .player(oldGame.getGuest())
                .cells(new BoardModel().toJson())
                .build();
        boardRepository.save(hostBoard);
        boardRepository.save(guestBoard);

        log.info("Rematch game created: {} for finished game {}", newGame.getId(), finishedGameId);
        return newGame.getId();
    }

    /**
     * Decline rematch. Returns the requester's username so the handler can notify them.
     */
    public String declineRematch(UUID finishedGameId) {
        RematchRequest request = pendingByGameId.remove(finishedGameId);
        return request != null ? request.getRequestedByUsername() : null;
    }

    public RematchRequest getRequest(UUID gameId) {
        return pendingByGameId.get(gameId);
    }
}
