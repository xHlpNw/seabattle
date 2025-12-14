import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  private authService = inject(AuthService);
  private router = inject(Router);

  canActivate(): boolean {
    console.log('AuthGuard: Checking authentication...');
    const token = this.authService.getToken();
    const hasToken = !!token;
    const isTokenValid = hasToken && !this.isTokenExpired(token);

    console.log('AuthGuard: token exists =', hasToken);
    console.log('AuthGuard: token is valid =', isTokenValid);

    if (isTokenValid) {
      console.log('AuthGuard: Allowing access to protected route');
      return true;
    } else {
      console.log('AuthGuard: Blocking access, redirecting to login');
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
