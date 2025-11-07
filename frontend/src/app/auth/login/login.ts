import { Component } from '@angular/core';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.html'
})
export class LoginComponent {
  username = '';
  password = '';

  constructor(private auth: AuthService, private router: Router) {}

  login() {
    this.auth.login({ username: this.username, password: this.password })
      .subscribe({
        next: res => {
          this.auth.setUser(res);
          this.router.navigate(['/home']);
        },
        error: (err: any) => alert('Ошибка входа: ' + (err.error?.message || err.message))
      });
  }

}
