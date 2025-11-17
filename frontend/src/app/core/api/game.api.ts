import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class GameApi {
  constructor(private http: HttpClient) {}

  /** Создание игры с ботом */
  createBotGame(): Observable<{ gameId: string; message: string }> {
    return this.http.post<{ gameId: string; message: string }>('/api/bot/create', {});
  }


  placeShipsAuto(gameId: string): Observable<{ message: string; grid: number[][] }> {
    return this.http.post<{ message: string; grid: number[][] }>(`/api/bot/${gameId}/place/auto`, {});
  }

  placeShips(gameId: string, grid: number[][]) {
    return this.http.post(`/api/games/${gameId}/place-ships`, { grid });
  }

  getBoard(gameId: string) {
    return this.http.get<{grid: number[][]}>(`/api/games/${gameId}/board`);
  }

}
