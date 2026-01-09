import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RoomStatus {
  token: string;
  status: string;
  hostUsername: string;
  guestUsername: string | null;
  isHost: boolean;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
  gameId: string | null;
}

@Injectable({ providedIn: 'root' })
export class RoomApi {
  constructor(private http: HttpClient) {}

  private get baseUrl(): string {
    const currentHost = window.location.hostname;
    const backendHost = currentHost === 'localhost' ? 'localhost' : currentHost;
    return `http://${backendHost}:8080`;
  }

  /** Create a new room */
  createRoom(): Observable<{ roomToken: string; shareableLink: string; message: string }> {
    return this.http.post<{ roomToken: string; shareableLink: string; message: string }>(`${this.baseUrl}/api/rooms/create`, {});
  }

  /** Join an existing room */
  joinRoom(token: string): Observable<{ message: string; hostUsername: string; roomToken: string; gameId?: string }> {
    return this.http.post<{ message: string; hostUsername: string; roomToken: string; gameId?: string }>(`${this.baseUrl}/api/rooms/join/${token}`, {});
  }

  /** Get room status */
  getRoomStatus(token: string): Observable<RoomStatus> {
    return this.http.get<RoomStatus>(`${this.baseUrl}/api/rooms/${token}`);
  }

  /** Start a game in a room (host only) */
  startGame(token: string): Observable<{ message: string; gameId?: string }> {
    return this.http.post<{ message: string; gameId?: string }>(`${this.baseUrl}/api/rooms/start/${token}`, {});
  }

  /** Delete a room (host only) */
  deleteRoom(token: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/api/rooms/${token}`);
  }

}
