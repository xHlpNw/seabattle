import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameApi } from '../../core/api/game.api';
import { UserApi } from '../../core/api/user.api';
import { AuthService } from '../../core/auth/auth.service';
import { GameWebSocketService, GameUpdate } from '../../core/ws/game-ws.service';
import { getApiBaseUrl } from '../../core/api/api-config';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { firstValueFrom, Subscription } from 'rxjs';

@Component({
  selector: 'page-game',
  templateUrl: './game.html',
  styleUrls: ['./game.scss'],
  standalone: true,
  imports: [CommonModule, RouterLink]
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

  isLoading: boolean = false;
  gameOver: boolean = false;

  botLastX: number | null = null;
  botLastY: number | null = null;

  showResultModal: boolean = false;
  resultText: string = "";
  gameResultStatus: string = "";
  startingNewGame: boolean = false;
  createGameError: string | null = null;
  rematchRequestPending: boolean = false;
  rematchRequestedBy: string | null = null;
  rematchDeclinedMessage: boolean = false;

  currentTurn: string | null = null;
  isPlayerTurn: boolean = true;

  opponentName: string = 'Командир Бета';
  opponentAvatar: string = '/default_avatar.png';
  isBotGame: boolean = true;
  isHost: boolean = true;

  private webSocketConnected: boolean = false;

  async ngOnInit() {
    const token = this.auth.getToken();
    const username = localStorage.getItem('username');

    if (!username || !token) {
      this.router.navigate(['/login']);
      return;
    }

    try {
      this.profile = await this.userApi.getProfile(username);
    } catch (err) {
      this.router.navigate(['/login']);
      return;
    }

    const resolveGameId = () => {
      const fromParams = this.route.snapshot.paramMap.get('gameId');
      const fromQuery = this.route.snapshot.queryParamMap.get('gameId');
      return fromParams || fromQuery || null;
    };

    this.gameId = resolveGameId();
    if (this.gameId) {
      await this.loadBoards();
    }

    this.route.paramMap.subscribe(async params => {
      const paramId = params.get('gameId');
      const queryId = this.route.snapshot.queryParamMap.get('gameId');
      const newGameId = paramId || queryId || null;
      if (newGameId && newGameId !== this.gameId) {
        this.gameId = newGameId;
        await this.loadBoards();
      }
    });
    this.route.queryParamMap.subscribe(async query => {
      const queryId = query.get('gameId');
      if (queryId && queryId !== this.gameId) {
        this.gameId = queryId;
        await this.loadBoards();
      }
    });
  }

  async loadBoards() {
    if (!this.gameId) return;

    this.isLoading = true;
    try {
      const res: any = await firstValueFrom(this.gameApi.getBoards(this.gameId));

      this.playerBoard = res.playerBoard;
      this.enemyBoard = res.enemyBoard;
      this.currentTurn = res.currentTurn;
      this.opponentName = res.opponentName;
      this.opponentAvatar = res.opponentAvatar || '/default_avatar.png';
      this.isBotGame = res.isBotGame;
      this.isHost = res.isHost;

      if (this.isBotGame) {
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;
      } else {
        this.isPlayerTurn = (this.isHost && res.currentTurn === 'HOST') || (!this.isHost && res.currentTurn === 'GUEST');
      }

      if (res.gameFinished) {
        this.gameOver = true;
        this.showResultModal = true;

        if (res.winner === 'HOST_WIN') {
          this.gameResultStatus = 'ПОБЕДА';
          this.resultText = "Вы победили!";
        } else if (res.winner === 'GUEST_WIN') {
          this.gameResultStatus = 'ПОРАЖЕНИЕ';
          this.resultText = "Вы проиграли!";
        } else if (res.winner === 'SURRENDER') {
          this.gameResultStatus = 'ПОРАЖЕНИЕ';
          this.resultText = "Вы сдались!";
        } else {
          this.gameResultStatus = 'ИГРА ОКОНЧЕНА';
          this.resultText = "Игра завершена";
        }
      } else if (this.isBotGame && !this.isPlayerTurn) {
        this.triggerBotMove();
      }

      if (!this.isBotGame && this.gameId && !this.webSocketConnected) {
        this.setupWebSocketConnection();
      }

    } catch (err) {
      this.playerBoard = this.createEmptyGrid();
      this.enemyBoard = this.createEmptyGrid();
    } finally {
      this.isLoading = false;
    }
  }

  private setupWebSocketConnection() {
    if (!this.gameId) return;

    this.gameWs.connect(this.gameId).subscribe({
      next: (connected) => {
        if (connected) {
          this.webSocketConnected = true;
          this.gameUpdatesSubscription = this.gameWs.getGameUpdates().subscribe(update => {
            if (update) {
              this.handleGameUpdate(update);
            }
          });
        }
      },
      error: () => {
        this.webSocketConnected = false;
      }
    });
  }

  private handleGameUpdate(update: GameUpdate) {
    switch (update.type) {
      case 'gameStateUpdate':
        if (update.gameId && update.gameId !== this.gameId) return;
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
        break;

      case 'gameFinished':
        this.gameOver = true;
        this.handleGameFinished(update.winner);
        break;

      case 'subscribed':
        // Подтягиваем актуальное состояние после подписки (на случай пропущенных обновлений)
        if (!this.isBotGame && update.gameId === this.gameId) {
          this.loadBoards();
        }
        break;

      case 'playerReady':
        if (update.gameStarted && !this.gameOver) {
          this.loadBoards();
        }
        break;

      case 'rematchRequested':
        if (update.gameId === this.gameId && !this.isBotGame) {
          this.rematchRequestedBy = update.requestedByUsername ?? 'Противник';
        }
        break;

      case 'rematchRequestSent':
        if (update.gameId === this.gameId) {
          this.rematchRequestPending = true;
          this.rematchDeclinedMessage = false;
        }
        break;

      case 'rematchAccepted':
        // Переходим на setup только если мы ещё на странице старой игры (игнорировать replay от BehaviorSubject)
        if (update.newGameId && update.gameId === this.gameId) {
          this.showResultModal = false;
          this.rematchRequestPending = false;
          this.rematchRequestedBy = null;
          this.rematchDeclinedMessage = false;
          this.router.navigate(['/setup'], { queryParams: { gameId: update.newGameId } });
        }
        break;

      case 'rematchDeclined':
        if (update.gameId === this.gameId) {
          this.rematchRequestPending = false;
          this.rematchDeclinedMessage = true;
        }
        break;

      case 'error':
        this.webSocketConnected = false;
        break;
    }
  }

  private handleGameFinished(winner?: string | null) {
    this.gameOver = true;
    this.showResultModal = true;
    this.createGameError = null;
    this.rematchRequestPending = false;
    this.rematchRequestedBy = null;
    this.rematchDeclinedMessage = false;

    if (winner === 'HOST_WIN') {
      this.gameResultStatus = this.isHost ? 'ПОБЕДА' : 'ПОРАЖЕНИЕ';
      this.resultText = this.isHost ? "Вы победили!" : "Вы проиграли!";
    } else if (winner === 'GUEST_WIN') {
      this.gameResultStatus = !this.isHost ? 'ПОБЕДА' : 'ПОРАЖЕНИЕ';
      this.resultText = !this.isHost ? "Вы победили!" : "Вы проиграли!";
    } else {
      this.gameResultStatus = 'ИГРА ОКОНЧЕНА';
      this.resultText = "Игра завершена";
    }
  }

  createEmptyGrid(): number[][] {
    return Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  goToHome() {
    this.showResultModal = false;
    this.createGameError = null;
    this.rematchRequestPending = false;
    this.rematchRequestedBy = null;
    this.rematchDeclinedMessage = false;
    this.router.navigate(['/']);
  }

  requestRematch() {
    if (!this.gameId || this.isBotGame || this.rematchRequestPending) return;
    this.gameWs.sendRematchRequest(this.gameId);
  }

  acceptRematch() {
    if (!this.gameId || this.isBotGame) return;
    this.gameWs.sendRematchAccept(this.gameId);
    this.rematchRequestedBy = null;
  }

  declineRematch() {
    if (!this.gameId || this.isBotGame) return;
    this.gameWs.sendRematchDecline(this.gameId);
    this.rematchRequestedBy = null;
  }

  startNewBotGame() {
    if (this.startingNewGame || !this.isBotGame) return;
    this.createGameError = null;
    this.startingNewGame = true;
    this.gameApi.createBotGame().subscribe({
      next: (res) => {
        this.showResultModal = false;
        this.router.navigate(['/setup'], { queryParams: { gameId: res.gameId } });
      },
      error: () => {
        this.startingNewGame = false;
        this.createGameError = 'Не удалось создать игру. Попробуйте с главной.';
      }
    });
  }

  attackEnemy(i: number, j: number) {
    if (!this.gameId || !this.isPlayerTurn) return;

    if (this.isBotGame) {
      this.gameApi.attackEnemy(this.gameId, i, j).subscribe(res => {
        this.playerBoard = res.playerBoard;
        this.enemyBoard = res.enemyBoard;
        this.currentTurn = res.currentTurn;
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;

        if (res.botX != null && res.botY != null) {
          this.botLastX = res.botX;
          this.botLastY = res.botY;
        }

        if (res.gameFinished) {
          this.gameOver = true;
          this.handleGameFinished(res.winner);
          this.showResultModal = true;
        } else if (!this.isPlayerTurn) {
          this.triggerBotMove();
        }
      });
    } else {
      this.gameApi.attackEnemy(this.gameId, i, j).subscribe({
        next: () => {
          if (!this.webSocketConnected) {
            setTimeout(() => this.loadBoards(), 500);
          }
        },
        error: () => {}
      });
    }
  }

  private triggerBotMove() {
    if (!this.gameId) return;

    setTimeout(() => {
      this.gameApi.botMove(this.gameId!).subscribe(res => {
        this.playerBoard = res.playerBoard;
        this.enemyBoard = res.enemyBoard;
        this.currentTurn = res.currentTurn;
        this.isPlayerTurn = res.currentTurn === 'HOST' || res.currentTurn === null;

        if (res.botX != null && res.botY != null) {
          this.botLastX = res.botX;
          this.botLastY = res.botY;
        }

        if (res.gameFinished) {
          this.gameOver = true;

          if (res.winner === 'HOST_WIN') {
            this.gameResultStatus = 'ПОБЕДА';
            this.resultText = "Вы победили!";
          } else if (res.winner === 'GUEST_WIN') {
            this.gameResultStatus = 'ПОРАЖЕНИЕ';
            this.resultText = "Вы проиграли!";
          } else if (res.winner === 'SURRENDER') {
            this.gameResultStatus = 'ПОРАЖЕНИЕ';
            this.resultText = "Вы сдались!";
          } else {
            this.gameResultStatus = 'ИГРА ОКОНЧЕНА';
            this.resultText = "Игра завершена";
          }

          this.showResultModal = true;
        } else if (this.isBotGame && !this.isPlayerTurn) {
          this.triggerBotMove();
        }
      });
    }, 1000);
  }

  surrender() {
    if (!this.gameId) return;

    if (confirm('Вы уверены, что хотите сдаться? Вы проиграете игру.')) {
      this.gameApi.surrender(this.gameId, this.isBotGame).subscribe({
        next: () => {
          if (this.isBotGame) {
            this.gameOver = true;
            this.showResultModal = true;
            this.gameResultStatus = 'ПОРАЖЕНИЕ';
            this.resultText = 'Вы сдались!';
            setTimeout(() => this.loadBoards(), 300);
          } else if (!this.webSocketConnected) {
            setTimeout(() => this.loadBoards(), 500);
          }
        },
        error: () => {}
      });
    }
  }

  getBackendUrl(): string {
    return getApiBaseUrl() || '';
  }

  ngOnDestroy() {
    this.webSocketConnected = false;
    if (this.gameUpdatesSubscription) {
      this.gameUpdatesSubscription.unsubscribe();
    }
    this.gameWs.disconnect();
  }
}
