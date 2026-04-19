import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { getApiBaseUrl } from '../../core/api/api-config';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

type AdminStats = {
  totalUsers: number;
  activeUsers: number;
  blockedUsers: number;
  inProgressGames: number;
  finishedGames: number;
  waitingRooms: number;
};

type Paged<T> = {
  content: T[];
  totalPages: number;
  number: number;
};

type AdminUser = {
  id: string;
  username: string;
  rating: number;
  wins: number;
  losses: number;
  role: string;
  status: string;
};

type AdminGame = {
  id: string;
  type: string;
  status: string;
  hostUsername: string | null;
  guestUsername: string | null;
  botGame: boolean;
};

type AdminRoom = {
  id: string;
  token: string;
  status: string;
  hostUsername: string | null;
  guestUsername: string | null;
  expiresAt: string | null;
};

@Component({
  selector: 'page-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin.html',
  styleUrls: ['./admin.scss']
})
export class AdminComponent {
  private http = inject(HttpClient);
  private readonly base = getApiBaseUrl() ? `${getApiBaseUrl()}/api/admin` : '/api/admin';
  private readonly pageSize = 200;

  stats: AdminStats | null = null;
  users: AdminUser[] = [];
  games: AdminGame[] = [];
  rooms: AdminRoom[] = [];
  loading = false;
  error = '';
  activeTab: 'users' | 'games' | 'rooms' = 'users';

  ngOnInit(): void {
    this.loadStats();
  }

  loadStats(): void {
    this.loading = true;
    this.error = '';
    this.http.get<AdminStats>(`${this.base}/stats`).subscribe({
      next: (data) => {
        this.stats = data;
        Promise.all([this.loadUsers(), this.loadGames(), this.loadRooms()])
          .finally(() => {
            this.loading = false;
          });
      },
      error: () => {
        this.error = 'Не удалось загрузить статистику админ-панели';
        this.loading = false;
      }
    });
  }

  setTab(tab: 'users' | 'games' | 'rooms'): void {
    this.activeTab = tab;
  }

  async loadUsers(): Promise<void> {
    try {
      this.users = await this.loadAllPages<AdminUser>(`${this.base}/users`);
    } catch {
      this.error = 'Не удалось загрузить пользователей';
    }
  }

  async loadGames(): Promise<void> {
    try {
      this.games = await this.loadAllPages<AdminGame>(`${this.base}/games`);
    } catch {
      this.error = 'Не удалось загрузить игры';
    }
  }

  async loadRooms(): Promise<void> {
    try {
      this.rooms = await this.loadAllPages<AdminRoom>(`${this.base}/rooms`);
    } catch {
      this.error = 'Не удалось загрузить комнаты';
    }
  }

  saveUser(user: AdminUser): void {
    this.http
      .patch(`${this.base}/users/${user.id}`, {
        rating: user.rating,
        wins: user.wins,
        losses: user.losses,
        role: user.role,
        status: user.status
      })
      .subscribe({
        next: () => {
          this.loadUsers();
          this.loadStats();
        },
        error: () => (this.error = `Не удалось обновить пользователя ${user.username}`)
      });
  }

  saveGame(game: AdminGame): void {
    this.http
      .patch(`${this.base}/games/${game.id}`, {
        status: game.status
      })
      .subscribe({
        next: () => {
          this.loadGames();
          this.loadStats();
        },
        error: () => (this.error = `Не удалось обновить игру ${game.id}`)
      });
  }

  saveRoom(room: AdminRoom): void {
    this.http
      .patch(`${this.base}/rooms/${room.id}`, {
        status: room.status
      })
      .subscribe({
        next: () => {
          this.loadRooms();
          this.loadStats();
        },
        error: () => (this.error = `Не удалось обновить комнату ${room.id}`)
      });
  }

  deleteUser(user: AdminUser): void {
    if (!confirm(`Удалить пользователя ${user.username}?`)) return;
    this.http.delete(`${this.base}/users/${user.id}`).subscribe({
      next: () => {
        this.loadUsers();
        this.loadStats();
      },
      error: () => (this.error = `Не удалось удалить пользователя ${user.username}`)
    });
  }

  deleteGame(game: AdminGame): void {
    if (!confirm(`Удалить игру ${game.id}?`)) return;
    this.http.delete(`${this.base}/games/${game.id}`).subscribe({
      next: () => {
        this.loadGames();
        this.loadStats();
      },
      error: () => (this.error = `Не удалось удалить игру ${game.id}`)
    });
  }

  deleteRoom(room: AdminRoom): void {
    if (!confirm(`Удалить комнату ${room.token}?`)) return;
    this.http.delete(`${this.base}/rooms/${room.id}`).subscribe({
      next: () => {
        this.loadRooms();
        this.loadStats();
      },
      error: () => (this.error = `Не удалось удалить комнату ${room.token}`)
    });
  }

  private async loadAllPages<T>(endpoint: string): Promise<T[]> {
    const records: T[] = [];
    let page = 0;
    let totalPages = 1;

    while (page < totalPages) {
      const url = `${endpoint}?page=${page}&size=${this.pageSize}`;
      const response = await firstValueFrom(this.http.get<Paged<T>>(url));
      records.push(...response.content);
      totalPages = response.totalPages ?? 1;
      page += 1;
    }

    return records;
  }
}
