package com.seabattle.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seabattle.server.dto.CreateBotGameResponse;
import com.seabattle.server.dto.ShotRequest;
import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotGameController {

    private final GameService gameService;
    private final UserRepository userRepo;

    @PostMapping("/create")
    public ResponseEntity<CreateBotGameResponse> create(@AuthenticationPrincipal Principal principal) throws Exception {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        Game g = gameService.createBotGame(user);
        return ResponseEntity.ok(new CreateBotGameResponse(g.getId(), "Created bot game. Place ships with /place/auto or /place"));
    }

    @PostMapping("/{gameId}/place/auto")
    public ResponseEntity<String> autoPlace(@PathVariable UUID gameId, @AuthenticationPrincipal Principal principal) throws Exception {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        gameService.placeShipsAuto(gameId, user.getId());
        return ResponseEntity.ok("Ships auto-placed and game started.");
    }

    @PostMapping("/{gameId}/place")
    public ResponseEntity<String> manualPlace(@PathVariable UUID gameId,
                                              @RequestBody ObjectNode body,
                                              @AuthenticationPrincipal Principal principal) throws Exception {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        if (!body.has("cellsJson")) return ResponseEntity.badRequest().body("cellsJson required");
        String cellsJson = body.get("cellsJson").asText();
        gameService.placeShipsManual(gameId, user.getId(), cellsJson);
        return ResponseEntity.ok("Ships placed and game started.");
    }

    @PostMapping("/{gameId}/shot")
    public ResponseEntity<ShotResultDto> shot(@PathVariable UUID gameId,
                                              @RequestBody ShotRequest req,
                                              @AuthenticationPrincipal Principal principal) throws Exception {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        ShotResultDto res = gameService.playerShot(gameId, user.getId(), req.getX(), req.getY());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{gameId}/surrender")
    public ResponseEntity<String> surrender(@PathVariable UUID gameId, @AuthenticationPrincipal Principal principal) {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        gameService.surrender(gameId, user.getId());
        return ResponseEntity.ok("You surrendered.");
    }

    @PostMapping("/{gameId}/rematch")
    public ResponseEntity<CreateBotGameResponse> rematch(@PathVariable UUID gameId, @AuthenticationPrincipal Principal principal) throws Exception {
        User user = userRepo.findByUsername(principal.getName()).orElseThrow();
        Game g = gameService.rematch(gameId, user.getId());
        return ResponseEntity.ok(new CreateBotGameResponse(g.getId(), "Rematch created."));
    }

}
