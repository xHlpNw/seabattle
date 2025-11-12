import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { UserApi } from '../../../core/api/user.api';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'page-login',
  templateUrl: './login.html',
  styleUrls: ['./login.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule, RouterModule]
})
export class LoginComponent {

  username = '';
  password = '';
  errorMsg = '';

  private userApi = inject(UserApi);
  private auth = inject(AuthService);
  private router = inject(Router);

  async doLogin() {
    this.errorMsg = '';

    try {
      const res = await this.userApi.login({
        username: this.username,
        password: this.password
      });

      console.log('Before login', res);
      this.auth.login(res.token);
      console.log('After login');
      this.router.navigate(['/']);

    } catch (e: any) {
      this.errorMsg = 'Неверный логин или пароль';
    }
  }
}
