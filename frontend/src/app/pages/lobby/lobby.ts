import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RoomApi, RoomStatus } from '../../core/api/room.api';

@Component({
  selector: 'page-lobby',
  templateUrl: './lobby.html',
  styleUrls: ['./lobby.scss'],
  standalone: true,
  imports: [CommonModule, RouterLink]
})
export class LobbyComponent implements OnInit, OnDestroy {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private roomApi = inject(RoomApi);

  roomToken: string = '';
  inviteLink: string = '';
  roomStatus: RoomStatus | null = null;
  isHost: boolean = false;
  opponentJoined: boolean = false;
  hostUsername: string = '';
  errorMessage: string = '';
  isLoading: boolean = true;

  private pollingInterval: any;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      if (token) {
        this.roomToken = token;
        this.inviteLink = `${window.location.origin}/lobby/join/${this.roomToken}`;
        this.loadRoomStatus();
      } else {
        this.errorMessage = 'Неверный код лобби';
        this.isLoading = false;
      }
    });
  }

  loadRoomStatus() {
    if (!this.roomToken) return;

    this.roomApi.getRoomStatus(this.roomToken).subscribe({
      next: (status) => {
        this.roomStatus = status;
        this.isHost = status.isHost;
        this.hostUsername = status.hostUsername;
        this.isLoading = false;

        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
        }

        if (status.status === 'IN_GAME') {
          this.router.navigate(['/game'], { queryParams: { roomToken: this.roomToken } });
        } else {
          this.startPolling();
        }
      },
      error: () => {
        this.errorMessage = 'Не удалось загрузить состояние лобби';
        this.isLoading = false;
      }
    });
  }

  startPolling() {
    this.pollingInterval = setInterval(() => {
      this.refreshRoomStatus();
    }, 5000);
  }

  private refreshRoomStatus() {
    if (!this.roomToken) return;

    this.roomApi.getRoomStatus(this.roomToken).subscribe({
      next: (status) => {
        this.roomStatus = status;
        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
          this.errorMessage = '';
        }

        if (status.status === 'IN_GAME' && status.gameId) {
          clearInterval(this.pollingInterval);
          this.router.navigate(['/setup'], { queryParams: { gameId: status.gameId } });
        }
      },
      error: () => {}
    });
  }

  copyInviteLink() {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(this.inviteLink).catch(() => {
        this.fallbackCopyToClipboard();
      });
    } else {
      this.fallbackCopyToClipboard();
    }
  }

  private fallbackCopyToClipboard() {
    try {
      const textArea = document.createElement('textarea');
      textArea.value = this.inviteLink;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      const successful = document.execCommand('copy');
      document.body.removeChild(textArea);

      if (!successful) {
        throw new Error('execCommand copy failed');
      }
    } catch (err) {
      alert(`Скопируйте ссылку вручную:\n\n${this.inviteLink}`);
    }
  }

  startGame() {
    if (!this.roomToken || !this.isHost || !this.opponentJoined) return;

    this.roomApi.startGame(this.roomToken).subscribe({
      next: (response) => {
        if (response.gameId) {
          this.router.navigate(['/setup'], { queryParams: { gameId: response.gameId } });
        } else {
          this.errorMessage = 'Не удалось начать игру';
        }
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Не удалось начать игру';
      }
    });
  }

  ngOnDestroy() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}
