import { Component } from '@angular/core';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './register.html'
})
export class RegisterComponent {
  username = '';
  email = '';
  password = '';

  constructor(private auth: AuthService, private router: Router) {}

  register() {
    this.auth.register({ username: this.username, email: this.email, password: this.password })
      .subscribe({
        next: res => {
          // ...
        },
        error: (err: any) => alert('Ошибка регистрации: ' + (err.error?.message || err.message))
      });

  }
}
