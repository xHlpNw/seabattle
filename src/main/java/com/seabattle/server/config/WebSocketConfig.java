package com.seabattle.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final GameWebSocketHandler gameWebSocketHandler;

    public WebSocketConfig(RoomWebSocketHandler roomWebSocketHandler, GameWebSocketHandler gameWebSocketHandler) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomWebSocketHandler, "/api/ws/room")
                .setAllowedOrigins("*");
        registry.addHandler(gameWebSocketHandler, "/api/ws/game")
                .setAllowedOrigins("*");
    }
}
