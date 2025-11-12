import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { GameApi } from '../../core/api/game.api'

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

  ngOnInit() {
    this.auth.isLoggedIn$.subscribe(v => this.isLoggedIn = v);
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
