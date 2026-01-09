import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameApi } from '../../core/api/game.api';
import { UserApi } from '../../core/api/user.api';
import { AuthService } from '../../core/auth/auth.service';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'page-game',
  templateUrl: './game.html',
  styleUrls: ['./game.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class GameComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private gameApi = inject(GameApi);
  private userApi = inject(UserApi);
  private auth = inject(AuthService);

  gameId: string | null = null;
  profile: any = null;

  playerBoard: number[][] = [];
  enemyBoard: number[][] = [];

  isLoading: boolean = true;
  gameOver: boolean = false;

  botLastX: number | null = null;
  botLastY: number | null = null;

  showResultModal: boolean = false;
  resultText: string = "";
  gameResultStatus: string = "";

  currentTurn: string | null = null;
  isPlayerTurn: boolean = true;

  opponentName: string = "Commander Beta";
  isBotGame: boolean = true;
  isHost: boolean = true;

  async ngOnInit() {
    const token = this.auth.getToken();
    const username = localStorage.getItem('username');

    if (!username || !token) {
      console.error('Пользователь не авторизован');
      this.router.navigate(['/login']);
      return;
    }

    try {
      this.profile = await this.userApi.getProfile(username);
      console.log('Профиль текущего пользователя:', this.profile);
    } catch (err) {
      console.error('Ошибка получения профиля', err);
      this.router.navigate(['/login']);
      return;
    }

    this.route.paramMap.subscribe(async params => {
      this.gameId = params.get('gameId');
      if (!this.gameId) {
        // Check query parameters if not found in route params
        this.route.queryParams.subscribe(async queryParams => {
          this.gameId = queryParams['gameId'];
          if (this.gameId) {
            await this.loadBoards();
          }
        });
      } else {
        await this.loadBoards();
      }
    });
  }

  async loadBoards() {
    if (!this.gameId) return;

    this.isLoading = true;
    try {
      // Расширяем API, чтобы возвращался полный AttackResult-like объект
      const res: any = await firstValueFrom(this.gameApi.getBoards(this.gameId));

      this.playerBoard = res.playerBoard;
      this.enemyBoard = res.enemyBoard;
      this.currentTurn = res.currentTurn;
      this.opponentName = res.opponentName;
      this.isBotGame = res.isBotGame;
      this.isHost = res.isHost;

      // Определяем, чей сейчас ход
      if (this.isBotGame) {
        // В играх с ботом: ход игрока когда currentTurn = HOST или null
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;
      } else {
        // В онлайн играх: ход игрока когда currentTurn совпадает с его ролью
        this.isPlayerTurn = (this.isHost && res.currentTurn === 'HOST') || (!this.isHost && res.currentTurn === 'GUEST');
      }

      console.log('loadBoards - isBotGame:', this.isBotGame, 'isHost:', this.isHost, 'currentTurn:', this.currentTurn, 'isPlayerTurn:', this.isPlayerTurn);

      // Проверяем, закончена ли игра
      if (res.gameFinished) {
        this.gameOver = true;
        this.showResultModal = true;

        if (res.winner === 'HOST_WIN') {
          this.gameResultStatus = "VICTORY";
          this.resultText = "🎉 Вы победили!";
        } else if (res.winner === 'GUEST_WIN') {
          this.gameResultStatus = "DEFEAT";
          this.resultText = "💀 Вы проиграли!";
        } else if (res.winner === 'SURRENDER') {
          this.gameResultStatus = "DEFEAT";
          this.resultText = "🏳️ Вы сдались!";
        } else {
          this.gameResultStatus = "GAME OVER";
          this.resultText = "Игра завершена";
        }
      } else if (this.isBotGame && !this.isPlayerTurn) {
        // If it's the bot's turn when loading the board, trigger bot move
        // Only for bot games, never for online games
        console.log('loadBoards: Triggering bot move');
        this.triggerBotMove();
      }

    } catch (err) {
      console.error('Ошибка получения досок:', err);
      this.playerBoard = this.createEmptyGrid();
      this.enemyBoard = this.createEmptyGrid();
    } finally {
      this.isLoading = false;
    }
  }


  createEmptyGrid(): number[][] {
    return Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  goToHome() {
    this.showResultModal = false;
    this.router.navigate(['/']); // переход на главную страницу
  }

  attackEnemy(i: number, j: number) {
    if (!this.gameId || !this.isPlayerTurn) return;

    this.gameApi.attackEnemy(this.gameId, i, j).subscribe(res => {
      console.log('Ответ сервера после выстрела:', res);

      this.playerBoard = res.playerBoard;
      this.enemyBoard = res.enemyBoard;
      this.currentTurn = res.currentTurn;

      // Определяем, чей сейчас ход (после атаки ход может измениться)
      if (this.isBotGame) {
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;
      } else {
        this.isPlayerTurn = (this.isHost && res.currentTurn === 'HOST') || (!this.isHost && res.currentTurn === 'GUEST');
      }

      console.log('attack - isBotGame:', this.isBotGame, 'isHost:', this.isHost, 'currentTurn:', this.currentTurn, 'isPlayerTurn:', this.isPlayerTurn);

      if (res.hit) console.log('Попадание!');
      if (res.sunk) console.log('Корабль потоплен!');
      if (res.already) console.log('Вы уже стреляли сюда');

      if (res.botX != null && res.botY != null) {
        this.botLastX = res.botX;
        this.botLastY = res.botY;
        console.log(`Бот стрелял: ${res.botX}, ${res.botY}`);
      }

      if (res.gameFinished) {
        this.gameOver = true;

        if (res.winner === 'HOST_WIN') {
          this.gameResultStatus = "VICTORY";
          this.resultText = "🎉 Вы победили!";
        } else if (res.winner === 'GUEST_WIN') {
          this.gameResultStatus = "DEFEAT";
          this.resultText = "💀 Вы проиграли!";
        } else if (res.winner === 'SURRENDER') {
          this.gameResultStatus = "DEFEAT";
          this.resultText = "🏳️ Вы сдались!";
        } else {
          this.gameResultStatus = "GAME OVER";
          this.resultText = "Игра завершена";
        }

        this.showResultModal = true; // ← показываем модалку
      } else if (this.isBotGame && !this.isPlayerTurn) {
        // If it's now the bot's turn, automatically trigger bot move
        // Only for bot games, never for online games
        console.log('attack: Triggering bot move');
        this.triggerBotMove();
      }

    });
  }

  private triggerBotMove() {
    if (!this.gameId) return;

    // Small delay to show the board update before bot moves
    setTimeout(() => {
      this.gameApi.botMove(this.gameId!).subscribe(res => {
        console.log('Ответ сервера после хода бота:', res);

        this.playerBoard = res.playerBoard;
        this.enemyBoard = res.enemyBoard;
        this.currentTurn = res.currentTurn;
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;

        if (res.botX != null && res.botY != null) {
          this.botLastX = res.botX;
          this.botLastY = res.botY;
          console.log(`Бот стрелял: ${res.botX}, ${res.botY}`);
        }

        if (res.gameFinished) {
          this.gameOver = true;

          if (res.winner === 'HOST_WIN') {
            this.gameResultStatus = "VICTORY";
            this.resultText = "🎉 Вы победили!";
          } else if (res.winner === 'GUEST_WIN') {
            this.gameResultStatus = "DEFEAT";
            this.resultText = "💀 Вы проиграли!";
          } else if (res.winner === 'SURRENDER') {
            this.gameResultStatus = "DEFEAT";
            this.resultText = "🏳️ Вы сдались!";
          } else {
            this.gameResultStatus = "GAME OVER";
            this.resultText = "Игра завершена";
          }

          this.showResultModal = true;
        } else if (this.isBotGame && !this.isPlayerTurn) {
          // If bot hit again, continue with bot moves
          // Only for bot games, never for online games
          console.log('triggerBotMove: Triggering bot move again');
          this.triggerBotMove();
        }
      });
    }, 1000); // 1 second delay to show the board update
  }

  surrender() {
    if (!this.gameId) return;

    if (confirm('Вы уверены, что хотите сдаться? Вы проиграете игру.')) {
      this.gameApi.surrender(this.gameId).subscribe({
        next: (response) => {
          console.log('Сдался:', response);
          // После сдачи обновляем доски, чтобы показать финальное состояние
          this.loadBoards();
        },
        error: (err) => {
          console.error('Ошибка при сдаче:', err);
        }
      });
    }
  }
}
