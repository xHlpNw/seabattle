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
    const username = this.tokenStorage.getToken();
    this._isLoggedIn$.next(!!username);
  }

  login(token: string) {
    this.tokenStorage.setToken(token);
    this._isLoggedIn$.next(true);
  }

  logout() {
    this.tokenStorage.clearToken();
    this._isLoggedIn$.next(false);
    this.router.navigate(['/']);
  }

  getToken(): string | null {
    return this.tokenStorage.getToken();
  }
}
