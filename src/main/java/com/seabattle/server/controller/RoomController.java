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

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Check if user already has an active room
        Room existingRoom = roomRepository.findByHost(user).stream()
                .filter(room -> "WAITING".equals(room.getStatus()))
                .findFirst()
                .orElse(null);

        if (existingRoom != null) {
            return ResponseEntity.ok(Map.of(
                    "roomToken", existingRoom.getToken(),
                    "message", "You already have an active room"
            ));
        }

        // Create new room
        Room room = Room.builder()
                .host(user)
                .token(UUID.randomUUID())
                .status("WAITING")
                .build();

        roomRepository.save(room);

        return ResponseEntity.ok(Map.of(
                "roomToken", room.getToken(),
                "shareableLink", "http://localhost:4200/lobby?join=" + room.getToken(),
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

        // Create online game with both players
        Game game = Game.builder()
                .type(Game.GameType.ONLINE)
                .host(room.getHost())
                .guest(user)
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
                .player(user)
                .cells(new BoardModel().toJson())
                .build();

        boardRepository.save(hostBoard);
        boardRepository.save(guestBoard);

        // Update room status to indicate game has started
        room.setStatus("IN_GAME");
        roomRepository.save(room);

        return ResponseEntity.ok(Map.of(
                "message", "Successfully joined room and started game",
                "hostUsername", room.getHost().getUsername(),
                "roomToken", room.getToken(),
                "gameId", game.getId()
        ));
    }

    @GetMapping("/{token}")
    public ResponseEntity<RoomResponseDTO> getRoomStatus(@PathVariable UUID token,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Room room = roomRepository.findByToken(token);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isHost = room.getHost().equals(user);
        boolean isExpired = room.getExpiresAt().isBefore(OffsetDateTime.now());

        RoomResponseDTO response = new RoomResponseDTO(
                room.getToken(),
                isExpired ? "EXPIRED" : room.getStatus(),
                room.getHost().getUsername(),
                isHost,
                room.getCreatedAt(),
                room.getExpiresAt(),
                isExpired
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
