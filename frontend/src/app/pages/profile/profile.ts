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
  loading = true;
  error = '';

  async ngOnInit() {
    try {
      // Получаем профиль пользователя напрямую из UserApi
      this.user = await this.userApi.getProfile();
    } catch (err) {
      console.error('Не удалось загрузить профиль:', err);
      this.error = 'Не удалось загрузить профиль';
      // Редирект на страницу логина, если нет токена или username
      this.router.navigate(['login']);
    } finally {
      this.loading = false;
    }
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.router.navigate(['/login']);
  }

}
