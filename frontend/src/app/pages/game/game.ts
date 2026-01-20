import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameApi } from '../../core/api/game.api';
import { UserApi } from '../../core/api/user.api';
import { AuthService } from '../../core/auth/auth.service';
import { GameWebSocketService, GameUpdate } from '../../core/ws/game-ws.service';
import { CommonModule } from '@angular/common';
import { firstValueFrom, Subscription } from 'rxjs';

@Component({
  selector: 'page-game',
  templateUrl: './game.html',
  styleUrls: ['./game.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class GameComponent implements OnInit, OnDestroy {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private gameApi = inject(GameApi);
  private userApi = inject(UserApi);
  private auth = inject(AuthService);
  private gameWs = inject(GameWebSocketService);
  private gameUpdatesSubscription?: Subscription;

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

      // For online games, connect to WebSocket and subscribe to updates
      if (!this.isBotGame && this.gameId) {
        this.setupWebSocketConnection();
      }

    } catch (err) {
      console.error('Ошибка получения досок:', err);
      this.playerBoard = this.createEmptyGrid();
      this.enemyBoard = this.createEmptyGrid();
    } finally {
      this.isLoading = false;
    }
  }

  private setupWebSocketConnection() {
    if (!this.gameId) return;

    console.log('🎮 Setting up WebSocket connection for online game:', this.gameId);
    
    // Connect to WebSocket
    this.gameWs.connect(this.gameId).subscribe({
      next: (connected) => {
        if (connected) {
          console.log('🎮 WebSocket connected successfully');
          // Subscribe to game updates
          this.gameUpdatesSubscription = this.gameWs.getGameUpdates().subscribe(update => {
            if (update) {
              this.handleGameUpdate(update);
            }
          });
        }
      },
      error: (err) => {
        console.error('🎮 WebSocket connection error:', err);
      }
    });
  }

  private handleGameUpdate(update: GameUpdate) {
    console.log('🎮 Handling game update:', update);

    switch (update.type) {
      case 'gameStateUpdate':
        if (update.playerBoard) {
          this.playerBoard = update.playerBoard;
        }
        if (update.enemyBoard) {
          this.enemyBoard = update.enemyBoard;
        }
        if (update.currentTurn !== undefined) {
          this.currentTurn = update.currentTurn;
          this.isPlayerTurn = (this.isHost && update.currentTurn === 'HOST') || (!this.isHost && update.currentTurn === 'GUEST');
        }
        if (update.gameFinished) {
          this.gameOver = true;
          this.handleGameFinished(update.winner);
        }
        if (update.hit) console.log('Попадание!');
        if (update.sunk) console.log('Корабль потоплен!');
        if (update.already) console.log('Вы уже стреляли сюда');
        break;

      case 'gameFinished':
        this.gameOver = true;
        this.handleGameFinished(update.winner);
        break;

      case 'playerReady':
        console.log('🎮 Player ready update:', update);
        if (update.gameStarted && !this.gameOver) {
          // Game started, might need to refresh boards
          this.loadBoards();
        }
        break;

      case 'error':
        console.error('🎮 WebSocket error:', update.message);
        break;

      default:
        console.log('🎮 Unknown update type:', update.type);
    }
  }

  private handleGameFinished(winner?: string | null) {
    this.gameOver = true;
    this.showResultModal = true;

    if (winner === 'HOST_WIN') {
      this.gameResultStatus = this.isHost ? "VICTORY" : "DEFEAT";
      this.resultText = this.isHost ? "🎉 Вы победили!" : "💀 Вы проиграли!";
    } else if (winner === 'GUEST_WIN') {
      this.gameResultStatus = !this.isHost ? "VICTORY" : "DEFEAT";
      this.resultText = !this.isHost ? "🎉 Вы победили!" : "💀 Вы проиграли!";
    } else if (winner === 'SURRENDER') {
      this.gameResultStatus = "DEFEAT";
      this.resultText = "🏳️ Вы сдались!";
    } else {
      this.gameResultStatus = "GAME OVER";
      this.resultText = "Игра завершена";
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

    // For online games, use WebSocket; for bot games, use HTTP
    if (this.isBotGame) {
      // Bot game - use HTTP as before
      this.gameApi.attackEnemy(this.gameId, i, j).subscribe(res => {
        console.log('Ответ сервера после выстрела:', res);

        this.playerBoard = res.playerBoard;
        this.enemyBoard = res.enemyBoard;
        this.currentTurn = res.currentTurn;

        // Определяем, чей сейчас ход (после атаки ход может измениться)
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;

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
          this.handleGameFinished(res.winner);
          this.showResultModal = true;
        } else if (!this.isPlayerTurn) {
          // If it's now the bot's turn, automatically trigger bot move
          console.log('attack: Triggering bot move');
          this.triggerBotMove();
        }
      });
    } else {
      // Online game - use HTTP to send attack (server will broadcast via WebSocket)
      // WebSocket will receive the update and handle it in handleGameUpdate
      this.gameApi.attackEnemy(this.gameId, i, j).subscribe({
        next: (res) => {
          console.log('🎯 Attack sent via HTTP, waiting for WebSocket update');
          // The response will come via WebSocket update
        },
        error: (err) => {
          console.error('🎯 Attack error:', err);
        }
      });
    }
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
      // For online games, WebSocket will receive gameFinished event
      // For bot games, we still need to reload boards
      this.gameApi.surrender(this.gameId).subscribe({
        next: (response) => {
          console.log('Сдался:', response);
          // After surrender, for bot games reload boards
          // For online games, WebSocket will notify us
          if (this.isBotGame) {
            this.loadBoards();
          }
        },
        error: (err) => {
          console.error('Ошибка при сдаче:', err);
        }
      });
    }
  }

  ngOnDestroy() {
    // Clean up WebSocket connection and subscriptions
    if (this.gameUpdatesSubscription) {
      this.gameUpdatesSubscription.unsubscribe();
    }
    this.gameWs.disconnect();
    console.log('🎮 Game component destroyed, WebSocket disconnected');
  }
}
