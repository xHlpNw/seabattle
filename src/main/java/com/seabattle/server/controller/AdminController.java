package com.seabattle.server.controller;

import com.seabattle.server.dto.*;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.Room;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.RoomRepository;
import com.seabattle.server.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final RoomRepository roomRepository;

    @GetMapping("/stats")
    public AdminStatsDto getStats() {
        return new AdminStatsDto(
                userRepository.count(),
                userRepository.search(null, null, User.Status.ACTIVE, PageRequest.of(0, 1)).getTotalElements(),
                userRepository.search(null, null, User.Status.BLOCKED, PageRequest.of(0, 1)).getTotalElements(),
                gameRepository.countByStatus(Game.GameStatus.IN_PROGRESS),
                gameRepository.countByStatus(Game.GameStatus.FINISHED),
                roomRepository.countByStatus("WAITING")
        );
    }

    @GetMapping("/users")
    public Page<AdminUserDto> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.Status status
    ) {
        Page<User> users = userRepository.search(
                query,
                role,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return users.map(u -> new AdminUserDto(
                u.getId(),
                u.getUsername(),
                u.getRating() != null ? u.getRating() : 0,
                u.getWins() != null ? u.getWins() : 0,
                u.getLosses() != null ? u.getLosses() : 0,
                u.getRole().name(),
                u.getStatus().name(),
                u.getCreatedAt()
        ));
    }

    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateRoleRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        User actor = userRepository.findByUsername(currentUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        final User.Role newRole;
        try {
            newRole = User.Role.valueOf(request.role().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role"));
        }
        if (user.getUsername().equals(actor.getUsername()) && newRole != User.Role.ADMIN) {
            return ResponseEntity.badRequest().body(Map.of("message", "You cannot remove your own admin role"));
        }

        user.setRole(newRole);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Role updated"));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateStatusRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        User actor = userRepository.findByUsername(currentUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        final User.Status newStatus;
        try {
            newStatus = User.Status.valueOf(request.status().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status"));
        }
        if (user.getUsername().equals(actor.getUsername()) && newStatus == User.Status.BLOCKED) {
            return ResponseEntity.badRequest().body(Map.of("message", "You cannot block yourself"));
        }

        user.setStatus(newStatus);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Status updated"));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        User actor = userRepository.findByUsername(currentUser.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        if (request.rating() != null) user.setRating(Math.max(0, request.rating()));
        if (request.wins() != null) user.setWins(Math.max(0, request.wins()));
        if (request.losses() != null) user.setLosses(Math.max(0, request.losses()));

        if (request.role() != null && !request.role().isBlank()) {
            final User.Role newRole;
            try {
                newRole = User.Role.valueOf(request.role().toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid role"));
            }
            if (user.getUsername().equals(actor.getUsername()) && newRole != User.Role.ADMIN) {
                return ResponseEntity.badRequest().body(Map.of("message", "You cannot remove your own admin role"));
            }
            user.setRole(newRole);
        }

        if (request.status() != null && !request.status().isBlank()) {
            final User.Status newStatus;
            try {
                newStatus = User.Status.valueOf(request.status().toUpperCase());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid status"));
            }
            if (user.getUsername().equals(actor.getUsername()) && newStatus == User.Status.BLOCKED) {
                return ResponseEntity.badRequest().body(Map.of("message", "You cannot block yourself"));
            }
            user.setStatus(newStatus);
        }

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User updated"));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (user.getUsername().equals(currentUser.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "You cannot delete yourself"));
        }
        try {
            userRepository.delete(user);
            return ResponseEntity.ok(Map.of("message", "User deleted"));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "message", "Cannot delete user with related games or history"
            ));
        }
    }

    @GetMapping("/games")
    public Page<AdminGameDto> getGames(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Game.GameStatus status
    ) {
        Page<Game> games = status == null
                ? gameRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                : gameRepository.findByStatus(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return games.map(g -> new AdminGameDto(
                g.getId(),
                g.getType().name(),
                g.getStatus().name(),
                g.getHost() != null ? g.getHost().getUsername() : null,
                g.getGuest() != null ? g.getGuest().getUsername() : null,
                g.isBot(),
                g.getCreatedAt(),
                g.getStartedAt(),
                g.getFinishedAt()
        ));
    }

    @PostMapping("/games/{gameId}/terminate")
    public ResponseEntity<?> terminateGame(@PathVariable UUID gameId) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new EntityNotFoundException("Game not found"));
        game.setStatus(Game.GameStatus.FINISHED);
        if (game.getFinishedAt() == null) {
            game.setFinishedAt(OffsetDateTime.now());
        }
        gameRepository.save(game);
        return ResponseEntity.ok(Map.of("message", "Game terminated"));
    }

    @PatchMapping("/games/{gameId}")
    public ResponseEntity<?> updateGame(@PathVariable UUID gameId, @RequestBody AdminUpdateGameRequest request) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new EntityNotFoundException("Game not found"));

        if (request.status() != null && !request.status().isBlank()) {
            try {
                Game.GameStatus status = Game.GameStatus.valueOf(request.status().toUpperCase());
                game.setStatus(status);
                if (status == Game.GameStatus.FINISHED && game.getFinishedAt() == null) {
                    game.setFinishedAt(OffsetDateTime.now());
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid status"));
            }
        }

        if (request.result() != null && !request.result().isBlank()) {
            try {
                game.setResult(Game.GameResult.valueOf(request.result().toUpperCase()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid result"));
            }
        }

        if (request.currentTurn() != null && !request.currentTurn().isBlank()) {
            try {
                game.setCurrentTurn(Game.Turn.valueOf(request.currentTurn().toUpperCase()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid currentTurn"));
            }
        }

        gameRepository.save(game);
        return ResponseEntity.ok(Map.of("message", "Game updated"));
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<?> deleteGame(@PathVariable UUID gameId) {
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new EntityNotFoundException("Game not found"));
        try {
            gameRepository.delete(game);
            return ResponseEntity.ok(Map.of("message", "Game deleted"));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of(
                    "message", "Cannot delete game with related entities"
            ));
        }
    }

    @GetMapping("/rooms")
    public Page<AdminRoomDto> getRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        Page<Room> rooms = (status == null || status.isBlank())
                ? roomRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                : roomRepository.findByStatus(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return rooms.map(r -> new AdminRoomDto(
                r.getId(),
                r.getToken(),
                r.getStatus(),
                r.getHost() != null ? r.getHost().getUsername() : null,
                r.getGuest() != null ? r.getGuest().getUsername() : null,
                r.getCreatedAt(),
                r.getExpiresAt()
        ));
    }

    @PatchMapping("/rooms/{roomId}")
    public ResponseEntity<?> updateRoom(@PathVariable UUID roomId, @RequestBody AdminUpdateRoomRequest request) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new EntityNotFoundException("Room not found"));

        if (request.status() != null && !request.status().isBlank()) {
            room.setStatus(request.status().toUpperCase());
        }
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                room.setExpiresAt(OffsetDateTime.parse(request.expiresAt()));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid expiresAt format"));
            }
        }

        roomRepository.save(room);
        return ResponseEntity.ok(Map.of("message", "Room updated"));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable UUID roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new EntityNotFoundException("Room not found"));
        roomRepository.delete(room);
        return ResponseEntity.ok(Map.of("message", "Room deleted"));
    }
}
