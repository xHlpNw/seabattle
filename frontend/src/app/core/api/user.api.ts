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
    return firstValueFrom(
      this.http.post<LoginResponse>(`${this.base}/login`, data)
    );
  }

  register(data: RegisterRequest): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base}/register`, data)
    );
  }

  async getProfile(username: string): Promise<{
    username: string;
    avatar: string;
    email?: string;
    rating: number;
    gamesPlayed?: number;
    wins?: number;
  }> {
    return firstValueFrom(
      this.http.get<any>(`${this.base}/${username}`)
    );
  }

}
