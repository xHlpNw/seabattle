import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
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

  private auth = inject(AuthService);
  private userApi = inject(UserApi);
  private router = inject(Router);

  user: any = null;
  loading = true;
  error = '';

  async ngOnInit() {
      const token = this.auth.getToken() || undefined;

          if (!token) {
            this.router.navigate(['login']);
            return;
          }

      const username = this.getUsernameFromToken(token); // извлекаем username
      if (!username) {
        this.router.navigate(['login']);
        return;
      }

      try {
        this.user = await this.userApi.getProfile(username, token);
      } catch (err) {
        this.error = 'Не удалось загрузить профиль';
      } finally {
        this.loading = false;
      }

    }

  logout() {
    this.auth.logout();
  }

  private getUsernameFromToken(token: string): string | null {
    try {
      const payloadBase64 = token.split('.')[1];
      const payload = JSON.parse(atob(payloadBase64));
      return payload.sub || null; // <-- здесь sub
    } catch (e) {
      return null;
    }
  }
}
