import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { getWsBaseUrl } from '../api/api-config';

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
        const wsBase = getWsBaseUrl();
        const wsUrl = `${wsBase}/api/ws/room`;
        this.socket = new WebSocket(wsUrl);

        this.socket.onopen = () => {
          this.reconnectAttempts = 0;
          observer.next(true);
          observer.complete();
        };

        this.socket.onmessage = (event) => {
          try {
            const data: RoomUpdate = JSON.parse(event.data);
            this.roomUpdates$.next(data);
          } catch {
            this.roomUpdates$.next({ type: 'error', message: 'Invalid message' });
          }
        };

        this.socket.onclose = () => {
          this.attemptReconnect();
        };

        this.socket.onerror = (error) => {
          observer.error(error);
        };

      } catch (error) {
        observer.error(error);
      }
    });
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      setTimeout(() => {
        this.connect().subscribe();
      }, this.reconnectDelay);
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