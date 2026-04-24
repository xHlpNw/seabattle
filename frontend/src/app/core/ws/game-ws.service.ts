import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { getWsBaseUrl } from '../api/api-config';

export interface GameUpdate {
  type: 'gameStateUpdate' | 'attackResult' | 'gameFinished' | 'playerReady' | 'subscribed' | 'error'
    | 'rematchRequested' | 'rematchAccepted' | 'rematchDeclined' | 'rematchRequestSent';
  gameId?: string;
  newGameId?: string;
  requestedByUsername?: string;
  playerBoard?: number[][];
  enemyBoard?: number[][];
  currentTurn?: string | null;
  gameFinished?: boolean;
  winner?: string;
  hit?: boolean;
  sunk?: boolean;
  already?: boolean;
  isHost?: boolean;
  hostReady?: boolean;
  guestReady?: boolean;
  bothReady?: boolean;
  gameStarted?: boolean;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class GameWebSocketService {
  private socket: WebSocket | null = null;
  private gameUpdates$ = new BehaviorSubject<GameUpdate | null>(null);
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  private reconnectDelay = 2000;
  private currentGameId: string | null = null;
  private authService = inject(AuthService);
  /** True when close was triggered by disconnect() (e.g. navigation away / rematch to setup) — skip reconnect and avoid emitting error */
  private intentionalDisconnect = false;
  /** Pending reconnect timeout — cleared in disconnect() to prevent reconnect after switching game */
  private reconnectTimeoutId: ReturnType<typeof setTimeout> | null = null;

  connect(gameId: string): Observable<boolean> {
    return new Observable(observer => {
      // If already connected to the same game, return success
      if (this.socket && this.socket.readyState === WebSocket.OPEN && this.currentGameId === gameId) {
        observer.next(true);
        observer.complete();
        return;
      }

      // Disconnect from previous game if different
      if (this.currentGameId !== gameId) {
        this.disconnect();
      }

      try {
        const wsBase = getWsBaseUrl();
        const token = this.authService.getToken();
        const tokenParam = token ? `?token=${encodeURIComponent(token)}` : '';
        const wsUrl = `${wsBase}/api/ws/game${tokenParam}`;
        this.socket = new WebSocket(wsUrl);
        this.currentGameId = gameId;
        // Очистить последнее значение, чтобы новый подписчик не получил старый rematchAccepted (replay BehaviorSubject)
        this.gameUpdates$.next(null);

        this.socket.onopen = () => {
          this.reconnectAttempts = 0;
          
          // Subscribe to game updates
          this.subscribeToGame(gameId);
          
          observer.next(true);
          observer.complete();
        };

        this.socket.onmessage = (event) => {
          try {
            const data: GameUpdate = JSON.parse(event.data);
            this.gameUpdates$.next(data);
          } catch (error) {
            this.gameUpdates$.next({ type: 'error', message: 'Некорректное сообщение' });
          }
        };

        this.socket.onclose = () => {
          this.currentGameId = null;
          if (this.intentionalDisconnect) {
            this.intentionalDisconnect = false;
            return;
          }
          this.gameUpdates$.next({
            type: 'error',
            message: 'Соединение WebSocket разорвано'
          });
          if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.attemptReconnect(gameId);
          }
        };

        this.socket.onerror = (error) => {
          observer.error(error);
        };

      } catch (error) {
        observer.error(error);
      }
    });
  }

  private attemptReconnect(gameId: string): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) return;
    this.reconnectAttempts++;
    this.reconnectTimeoutId = setTimeout(() => {
      this.reconnectTimeoutId = null;
      this.connect(gameId).subscribe();
    }, this.reconnectDelay);
  }

  disconnect(): void {
    if (this.reconnectTimeoutId != null) {
      clearTimeout(this.reconnectTimeoutId);
      this.reconnectTimeoutId = null;
    }
    if (this.socket) {
      this.intentionalDisconnect = true;
      this.reconnectAttempts = 0;
      this.socket.close();
      this.socket = null;
      this.currentGameId = null;
    }
  }

  subscribeToGame(gameId: string): void {
    // Send subscription message
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'subscribe',
        gameId: gameId
      }));
    }
  }

  getGameUpdates(): Observable<GameUpdate | null> {
    return this.gameUpdates$.asObservable();
  }

  sendRematchRequest(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'rematchRequest', gameId }));
    }
  }

  sendRematchAccept(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'rematchAccept', gameId }));
    }
  }

  sendRematchDecline(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'rematchDecline', gameId }));
    }
  }
}
