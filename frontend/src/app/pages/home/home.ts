import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { GameApi } from '../../core/api/game.api'

export interface Player {
  username: string;
  rating: number;
}

@Component({
  selector: 'page-home',
  templateUrl: './home.html',
  styleUrls: ['./home.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule]
})
export class HomeComponent {

  private auth = inject(AuthService);
  private router = inject(Router);
  private gameApi = inject(GameApi);

  isLoggedIn = false;
  topPlayers: Player[] = [];

  ngOnInit() {
    this.auth.isLoggedIn$.subscribe(v => this.isLoggedIn = v);

    this.gameApi.getTopPlayers(5).subscribe({
      next: (players) => {
        this.topPlayers = players;
      },
      error: (err) => {
        console.error('Ошибка при получении топ игроков:', err);
      }
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }

  goProfile() {
    this.router.navigate(['/profile']);
  }

  playBot() {
    console.log('Токен:', this.auth.getToken());
    if (!this.isLoggedIn) {
      this.router.navigate(['/login']);
      return;
    }

    this.gameApi.createBotGame().subscribe({
      next: (res) => {
        console.log('✅ Игра с ботом создана:', res);
        this.router.navigate(['/setup'], { queryParams: { gameId: res.gameId } });
      },
      error: (err) => {
        console.error('❌ Ошибка при создании игры с ботом:', err);
      }
    });
  }

  playOnline() {
    this.router.navigate(['/lobby']);
  }
}
