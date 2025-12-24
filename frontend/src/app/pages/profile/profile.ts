import { Component, inject } from '@angular/core';
import { UserApi } from '../../core/api/user.api';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'page-profile',
  templateUrl: './profile.html',
  styleUrls: ['./profile.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule]
})
export class ProfileComponent {

  private userApi = inject(UserApi);
  private router = inject(Router);
  private title = inject(Title);
  private authService = inject(AuthService);

  user: any = null;
  topPlayers: any[] = [];
  rank: number | null = null;
  loading = true;
  error = '';

  async ngOnInit() {
    try {
      // username можно взять из localStorage или из токена
          const username = localStorage.getItem('username')!;
          this.user = await this.userApi.getProfile(username);

          // Обновляем заголовок страницы с именем пользователя
          this.title.setTitle(`Profile — ${this.user.username}`);

          // Получаем топ 10 игроков для leaderboard
          this.topPlayers = await this.userApi.getTopPlayers(10);
    } catch (err) {
      console.error(err);
          this.router.navigate(['login']);
    } finally {
      this.loading = false;
    }
  }

  goHome() {
    this.router.navigate(['/']); // путь к твоей homepage
  }

  logout() {
    this.authService.logout();
  }

  getBackendUrl(): string {
    const currentHost = window.location.hostname;
    const backendHost = currentHost === 'localhost' ? 'localhost' : currentHost;
    return `http://${backendHost}:8080`;
  }

}
