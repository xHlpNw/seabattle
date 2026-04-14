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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDto userDto) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }
        User user = User.builder()
                .username(userDto.getUsername())
                .passwordHash(passwordEncoder.encode(userDto.getPassword()))
                .avatar(userDto.getAvatar() != null ? userDto.getAvatar() : "/default_avatar.png")
                .rating(0)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDto userDto) {
        String username = userDto.getUsername() != null ? userDto.getUsername().trim() : "";
        String password = userDto.getPassword() != null ? userDto.getPassword().trim() : null;
        if (username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        String token = jwtUtil.generateToken(user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("avatar", user.getAvatar()); // null допустим
        response.put("rating", user.getRating() != null ? user.getRating() : 0);

        return ResponseEntity.ok(response);

    }

    @GetMapping("/profile")
    public UserProfileDTO getProfile(@RequestParam String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Cannot find user by username"));

        List<User> sorted = userRepository.findAll(Sort.by("rating").descending());
        int position = 1;
        for (User u : sorted) {
            if (u.getUsername().equals(username)) break;
            position++;
        }

        int wins = user.getWins() != null ? user.getWins() : 0;
        int losses = user.getLosses() != null ? user.getLosses() : 0;
        int rating = user.getRating() != null ? user.getRating() : 0;
        int totalGames = wins + losses;
        double winrate = totalGames > 0 ? (double) wins / totalGames * 100 : 0;

        return new UserProfileDTO(
                user.getUsername(),
                totalGames,
                wins,
                losses,
                winrate,
                rating,
                position,
                user.getAvatar()
        );
    }


    @GetMapping("/top")
    public List<UserRatingDto> getTopPlayers(@RequestParam(defaultValue = "5") int limit) {
        return userRepository.findAll(PageRequest.of(0, limit, Sort.by("rating").descending()))
                .getContent()
                .stream()
                .map(u -> new UserRatingDto(u.getUsername(), u.getRating() != null ? u.getRating() : 0))
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
        int userRating = user.getRating() != null ? user.getRating() : 0;
        return new UserRankDTO(userRating, position);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unauthorized"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only image files are allowed"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "File size must not exceed 5 MB"));
        }

        try {
            Path uploadPath = Paths.get(uploadDir, "avatars");
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : ".png";
            String filename = UUID.randomUUID() + extension;

            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/avatars/" + filename;

            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            user.setAvatar(avatarUrl);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("avatar", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload avatar"));
        }
    }
}