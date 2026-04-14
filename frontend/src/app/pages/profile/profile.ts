import { Component, ElementRef, inject, ViewChild } from '@angular/core';
import { UserApi } from '../../core/api/user.api';
import { getApiBaseUrl } from '../../core/api/api-config';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'page-profile',
  templateUrl: './profile.html',
  styleUrls: ['./profile.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink]
})
export class ProfileComponent {

  @ViewChild('avatarInput') avatarInput!: ElementRef<HTMLInputElement>;

  private userApi = inject(UserApi);
  private router = inject(Router);
  private title = inject(Title);
  private authService = inject(AuthService);

  user: any = null;
  topPlayers: any[] = [];
  loading = true;
  error = '';
  avatarUploading = false;
  avatarError = '';

  async ngOnInit() {
    try {
      const username = localStorage.getItem('username')!;
      this.user = await this.userApi.getProfile(username);
      this.title.setTitle(`Профиль — ${this.user.username}`);
      this.topPlayers = await this.userApi.getTopPlayers(10);
    } catch (err) {
      console.error(err);
      this.router.navigate(['login']);
    } finally {
      this.loading = false;
    }
  }

  goHome() {
    this.router.navigate(['/']);
  }

  logout() {
    this.authService.logout();
  }

  getBackendUrl(): string {
    return getApiBaseUrl() || '';
  }

  openAvatarPicker() {
    this.avatarInput.nativeElement.click();
  }

  async onAvatarSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    input.value = '';

    if (!file.type.startsWith('image/')) {
      this.avatarError = 'Разрешены только изображения';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.avatarError = 'Размер файла не должен превышать 5 МБ';
      return;
    }

    this.avatarUploading = true;
    this.avatarError = '';
    try {
      const result = await this.userApi.uploadAvatar(file);
      this.user = { ...this.user, avatar: result.avatar };
    } catch (err: any) {
      this.avatarError = err.message || 'Не удалось загрузить аватар';
    } finally {
      this.avatarUploading = false;
    }
  }

}
