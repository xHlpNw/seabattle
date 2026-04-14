import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { UserApi } from '../api/user.api';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private tokenStorage = inject(TokenStorageService);
  private router = inject(Router);
  private userApi = inject(UserApi);

  private _isLoggedIn$ = new BehaviorSubject<boolean>(false);
  isLoggedIn$ = this._isLoggedIn$.asObservable();

  constructor() {
    this._isLoggedIn$.next(false);
  }

  validateToken(): Promise<void> {
    const token = this.tokenStorage.getToken();
    if (!token || this.isTokenExpired(token)) {
      this.tokenStorage.clearToken();
      localStorage.removeItem('username');
      this._isLoggedIn$.next(false);
      return Promise.resolve();
    }
    return firstValueFrom(this.userApi.validateToken())
      .then(() => this._isLoggedIn$.next(true))
      .catch(() => {
        this.tokenStorage.clearToken();
        localStorage.removeItem('username');
        this._isLoggedIn$.next(false);
      });
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
    this.router.navigateByUrl('/', { replaceUrl: true });
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
