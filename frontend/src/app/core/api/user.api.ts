import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

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

  // Dynamically determine backend URL based on current location
  private get base(): string {
    const currentHost = window.location.hostname;
    const backendHost = currentHost === 'localhost' ? 'localhost' : currentHost;
    return `http://${backendHost}:8080/api/users`;
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
