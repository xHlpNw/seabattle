import { Component, inject } from '@angular/core';
import { UserApi } from '../../core/api/user.api';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

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
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.router.navigate(['/login']);
  }

}
