import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';
import { TokenStorageService } from './token-storage.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private tokenStorage = inject(TokenStorageService);
  private router = inject(Router);

  private _isLoggedIn$ = new BehaviorSubject<boolean>(false);
  isLoggedIn$ = this._isLoggedIn$.asObservable();

  constructor() {
    console.log('AuthService: Initializing...');
    const token = this.tokenStorage.getToken();
    console.log('AuthService: Token from storage =', !!token);
    const isValidToken = token && !this.isTokenExpired(token);
    console.log('AuthService: Token is valid =', isValidToken);
    this._isLoggedIn$.next(!!isValidToken);
    console.log('AuthService: Set initial isLoggedIn to', !!isValidToken);
  }

  login(token: string, username?: string) {
    this.tokenStorage.setToken(token);
    if (username) {
      localStorage.setItem('username', username);
    }
    this._isLoggedIn$.next(true);
  }

  logout() {
    this.tokenStorage.clearToken();
    localStorage.removeItem('username');
    this._isLoggedIn$.next(false);
    this.router.navigate(['/']);
  }

  getToken(): string | null {
    return this.tokenStorage.getToken();
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
