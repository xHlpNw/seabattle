import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

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
        // Use native WebSocket - connect directly to backend
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const backendHost = window.location.hostname;
        const backendPort = ':8080'; // Always use port 8080 for backend
        
        // Get token from AuthService (same as used for HTTP requests)
        const token = this.authService.getToken();
        const tokenParam = token ? `?token=${encodeURIComponent(token)}` : '';
        
        const wsUrl = `${protocol}//${backendHost}${backendPort}/api/ws/game${tokenParam}`;
        console.log('🎮 Attempting Game WebSocket connection to:', wsUrl);
        this.socket = new WebSocket(wsUrl);
        this.currentGameId = gameId;

        this.socket.onopen = () => {
          console.log('✅ Game WebSocket connected successfully to:', wsUrl);
          this.reconnectAttempts = 0;
          
          // Subscribe to game updates
          this.subscribeToGame(gameId);
          
          observer.next(true);
          observer.complete();
        };

        this.socket.onmessage = (event) => {
          try {
            const data: GameUpdate = JSON.parse(event.data);
            console.log('🎮 Game WebSocket message received:', data);
            this.gameUpdates$.next(data);
          } catch (error) {
            console.error('Failed to parse Game WebSocket message:', error);
          }
        };

        this.socket.onclose = () => {
          console.log('🎮 Disconnected from Game WebSocket');
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
          console.error('🎮 Game WebSocket error:', error);
          observer.error(error);
        };

      } catch (error) {
        console.error('Failed to connect to Game WebSocket:', error);
        observer.error(error);
      }
    });
  }

  private attemptReconnect(gameId: string): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`🎮 Attempting to reconnect to game... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

      setTimeout(() => {
        this.connect(gameId).subscribe();
      }, this.reconnectDelay);
    } else {
      console.error('🎮 Max reconnection attempts reached');
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
      console.log('📡 Subscribed to game:', gameId);
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
      console.log('🎯 Attack sent via WebSocket:', { gameId, x, y });
    } else {
      console.warn('🎯 WebSocket not connected, attack sent via HTTP instead');
    }
  }

  sendReady(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'ready',
        gameId: gameId
      }));
      console.log('✅ Ready sent via WebSocket:', gameId);
    } else {
      console.warn('✅ WebSocket not connected, ready sent via HTTP instead');
    }
  }

  sendSurrender(gameId: string): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'surrender',
        gameId: gameId
      }));
      console.log('🏳️ Surrender sent via WebSocket:', gameId);
    } else {
      console.warn('🏳️ WebSocket not connected, surrender sent via HTTP instead');
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
