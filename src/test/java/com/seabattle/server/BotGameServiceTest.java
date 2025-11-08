package com.seabattle.server;

import com.seabattle.server.dto.ShotResultDto;
import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class BotGameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private UserRepository userRepo;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = User.builder()
                .username("player2")
                .passwordHash("pass")
                .rating(1000)
                .wins(0)
                .losses(0)
                .role(User.Role.PLAYER)
                .build();
        userRepo.save(testUser);
    }

    @Test
    @WithMockUser(username = "player1", roles = {"PLAYER"})
    void testCreateBotGame() throws Exception {
        Game game = gameService.createBotGame(testUser);
        assertThat(game).isNotNull();
        assertThat(game.getType()).isEqualTo(Game.GameType.BOT);
        assertThat(game.getStatus()).isEqualTo(Game.GameStatus.WAITING);
        assertThat(game.isBot()).isTrue();
    }

    @Test
    @WithMockUser(username = "player2", roles = {"PLAYER"})
    void testAutoPlaceShips() throws Exception {
        Game game = gameService.createBotGame(testUser);
        gameService.placeShipsAuto(game.getId(), testUser.getId());
        Game updated = gameService.playerShot(game.getId(), testUser.getId(), 0, 0).isGameOver() ? game : game;
        assertThat(updated.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(username = "player1", roles = {"PLAYER"})
    void testPlayerHitAndBotResponse() throws Exception {
        Game game = gameService.createBotGame(testUser);
        gameService.placeShipsAuto(game.getId(), testUser.getId());

        // делаем один выстрел
        var res = gameService.playerShot(game.getId(), testUser.getId(), 0, 0);
        assertThat(res).isNotNull();
        assertThat(res.isGameOver()).isFalse(); // игра продолжается
        // hit может быть true/false в зависимости от рандома
    }

    @Test
    @WithMockUser(username = "player1", roles = {"PLAYER"})
    void testSurrender() throws Exception {
        Game game = gameService.createBotGame(testUser);
        gameService.placeShipsAuto(game.getId(), testUser.getId());

        gameService.surrender(game.getId(), testUser.getId());

        Game finished = gameService.getGameById(game.getId());

        assertThat(finished.getStatus()).isEqualTo(Game.GameStatus.FINISHED);
        assertThat(finished.getResult()).isEqualTo(Game.GameResult.SURRENDER);

        User playerAfter = userRepo.findById(testUser.getId()).orElseThrow();
        assertThat(playerAfter.getLosses()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "player1", roles = {"PLAYER"})
    void testRematchCreatesNewGame() throws Exception {
        Game oldGame = gameService.createBotGame(testUser);
        gameService.placeShipsAuto(oldGame.getId(), testUser.getId());

        Game newGame = gameService.rematch(oldGame.getId(), testUser.getId());
        assertThat(newGame).isNotNull();
        assertThat(newGame.getId()).isNotEqualTo(oldGame.getId());
        assertThat(newGame.getStatus()).isEqualTo(Game.GameStatus.WAITING);
        assertThat(newGame.isBot()).isTrue();
    }
}