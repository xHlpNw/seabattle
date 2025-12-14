package com.seabattle.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;

    // Store sessions by room token
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    public RoomWebSocketHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WebSocket connection established: " + session.getId());
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
                    System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error handling WebSocket message: " + e.getMessage());
        }
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload) {
        String roomToken = (String) payload.get("roomToken");
        if (roomToken != null) {
            roomSessions.computeIfAbsent(roomToken, k -> new CopyOnWriteArraySet<>()).add(session);
            System.out.println("Session " + session.getId() + " subscribed to room " + roomToken);
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

    private void broadcastToRoom(String roomToken, Object message) {
        CopyOnWriteArraySet<WebSocketSession> sessions = roomSessions.get(roomToken);
        if (sessions != null) {
            sessions.forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                    } catch (IOException e) {
                        System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
                    }
                }
            });
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
        System.out.println("WebSocket connection closed: " + session.getId());
    }
}