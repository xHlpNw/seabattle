import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RoomStatus {
  token: string;
  status: string;
  hostUsername: string;
  isHost: boolean;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
}

@Injectable({ providedIn: 'root' })
export class RoomApi {
  constructor(private http: HttpClient) {}

  /** Create a new room */
  createRoom(): Observable<{ roomToken: string; shareableLink: string; message: string }> {
    return this.http.post<{ roomToken: string; shareableLink: string; message: string }>('/api/rooms/create', {});
  }

  /** Join an existing room */
  joinRoom(token: string): Observable<{ message: string; hostUsername: string; roomToken: string; gameId?: string }> {
    return this.http.post<{ message: string; hostUsername: string; roomToken: string; gameId?: string }>(`/api/rooms/join/${token}`, {});
  }

  /** Get room status */
  getRoomStatus(token: string): Observable<RoomStatus> {
    return this.http.get<RoomStatus>(`/api/rooms/${token}`);
  }

  /** Delete a room (host only) */
  deleteRoom(token: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`/api/rooms/${token}`);
  }
}
