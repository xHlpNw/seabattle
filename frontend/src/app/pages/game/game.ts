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
  profile: any = null;

  playerBoard: number[][] = [];
  enemyBoard: number[][] = [];

  isLoading: boolean = true;
  gameOver: boolean = false;

  botLastX: number | null = null;
  botLastY: number | null = null;

  showResultModal: boolean = false;
  resultText: string = "";

  async ngOnInit() {
    const token = localStorage.getItem('token') ?? undefined;
    const username = localStorage.getItem('username');

    if (!username || !token) {
      console.error('Пользователь не авторизован');
      this.router.navigate(['/login']);
      return;
    }

    try {
      this.profile = await this.userApi.getProfile(username);
      console.log('Профиль текущего пользователя:', this.profile);
    } catch (err) {
      console.error('Ошибка получения профиля', err);
      this.router.navigate(['/login']);
      return;
    }

    this.route.paramMap.subscribe(async params => {
      this.gameId = params.get('gameId');
      if (this.gameId) {
        await this.loadBoards();
      }
    });
  }

  async loadBoards() {
    if (!this.gameId) return;

    this.isLoading = true;
    try {
      // Расширяем API, чтобы возвращался полный AttackResult-like объект
      const res: any = await firstValueFrom(this.gameApi.getBoards(this.gameId));

      this.playerBoard = res.playerBoard;
      this.enemyBoard = res.enemyBoard;

      // Проверяем, закончена ли игра
      if (res.gameFinished) {
        this.gameOver = true;
        this.showResultModal = true;
        this.resultText = res.winner === 'HOST_WIN'
          ? "🎉 Вы победили!"
          : res.winner === 'GUEST_WIN'
            ? "💀 Вы проиграли!"
            : "Игра завершена";
      }

    } catch (err) {
      console.error('Ошибка получения досок:', err);
      this.playerBoard = this.createEmptyGrid();
      this.enemyBoard = this.createEmptyGrid();
    } finally {
      this.isLoading = false;
    }
  }


  createEmptyGrid(): number[][] {
    return Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  goToHome() {
    this.showResultModal = false;
    this.router.navigate(['/']); // переход на главную страницу
  }

  attackEnemy(i: number, j: number) {
    if (!this.gameId) return;

    this.gameApi.attackEnemy(this.gameId, i, j).subscribe(res => {
      console.log('Ответ сервера после выстрела:', res);

      this.playerBoard = res.playerBoard;

      this.enemyBoard = res.enemyBoard;

      if (res.hit) console.log('Попадание!');
      if (res.sunk) console.log('Корабль потоплен!');
      if (res.already) console.log('Вы уже стреляли сюда');

      if (res.botX != null && res.botY != null) {
        this.botLastX = res.botX;
        this.botLastY = res.botY;
        console.log(`Бот стрелял: ${res.botX}, ${res.botY}`);
      }

      if (res.gameFinished) {
        this.gameOver = true;

        if (res.winner === 'HOST') {
          this.resultText = "🎉 Вы победили!";
        } else if (res.winner === 'GUEST') {
          this.resultText = "💀 Вы проиграли!";
        } else {
          this.resultText = "Игра завершена";
        }

        this.showResultModal = true; // ← показываем модалку
      }

    });
  }
}
