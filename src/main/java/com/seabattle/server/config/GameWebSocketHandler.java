package com.seabattle.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.RematchService;
import com.seabattle.server.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final JwtUtil jwtUtil;
    private final RematchService rematchService;

    private final Map<UUID, CopyOnWriteArraySet<WebSocketSession>> gameSessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(UserRepository userRepository, GameRepository gameRepository, JwtUtil jwtUtil, RematchService rematchService) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.jwtUtil = jwtUtil;
        this.rematchService = rematchService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Game WebSocket connection established: sessionId={}", session.getId());

        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                        String token = keyValue[1];
                        if (jwtUtil.validateToken(token)) {
                            String username = jwtUtil.extractUsername(token);
                            session.getAttributes().put("username", username);
                            log.debug("Authenticated WebSocket user: {}", username);
                        } else {
                            log.warn("Invalid token in WebSocket connection");
                            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case "subscribe":
                    handleSubscribe(session, payload);
                    break;
                case "rematchRequest":
                    handleRematchRequest(session, payload);
                    break;
                case "rematchAccept":
                    handleRematchAccept(session, payload);
                    break;
                case "rematchDecline":
                    handleRematchDecline(session, payload);
                    break;
                default:
                    log.warn("Unknown game message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling game WebSocket message", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload) {
        String gameIdStr = (String) payload.get("gameId");
        if (gameIdStr == null) {
            log.warn("Missing gameId in subscribe message");
            return;
        }

        try {
            UUID gameId = UUID.fromString(gameIdStr);
            String username = (String) session.getAttributes().get("username");
            if (username == null) {
                log.warn("User not authenticated in WebSocket session");
                sendError(session, "Authentication required");
                return;
            }

            if (!isUserParticipant(session, gameId)) {
                log.warn("User is not a participant in game {}", gameId);
                sendError(session, "You are not a participant in this game");
                return;
            }

            gameSessions.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session);
            session.getAttributes().put("gameId", gameId);
            log.debug("Session {} subscribed to game {}", session.getId(), gameId);

            sendMessage(session, Map.of("type", "subscribed", "gameId", gameIdStr));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid gameId format: {}", gameIdStr);
            sendError(session, "Invalid game ID format");
        }
    }

    private void handleRematchRequest(WebSocketSession session, Map<String, Object> payload) {
        String gameIdStr = (String) payload.get("gameId");
        String username = (String) session.getAttributes().get("username");
        if (gameIdStr == null || username == null) {
            sendError(session, "Missing gameId or not authenticated");
            return;
        }
        try {
            UUID gameId = UUID.fromString(gameIdStr);
            if (!isUserParticipant(session, gameId)) {
                sendError(session, "You are not a participant in this game");
                return;
            }
            String otherUsername = rematchService.requestRematch(gameId, username);
            if (otherUsername == null) {
                sendError(session, "Cannot request rematch for this game");
                return;
            }
            sendToUser(gameId, otherUsername, Map.of(
                "type", "rematchRequested",
                "gameId", gameIdStr,
                "requestedByUsername", username
            ));
            sendMessage(session, Map.of("type", "rematchRequestSent", "gameId", gameIdStr));
        } catch (IllegalArgumentException e) {
            sendError(session, "Invalid game ID format");
        }
    }

    private void handleRematchAccept(WebSocketSession session, Map<String, Object> payload) {
        String gameIdStr = (String) payload.get("gameId");
        String username = (String) session.getAttributes().get("username");
        if (gameIdStr == null || username == null) {
            sendError(session, "Missing gameId or not authenticated");
            return;
        }
        try {
            UUID gameId = UUID.fromString(gameIdStr);
            if (!isUserParticipant(session, gameId)) {
                sendError(session, "You are not a participant in this game");
                return;
            }
            UUID newGameId = rematchService.acceptRematch(gameId, username);
            if (newGameId == null) {
                sendError(session, "Cannot accept rematch");
                return;
            }
            broadcastToGame(gameId, Map.of(
                "type", "rematchAccepted",
                "gameId", gameIdStr,
                "newGameId", newGameId.toString()
            ));
        } catch (IllegalArgumentException e) {
            sendError(session, "Invalid game ID format");
        }
    }

    private void handleRematchDecline(WebSocketSession session, Map<String, Object> payload) {
        String gameIdStr = (String) payload.get("gameId");
        if (gameIdStr == null) {
            sendError(session, "Missing gameId");
            return;
        }
        try {
            UUID gameId = UUID.fromString(gameIdStr);
            String requestedBy = rematchService.declineRematch(gameId);
            if (requestedBy != null) {
                sendToUser(gameId, requestedBy, Map.of("type", "rematchDeclined", "gameId", gameIdStr));
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Invalid game ID format");
        }
    }

    private boolean isUserParticipant(WebSocketSession session, UUID gameId) {
        String username = (String) session.getAttributes().get("username");
        if (username == null) {
            return false;
        }

        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) return false;

            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null) return false;

            // Compare by ID to avoid EntityManager detachment issues
            boolean isHost = game.getHost().getId().equals(user.getId());
            boolean isGuest = game.getGuest() != null && game.getGuest().getId().equals(user.getId());
            return isHost || isGuest;
        } catch (Exception e) {
            log.warn("Error checking user participation: {}", e.getMessage());
            return false;
        }
    }

    public void broadcastToGame(UUID gameId, Object message) {
        log.debug("Broadcasting to game {}: {}", gameId, message);
        CopyOnWriteArraySet<WebSocketSession> sessions = gameSessions.get(gameId);
        if (sessions == null) {
            log.debug("No sessions found for game {}", gameId);
            return;
        }
        sessions.removeIf(s -> !s.isOpen());
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                } catch (IOException e) {
                    log.warn("Error sending message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        });
    }

    public void sendToUser(UUID gameId, String username, Object message) {
        CopyOnWriteArraySet<WebSocketSession> sessions = gameSessions.get(gameId);
        if (sessions == null) return;
        sessions.removeIf(s -> !s.isOpen());
        sessions.forEach(session -> {
            String sessionUsername = (String) session.getAttributes().get("username");
            if (session.isOpen() && username.equals(sessionUsername)) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                } catch (IOException e) {
                    log.warn("Error sending message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        });
    }

    private void sendMessage(WebSocketSession session, Object message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (IOException e) {
            log.warn("Error sending message: {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        sendMessage(session, Map.of("type", "error", "message", errorMessage));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        gameSessions.forEach((gameId, sessions) -> {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                gameSessions.remove(gameId);
            }
        });

        Object gameIdObj = session.getAttributes().get("gameId");
        if (gameIdObj instanceof UUID) {
            UUID gameId = (UUID) gameIdObj;
            CopyOnWriteArraySet<WebSocketSession> sessions = gameSessions.get(gameId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    gameSessions.remove(gameId);
                }
            }
        }

        log.info("Game WebSocket connection closed: sessionId={}", session.getId());
    }
}
