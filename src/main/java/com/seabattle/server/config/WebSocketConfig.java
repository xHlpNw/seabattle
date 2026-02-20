package com.seabattle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final GameWebSocketHandler gameWebSocketHandler;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler, GameWebSocketHandler gameWebSocketHandler) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = "*".equals(allowedOrigins.trim())
                ? new String[]{"*"}
                : allowedOrigins.trim().split("\\s*,\\s*");
        registry.addHandler(roomWebSocketHandler, "/api/ws/room")
                .setAllowedOrigins(origins);
        registry.addHandler(gameWebSocketHandler, "/api/ws/game")
                .setAllowedOrigins(origins);
    }
}
