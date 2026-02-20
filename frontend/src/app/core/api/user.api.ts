import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { getApiBaseUrl } from './api-config';

interface LoginRequest {
  username: string;
  password: string;
}

interface LoginResponse {
  username: string;
  avatar: string;
  rating: number;
  token: string;
}

interface RegisterRequest {
  username: string;
  password: string;
  avatar?: string;
}

@Injectable({ providedIn: 'root' })
export class UserApi {

  private http = inject(HttpClient);

  private get base(): string {
    const apiBase = getApiBaseUrl();
    return apiBase ? `${apiBase}/api/users` : '/api/users';
  }

  private get backendBase(): string {
    const apiBase = getApiBaseUrl();
    return apiBase || '';
  }

  /** Проверка валидности токена на бэкенде (нужен Authorization: Bearer). */
  validateToken(): Observable<{ valid: boolean }> {
    return this.http.get<{ valid: boolean }>(`${this.backendBase}/api/auth/validate`);
  }

  login(data: LoginRequest): Promise<LoginResponse> {
    return firstValueFrom(this.http.post<LoginResponse>(`${this.base}/login`, data));
  }

  register(data: RegisterRequest): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base}/register`, data));
  }

  async getProfile(username: string): Promise<any> {
    const backendUrl = this.base.replace('/api/users', '');
    const res = await fetch(`${backendUrl}/api/users/profile?username=${username}`);
    if (!res.ok) throw new Error(`HTTP error ${res.status}`);
    return res.json();
  }

  async getTopPlayers(limit: number = 10): Promise<any[]> {
    const backendUrl = this.base.replace('/api/users', '');
    return fetch(`${backendUrl}/api/users/top?limit=${limit}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    }).then(r => r.json());
  }

  async getUserRank(username: string): Promise<any> {
    const backendUrl = this.base.replace('/api/users', '');
    return fetch(`${backendUrl}/api/users/rating/position?username=${username}`)
      .then(r => r.json());
  }

}
