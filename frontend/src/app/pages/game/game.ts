import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { GameApi } from '../../core/api/game.api';
import { UserApi } from '../../core/api/user.api';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'page-game',
  templateUrl: './game.html',
  styleUrls: ['./game.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class GameComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private gameApi = inject(GameApi);
  private userApi = inject(UserApi);

  gameId: string | null = null;
  grid: number[][] = [];
  profile: any = null;

  async ngOnInit() {
    const token = localStorage.getItem('token') ?? undefined;
    const username = localStorage.getItem('username');

    if (!username || !token) {
      console.error('Пользователь не авторизован');
      this.router.navigate(['/login']);
      return;
    }

    try {
      this.profile = await this.userApi.getProfile();
      console.log('Профиль текущего пользователя:', this.profile);
    } catch (err) {
      console.error('Ошибка получения профиля', err);
      this.router.navigate(['/login']);
      return;
    }

    this.route.paramMap.subscribe(async params => {
      this.gameId = params.get('gameId');
      if (this.gameId) {
        try {
          const board = await firstValueFrom(this.gameApi.getBoard(this.gameId));
          console.log('Board from API:', board);

          // Проверяем, есть ли grid, иначе создаём пустое поле
          this.grid = board?.grid || this.createEmptyGrid();
        } catch (err: any) {
          console.error('Ошибка получения доски:', err);
          this.grid = this.createEmptyGrid();
        }
      }
    });
  }

  createEmptyGrid(): number[][] {
    return Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  // Дополнительно: для отображения классов клеток
  cellClass(value: number): string {
    switch (value) {
      case 0: return 'empty';
      case 1: return 'ship';
      default: return '';
    }
  }
}
