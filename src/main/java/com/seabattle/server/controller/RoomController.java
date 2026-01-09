package com.seabattle.server.controller;

import com.seabattle.server.dto.RoomResponseDTO;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.Room;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.RoomRepository;
import com.seabattle.server.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final BoardRepository boardRepository;

    private String buildShareableLink(UUID roomToken) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();

        String scheme = request.getScheme(); // http or https
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        // For development, assume frontend is on port 4200, but use the same host
        String frontendPort = (serverPort == 8080) ? "4200" : String.valueOf(serverPort);

        // If running on standard ports, don't include port in URL
        String portPart = "";
        if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
            portPart = ":" + frontendPort;
        }

        return scheme + "://" + serverName + portPart + "/lobby/join/" + roomToken;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        System.out.println("createRoom called by user: " + user.getUsername());

        // Always create new room (no checking for existing active rooms)
        Room room = new Room();
        room.setHost(user);
        room.setToken(UUID.randomUUID());
        room.setStatus("WAITING");
        room.setCreatedAt(OffsetDateTime.now());
        room.setExpiresAt(OffsetDateTime.now().plusDays(1)); // 24 hours for testing

        roomRepository.save(room);

        System.out.println("Room created with token: " + room.getToken() + ", host: " + user.getUsername());

        return ResponseEntity.ok(Map.of(
                "roomToken", room.getToken(),
                "shareableLink", buildShareableLink(room.getToken()),
                "message", "Room created successfully"
        ));
    }

    @PostMapping("/join/{token}")
    public ResponseEntity<?> joinRoom(@PathVariable UUID token,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Room room = roomRepository.findByToken(token);
        if (room == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room not found"));
        }

        if ("EXPIRED".equals(room.getStatus()) || room.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room has expired"));
        }

        if (room.getHost().equals(user)) {
            return ResponseEntity.badRequest().body(Map.of("message", "You cannot join your own room"));
        }

        // Check if a game already exists for this room
        Game existingGame = gameRepository.findByRoomToken(token).stream().findFirst().orElse(null);
        if (existingGame != null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Successfully joined room",
                    "hostUsername", room.getHost().getUsername(),
                    "roomToken", room.getToken(),
                    "gameId", existingGame.getId()
            ));
        }

        // Set guest on room (don't create game yet)
        room.setGuest(user);
        roomRepository.save(room);

        // Player joined successfully (no WebSocket notification needed)
        System.out.println("👥 Player " + user.getUsername() + " joined room " + room.getToken());

        return ResponseEntity.ok(Map.of(
                "message", "Successfully joined room",
                "hostUsername", room.getHost().getUsername(),
                "roomToken", room.getToken()
        ));
    }

    @PostMapping("/start/{token}")
    public ResponseEntity<?> startGame(@PathVariable UUID token,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Room room = roomRepository.findByToken(token);
        if (room == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room not found"));
        }

        if (!room.getHost().equals(user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Only room host can start the game"));
        }

        if (room.getGuest() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cannot start game without opponent"));
        }

        // Check if a game already exists for this room
        Game existingGame = gameRepository.findByRoomToken(token).stream().findFirst().orElse(null);
        if (existingGame != null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Game already started",
                    "gameId", existingGame.getId()
            ));
        }

        // Create online game with both players
        Game game = Game.builder()
                .type(Game.GameType.ONLINE)
                .host(room.getHost())
                .guest(room.getGuest())
                .status(Game.GameStatus.IN_PROGRESS)
                .roomToken(token)
                .startedAt(OffsetDateTime.now())
                .build();

        gameRepository.save(game);

        // Create empty boards for both players
        Board hostBoard = Board.builder()
                .game(game)
                .player(room.getHost())
                .cells(new BoardModel().toJson())
                .build();

        Board guestBoard = Board.builder()
                .game(game)
                .player(room.getGuest())
                .cells(new BoardModel().toJson())
                .build();

        boardRepository.save(hostBoard);
        boardRepository.save(guestBoard);

        // Update room status to indicate game has started
        room.setStatus("IN_GAME");
        roomRepository.save(room);

        return ResponseEntity.ok(Map.of(
                "message", "Game started successfully",
                "gameId", game.getId()
        ));
    }

    @GetMapping("/{token}")
    public ResponseEntity<RoomResponseDTO> getRoomStatus(@PathVariable UUID token,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        System.out.println("getRoomStatus called by user: " + user.getUsername() + " for room token: " + token);

        Room room = roomRepository.findByToken(token);
        if (room == null) {
            System.out.println("Room not found for token: " + token);
            return ResponseEntity.notFound().build();
        }

        System.out.println("Room found: host=" + room.getHost().getUsername() + ", guest=" + (room.getGuest() != null ? room.getGuest().getUsername() : "null"));

        // Check if user is a participant in this room
        boolean isHost = room.getHost().equals(user);
        boolean isGuest = room.getGuest() != null && room.getGuest().equals(user);
        if (!isHost && !isGuest) {
            System.out.println("Access denied: user " + user.getUsername() + " is not a participant in room " + token + ", host: " + room.getHost().getUsername() + ", guest: " + (room.getGuest() != null ? room.getGuest().getUsername() : "null"));
            return ResponseEntity.status(403).build();
        }

        boolean isExpired = room.getExpiresAt().isBefore(OffsetDateTime.now());
        String guestUsername = room.getGuest() != null ? room.getGuest().getUsername() : null;

        // Get game ID if game exists for this room
        Game game = gameRepository.findByRoomToken(token).stream().findFirst().orElse(null);
        UUID gameId = game != null ? game.getId() : null;

        RoomResponseDTO response = new RoomResponseDTO(
                room.getToken(),
                isExpired ? "EXPIRED" : room.getStatus(),
                room.getHost().getUsername(),
                guestUsername,
                isHost,
                room.getCreatedAt(),
                room.getExpiresAt(),
                isExpired,
                gameId
        );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<?> deleteRoom(@PathVariable UUID token,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Room room = roomRepository.findByToken(token);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }

        if (!room.getHost().equals(user)) {
            return ResponseEntity.status(403).body(Map.of("message", "Only room host can delete the room"));
        }

        roomRepository.delete(room);
        return ResponseEntity.ok(Map.of("message", "Room deleted successfully"));
    }

}


