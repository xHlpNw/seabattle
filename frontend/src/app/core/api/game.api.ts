import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getApiBaseUrl } from './api-config';

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

  private get baseUrl(): string {
    return getApiBaseUrl() || '';
  }

  /** Создание игры с ботом */
  createBotGame(): Observable<{ gameId: string; message: string }> {
    return this.http.post<{ gameId: string; message: string }>(`${this.baseUrl}/api/bot/create`, {});
  }

  /** Создание онлайн игры */
  createOnlineGame(roomToken: string): Observable<{ gameId: string; message: string }> {
    return this.http.post<{ gameId: string; message: string }>(`${this.baseUrl}/api/games/online/create?roomToken=${roomToken}`, {});
  }

  /** Проверка незавершенных игр с ботом */
  getUnfinishedBotGames(): Observable<{ gameId: string; createdAt: string }[]> {
    return this.http.get<{ gameId: string; createdAt: string }[]>(`${this.baseUrl}/api/bot/unfinished`);
  }

  getTopPlayers(limit: number) {
    return this.http.get<Player[]>(`${this.baseUrl}/api/users/top?limit=${limit}`);
  }

  placeShipsAuto(gameId: string): Observable<{ message: string; grid: number[][] }> {
    return this.http.post<{ message: string; grid: number[][] }>(`${this.baseUrl}/api/bot/${gameId}/place/auto`, {});
  }

  placeShips(gameId: string, payload: BoardPayload) {
    return this.http.post(`${this.baseUrl}/api/games/${gameId}/place-ships`, payload);
  }

  markReady(gameId: string): Observable<{ message: string; bothReady: boolean; gameStarted: boolean }> {
    return this.http.post<{ message: string; bothReady: boolean; gameStarted: boolean }>(`${this.baseUrl}/api/games/${gameId}/ready`, {});
  }

  getBoard(gameId: string) {
    return this.http.get<{grid: number[][]}>(`${this.baseUrl}/api/games/${gameId}/board`);
  }

  getBoards(gameId: string): Observable<{ playerBoard: number[][]; enemyBoard: number[][]; currentTurn?: string | null; gameFinished: boolean; winner: string; opponentName: string; isBotGame: boolean; isHost: boolean }> {
    return this.http.get<{ playerBoard: number[][]; enemyBoard: number[][]; currentTurn?: string; gameFinished: boolean; winner: string; opponentName: string; isBotGame: boolean; isHost: boolean }>(`${this.baseUrl}/api/games/${gameId}/boards`);
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
    }>(`${this.baseUrl}/api/games/${gameId}/attack`, { x, y });
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
    }>(`${this.baseUrl}/api/games/${gameId}/bot-move`, {});
  }

  /** Сдаться в игре (бот или онлайн — вызывается нужный эндпоинт) */
  surrender(gameId: string, isBotGame: boolean): Observable<string> {
    const path = isBotGame
      ? `${this.baseUrl}/api/bot/${gameId}/surrender`
      : `${this.baseUrl}/api/games/${gameId}/surrender`;
    return this.http.post(path, {}, { responseType: 'text' });
  }


}
