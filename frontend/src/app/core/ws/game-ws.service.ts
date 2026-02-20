import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { getWsBaseUrl } from '../api/api-config';

export interface GameUpdate {
  type: 'gameStateUpdate' | 'attackResult' | 'gameFinished' | 'playerReady' | 'subscribed' | 'error';
  gameId?: string;
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

  constructor() {}

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
            this.gameUpdates$.next({ type: 'error', message: 'Invalid message' });
          }
        };

        this.socket.onclose = () => {
          this.currentGameId = null;
          // Notify about disconnection
          this.gameUpdates$.next({
            type: 'error',
            message: 'WebSocket disconnected'
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
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      setTimeout(() => {
        this.connect(gameId).subscribe();
      }, this.reconnectDelay);
    }
  }

  disconnect(): void {
    if (this.socket) {
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

  sendAttack(gameId: string, x: number, y: number): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'attack',
        gameId: gameId,
        x: x,
        y: y
      }));
    }
  }

  sendReady(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'ready',
        gameId: gameId
      }));
    }
  }

  sendSurrender(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'surrender',
        gameId: gameId
      }));
    }
  }

  sendMessage(type: string, data: any): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: type,
        ...data
      }));
    }
  }
}
