package com.seabattle.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;

    // Store sessions by room token
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Room WebSocket connection established: sessionId={}", session.getId());
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
                case "join":
                    handleJoin(session, payload);
                    break;
                case "status":
                    handleStatus(session, payload);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload) {
        String roomToken = (String) payload.get("roomToken");
        if (roomToken != null) {
            roomSessions.computeIfAbsent(roomToken, k -> new CopyOnWriteArraySet<>()).add(session);
            log.debug("Session {} subscribed to room {}", session.getId(), roomToken);
        }
    }

    private void handleJoin(WebSocketSession session, Map<String, Object> payload) {
        String roomToken = (String) payload.get("roomToken");
        if (roomToken != null) {
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                User user = userRepository.findByUsername(auth.getName()).orElse(null);
                if (user != null) {
                    // Broadcast to all subscribers of this room
                    broadcastToRoom(roomToken, Map.of(
                        "type", "playerJoined",
                        "username", user.getUsername()
                    ));
                }
            }
        }
    }

    private void handleStatus(WebSocketSession session, Map<String, Object> payload) {
        String roomToken = (String) payload.get("roomToken");
        String status = (String) payload.get("status");
        if (roomToken != null && status != null) {
            broadcastToRoom(roomToken, Map.of(
                "type", "statusUpdate",
                "status", status
            ));
        }
    }

    public void broadcastToRoom(String roomToken, Object message) {
        log.debug("Broadcasting to room {}: {}", roomToken, message);
        CopyOnWriteArraySet<WebSocketSession> sessions = roomSessions.get(roomToken);
        if (sessions != null) {
            log.debug("Found {} sessions in room {}", sessions.size(), roomToken);
            sessions.forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                        log.trace("Message sent to session {}", session.getId());
                    } catch (IOException e) {
                        log.warn("Error sending message to session {}: {}", session.getId(), e.getMessage());
                    }
                } else {
                    log.debug("Session {} is not open", session.getId());
                }
            });
        } else {
            log.debug("No sessions found for room {}", roomToken);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Remove session from all rooms
        roomSessions.forEach((roomToken, sessions) -> {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomToken);
            }
        });
        log.info("Room WebSocket connection closed: sessionId={}", session.getId());
    }
}