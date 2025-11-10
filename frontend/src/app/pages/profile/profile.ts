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
    const username = this.auth.getUsername();
    if (!username) {
      this.router.navigate(['/auth/login']);
      return;
    }

    try {
      this.user = await this.userApi.getProfile(username);
    } catch (err) {
      this.error = 'Не удалось загрузить профиль';
    } finally {
      this.loading = false;
    }
  }

  logout() {
    this.auth.logout();
  }
}
