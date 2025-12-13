import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ShipPayload {
  id: number;
  length: number;
  cells: { x: number; y: number }[];
  sunk: boolean;
}

export interface BoardPayload {
  size: number;
  cells: number[][];
  ships: ShipPayload[];
}

export interface Player {
  username: string;
  rating: number;
}

@Injectable({ providedIn: 'root' })
export class GameApi {
  constructor(private http: HttpClient) {}

  /** Создание игры с ботом */
  createBotGame(): Observable<{ gameId: string; message: string }> {
    return this.http.post<{ gameId: string; message: string }>('/api/bot/create', {});
  }

  /** Проверка незавершенных игр с ботом */
  getUnfinishedBotGames(): Observable<{ gameId: string; createdAt: string }[]> {
    return this.http.get<{ gameId: string; createdAt: string }[]>('/api/bot/unfinished');
  }

  getTopPlayers(limit: number) {
    return this.http.get<Player[]>(`/api/users/top?limit=${limit}`);
  }

  placeShipsAuto(gameId: string): Observable<{ message: string; grid: number[][] }> {
    return this.http.post<{ message: string; grid: number[][] }>(`/api/bot/${gameId}/place/auto`, {});
  }

  placeShips(gameId: string, payload: BoardPayload) {
    return this.http.post(`/api/games/${gameId}/place-ships`, payload);
  }

  getBoard(gameId: string) {
    return this.http.get<{grid: number[][]}>(`/api/games/${gameId}/board`);
  }

  getBoards(gameId: string): Observable<{ playerBoard: number[][]; enemyBoard: number[][]; currentTurn?: string; gameFinished: boolean; winner: string; opponentName: string; isBotGame: boolean }> {
    return this.http.get<{ playerBoard: number[][]; enemyBoard: number[][]; currentTurn?: string; gameFinished: boolean; winner: string; opponentName: string; isBotGame: boolean }>(`/api/games/${gameId}/boards`);
  }

  attackEnemy(gameId: string, x: number, y: number) {
    return this.http.post<{
      playerBoard: number[][];
      enemyBoard: number[][];

      hit: boolean;
      sunk: boolean;
      already: boolean;

      botX: number | null;
      botY: number | null;
      botHit: boolean | null;
      botSunk: boolean | null;

      gameFinished: boolean;
      winner: string | null;
      currentTurn: string | null;
    }>(`/api/games/${gameId}/attack`, { x, y });
  }

  botMove(gameId: string) {
    return this.http.post<{
      playerBoard: number[][];
      enemyBoard: number[][];

      botX: number | null;
      botY: number | null;
      botHit: boolean | null;
      botSunk: boolean | null;

      gameFinished: boolean;
      winner: string | null;
      currentTurn: string | null;
    }>(`/api/games/${gameId}/bot-move`, {});
  }

  /** Сдаться в игре */
  surrender(gameId: string): Observable<string> {
    return this.http.post(`/api/bot/${gameId}/surrender`, {}, { responseType: 'text' });
  }


}
