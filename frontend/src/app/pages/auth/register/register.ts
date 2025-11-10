import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { UserApi } from '../../../core/api/user.api';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'page-register',
  templateUrl: './register.html',
  styleUrls: ['./register.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule]
})
export class RegisterComponent {

  username = '';
  password = '';
  errorMsg = '';

  private userApi = inject(UserApi);
  private auth = inject(AuthService);
  private router = inject(Router);

  async doRegister() {
    this.errorMsg = '';

    try {
      await this.userApi.register({
        username: this.username,
        password: this.password
      });

      // после успешной регистрации логиним пользователя
      this.auth.login(this.username);
      this.router.navigate(['/']);

    } catch (e: any) {
      this.errorMsg = 'Ошибка регистрации: возможно имя уже занято';
    }
  }
}
