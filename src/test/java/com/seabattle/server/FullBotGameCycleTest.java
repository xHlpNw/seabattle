package com.seabattle.server;

import com.seabattle.server.entity.Game;
import com.seabattle.server.entity.User;
import com.seabattle.server.repository.UserRepository;
import com.seabattle.server.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class FullBotGameCycleTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private UserRepository userRepo;

    private User testUser;

    @BeforeEach
    void setup() {
        testUser = User.builder()
                .username("player_full_cycle")
                .passwordHash("pass")
                .rating(1000)
                .wins(0)
                .losses(0)
                .role(User.Role.PLAYER)
                .build();
        userRepo.save(testUser);
    }

    @Test
    @WithMockUser(username = "player_full_cycle", roles = {"PLAYER"})
    void testFullBotGameCycle() throws Exception {

        // 1. Создаём игру с ботом
        Game game = gameService.createBotGame(testUser);
        assertThat(game).isNotNull();
        assertThat(game.getStatus()).isEqualTo(Game.GameStatus.WAITING);

        // 2. Авто-расстановка кораблей игрока
        gameService.placeShipsAuto(game.getId(), testUser.getId());
        Game updated = gameService.getGameById(game.getId());
        assertThat(updated.getStatus()).isEqualTo(Game.GameStatus.IN_PROGRESS);

        // 3. Игрок делает ходы до окончания игры
        boolean gameOver = false;
        int x = 0, y = 0;
        while (!gameOver && x < 10) { // safety limit 10x10
            var resultDto = gameService.playerShot(game.getId(), testUser.getId(), x, y);
            gameOver = resultDto.isGameOver();

            // двигаем координаты по сетке
            y++;
            if (y >= 10) { y = 0; x++; }
        }

        // 4. Проверяем финальный статус игры
        Game finishedGame = gameService.getGameById(game.getId());
        assertThat(finishedGame.getStatus()).isEqualTo(Game.GameStatus.FINISHED);
        assertThat(finishedGame.getResult())
                .isIn(Game.GameResult.HOST_WIN, Game.GameResult.GUEST_WIN);

        // 5. Проверяем обновление статистики игрока
        User playerAfter = userRepo.findById(testUser.getId()).orElseThrow();
        assertThat(playerAfter.getWins() + playerAfter.getLosses()).isGreaterThan(0);
        assertThat(playerAfter.getRating()).isNotNull();

        System.out.println("Full game finished. Result: " + finishedGame.getResult());
        System.out.println("Player rating: " + playerAfter.getRating() +
                ", wins: " + playerAfter.getWins() +
                ", losses: " + playerAfter.getLosses());
    }
}
