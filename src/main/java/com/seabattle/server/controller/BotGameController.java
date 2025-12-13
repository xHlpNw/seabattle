package com.seabattle.server.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seabattle.server.dto.AutoPlaceResponse;
import com.seabattle.server.dto.CreateBotGameResponse;
import com.seabattle.server.dto.ShotRequest;
import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.engine.BoardModel;
import com.seabattle.server.entity.Board;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.BoardRepository;
import com.seabattle.server.repository.GameRepository;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotGameController {

    private final GameService gameService;
    private final UserRepository userRepo;
    private final BoardRepository boardRepository;
    private final GameRepository gameRepo;

    @PostMapping("/create")
    public ResponseEntity<CreateBotGameResponse> create(@AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();

        // Создаём игру
        Game g = gameService.createBotGame(user);

        // Создаём доску игрока (пока пустую)
        Board playerBoard = Board.builder()
                .game(g)
                .player(user)
                .cells(new BoardModel().toJson())
                .build();
        boardRepository.save(playerBoard);

        // Создаём доску бота с расставленными кораблями
        Board botBoard = Board.builder()
                .game(g)
                .player(null) // бот
                .cells(BoardModel.autoPlaceRandom().toJson()) // <-- рандомная доска
                .build();
        boardRepository.save(botBoard);

        return ResponseEntity.ok(new CreateBotGameResponse(g.getId(),
                "Created bot game. Place ships with /place/auto or /place"));
    }


    @PostMapping("/{gameId}/place/auto")
    public ResponseEntity<AutoPlaceResponse> autoPlace(
            @PathVariable UUID gameId,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();
        AutoPlaceResponse response = gameService.placeShipsAuto(gameId, user.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{gameId}/place")
    public ResponseEntity<String> manualPlace(@PathVariable UUID gameId,
                                              @RequestBody ObjectNode body,
                                              @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();
        if (!body.has("cellsJson")) return ResponseEntity.badRequest().body("cellsJson required");
        String cellsJson = body.get("cellsJson").asText();
        gameService.placeShipsManual(gameId, user.getId(), cellsJson);
        return ResponseEntity.ok("Ships placed and game started.");
    }

    @PostMapping("/{gameId}/shot")
    public ResponseEntity<ShotResultDto> shot(@PathVariable UUID gameId,
                                              @RequestBody ShotRequest req,
                                              @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();
        ShotResultDto res = gameService.playerShot(gameId, user.getId(), req.getX(), req.getY());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{gameId}/surrender")
    public ResponseEntity<String> surrender(@PathVariable UUID gameId, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();
        gameService.surrender(gameId, user.getId());
        return ResponseEntity.ok("You surrendered.");
    }

    @GetMapping("/unfinished")
    public ResponseEntity<List<Map<String, Object>>> getUnfinishedGames(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();

        List<Game> unfinishedGames = gameRepo.findByHostIdAndTypeAndStatus(user.getId(), Game.GameType.BOT, Game.GameStatus.IN_PROGRESS);

        List<Map<String, Object>> result = unfinishedGames.stream()
            .map(game -> Map.of(
                "gameId", (Object) game.getId().toString(),
                "createdAt", (Object) game.getCreatedAt().toString()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{gameId}/rematch")
    public ResponseEntity<CreateBotGameResponse> rematch(@PathVariable UUID gameId, @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = userRepo.findByUsername(userDetails.getUsername()).orElseThrow();
        Game g = gameService.rematch(gameId, user.getId());
        return ResponseEntity.ok(new CreateBotGameResponse(g.getId(), "Rematch created."));
    }

}
