import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';

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

  isLoggedIn = false;

  ngOnInit() {
    this.auth.isLoggedIn$.subscribe(v => this.isLoggedIn = v);
  }

  goLogin() {
    this.router.navigate(['/auth/login']);
  }

  goProfile() {
    this.router.navigate(['/profile']);
  }

  playBot() {
    this.router.navigate(['/setup']);
  }

  playOnline() {
    this.router.navigate(['/lobby']);
  }
}
