package com.seabattle.server.controller;

import com.seabattle.server.dto.UserDto;
import com.seabattle.server.dto.UserProfileDTO;
import com.seabattle.server.dto.UserRankDTO;
import com.seabattle.server.dto.UserRatingDto;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.util.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDto userDto) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }
        User user = User.builder()
                .username(userDto.getUsername())
                .passwordHash(passwordEncoder.encode(userDto.getPassword()))
                .avatar(userDto.getAvatar() != null ? userDto.getAvatar() : "/default_avatar.png")
                .rating(1000)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDto userDto) {
        User user = userRepository.findByUsername(userDto.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        if (user == null || !passwordEncoder.matches(userDto.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        String token = jwtUtil.generateToken(user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("avatar", user.getAvatar()); // null допустим
        response.put("rating", user.getRating());

        return ResponseEntity.ok(response);

    }

    @GetMapping("/profile")
    public UserProfileDTO getProfile(@RequestParam String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Cannot find user by username"));

        // Вычисляем позицию
        List<User> sorted = userRepository.findAll(Sort.by("rating").descending());
        int position = 1;
        for (User u : sorted) {
            if (u.getUsername().equals(username)) break;
            position++;
        }

        int totalGames = user.getWins() + user.getLosses();
        double winrate = totalGames > 0 ? (double) user.getWins() / totalGames * 100 : 0;

        return new UserProfileDTO(
                user.getUsername(),
                totalGames,
                user.getWins(),
                user.getLosses(),
                winrate,
                user.getRating(),
                position
        );
    }


    @GetMapping("/top")
    public List<UserRatingDto> getTopPlayers(@RequestParam(defaultValue = "5") int limit) {
        return userRepository.findAll(PageRequest.of(0, limit, Sort.by("rating").descending()))
                .getContent()
                .stream()
                .map(u -> new UserRatingDto(u.getUsername(), u.getRating()))
                .toList();
    }

    @GetMapping("/rating/position")
    public UserRankDTO getUserRank(@RequestParam String username) {
        List<User> sorted = userRepository.findAll(Sort.by("rating").descending());

        int position = 1;
        for (User u : sorted) {
            if (u.getUsername().equals(username)) break;
            position++;
        }

        User user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Cannot find user by nickname"));
        return new UserRankDTO(user.getRating(), position);
    }
}