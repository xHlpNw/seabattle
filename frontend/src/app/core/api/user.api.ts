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
  private base = '/api/users';

  login(data: LoginRequest): Promise<LoginResponse> {
    return firstValueFrom(this.http.post<LoginResponse>(`${this.base}/login`, data));
  }

  register(data: RegisterRequest): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base}/register`, data));
  }

  async getProfile(username: string): Promise<any> {
    const res = await fetch(`http://localhost:8080/api/users/profile?username=${username}`);
    if (!res.ok) throw new Error(`HTTP error ${res.status}`);
    return res.json();
  }

  async getTopPlayers(limit: number = 10): Promise<any[]> {
    return fetch(`http://localhost:8080/api/users/top?limit=${limit}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    }).then(r => r.json());
  }

  async getUserRank(username: string): Promise<any> {
    return fetch(`http://localhost:8080/api/users/rating/position?username=${username}`)
      .then(r => r.json());
  }

}
