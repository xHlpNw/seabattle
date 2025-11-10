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

  login(username: string) {
    this.tokenStorage.setToken(username);
    this._isLoggedIn$.next(true);
  }

  logout() {
    this.tokenStorage.clearToken();
    this._isLoggedIn$.next(false);
    this.router.navigate(['/']);
  }

  getUsername(): string | null {
    return this.tokenStorage.getToken();
  }
}
