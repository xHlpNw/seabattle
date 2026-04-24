import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  private authService = inject(AuthService);
  private router = inject(Router);

  canActivate(): boolean {
    const token = this.authService.getToken();
    const isValid = !!token && !this.isTokenExpired(token);

    if (isValid) {
      return true;
    } else {
      this.router.navigate(['/login']);
      return false;
    }
  }

  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return (payload.exp * 1000) < Date.now();
    } catch {
      return true;
    }
  }
}
