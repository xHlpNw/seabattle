import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {
  private authService = inject(AuthService);
  private router = inject(Router);

  canActivate(): boolean {
    const token = this.authService.getToken();
    const role = this.authService.getRole();

    if (!token) {
      this.router.navigate(['/login']);
      return false;
    }

    if (role === 'ADMIN') {
      return true;
    }

    this.router.navigate(['/']);
    return false;
  }
}
