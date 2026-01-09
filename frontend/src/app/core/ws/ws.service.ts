import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface RoomUpdate {
  type: string;
  username?: string;
  message?: string;
  roomToken?: string;
}

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private socket: WebSocket | null = null;
  private roomUpdates$ = new BehaviorSubject<RoomUpdate | null>(null);
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  private reconnectDelay = 2000;

  constructor() {}

  connect(): Observable<boolean> {
    return new Observable(observer => {
      if (this.socket && this.socket.readyState === WebSocket.OPEN) {
        observer.next(true);
        observer.complete();
        return;
      }

      try {
        // Use native WebSocket - connect directly to backend
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        // Connect directly to backend on port 8080
        const backendHost = window.location.hostname;
        const backendPort = ':8080'; // Always use port 8080 for backend
        const wsUrl = `${protocol}//${backendHost}${backendPort}/api/ws/room`;
        console.log('🔌 Attempting WebSocket connection to:', wsUrl);
        this.socket = new WebSocket(wsUrl);

        this.socket.onopen = () => {
          console.log('✅ WebSocket connected successfully to:', `${protocol}//${backendHost}${backendPort}/api/ws/room`);
          this.reconnectAttempts = 0;
          observer.next(true);
          observer.complete();
        };

        this.socket.onmessage = (event) => {
          try {
            const data: RoomUpdate = JSON.parse(event.data);
            this.roomUpdates$.next(data);
          } catch (error) {
            console.error('Failed to parse WebSocket message:', error);
          }
        };

        this.socket.onclose = () => {
          console.log('Disconnected from WebSocket');
          this.attemptReconnect();
        };

        this.socket.onerror = (error) => {
          console.error('WebSocket error:', error);
          observer.error(error);
        };

      } catch (error) {
        console.error('Failed to connect to WebSocket:', error);
        observer.error(error);
      }
    });
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Attempting to reconnect... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

      setTimeout(() => {
        this.connect().subscribe();
      }, this.reconnectDelay);
    } else {
      console.error('Max reconnection attempts reached');
    }
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  subscribeToRoomUpdates(roomToken: string): Observable<RoomUpdate | null> {
    // Send subscription message
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({
        type: 'subscribe',
        roomToken: roomToken
      }));
    }
    return this.roomUpdates$.asObservable();
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