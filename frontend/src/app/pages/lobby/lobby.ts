import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RoomApi, RoomStatus } from '../../core/api/room.api';

@Component({
  selector: 'page-lobby',
  templateUrl: './lobby.html',
  styleUrls: ['./lobby.scss'],
  standalone: true,
  imports: [CommonModule]
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
    // Get room token from query params (passed from home page)
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      if (token) {
        this.roomToken = token;
        this.inviteLink = `${window.location.origin}/lobby/join/${this.roomToken}`;
        this.loadRoomStatus();
      } else {
        this.errorMessage = 'Invalid lobby token';
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

        // Check if opponent has joined
        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
        }

        // If game already started, redirect to game
        if (status.status === 'IN_GAME') {
          this.router.navigate(['/game'], { queryParams: { roomToken: this.roomToken } });
        } else {
          // Start polling for updates
          this.startPolling();
        }
      },
      error: (error) => {
        this.errorMessage = 'Failed to load lobby status';
        this.isLoading = false;
        console.error('Failed to load room status:', error);
      }
    });
  }

  startPolling() {
    // Start polling every 5 seconds to check for opponent joining
    this.pollingInterval = setInterval(() => {
      this.refreshRoomStatus();
    }, 5000);
  }

  private refreshRoomStatus() {
    if (!this.roomToken) return;

    this.roomApi.getRoomStatus(this.roomToken).subscribe({
      next: (status) => {
        this.roomStatus = status;
        // Update opponent joined status
        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
          this.errorMessage = '';
        }

        // If game started, redirect to setup
        if (status.status === 'IN_GAME' && status.gameId) {
          clearInterval(this.pollingInterval);
          console.log('Game started, redirecting to setup with gameId:', status.gameId);
          this.router.navigate(['/setup'], { queryParams: { gameId: status.gameId } });
        }
      },
      error: (error) => {
        console.log('Error refreshing room status:', error);
      }
    });
  }

  copyInviteLink() {
    // Try modern clipboard API first
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(this.inviteLink).then(() => {
        // Link copied successfully
      }).catch(err => {
        console.error('Modern clipboard API failed:', err);
        this.fallbackCopyToClipboard();
      });
    } else {
      // Fallback for browsers without clipboard API
      this.fallbackCopyToClipboard();
    }
  }

  private fallbackCopyToClipboard() {
    try {
      // Create a temporary textarea element
      const textArea = document.createElement('textarea');
      textArea.value = this.inviteLink;
      textArea.style.position = 'fixed';
      textArea.style.left = '-999999px';
      textArea.style.top = '-999999px';
      document.body.appendChild(textArea);

      // Select and copy the text
      textArea.focus();
      textArea.select();

      const successful = document.execCommand('copy');
      document.body.removeChild(textArea);

      if (successful) {
        // Link copied successfully
      } else {
        throw new Error('execCommand copy failed');
      }
    } catch (err) {
      console.error('Fallback clipboard copy failed:', err);
      // Last resort: show the link and ask user to copy manually
      alert(`Please copy this link manually:\n\n${this.inviteLink}`);
    }
  }

  startGame() {
    if (!this.roomToken || !this.isHost || !this.opponentJoined) return;

    this.roomApi.startGame(this.roomToken).subscribe({
      next: (response) => {
        if (response.gameId) {
          this.router.navigate(['/setup'], { queryParams: { gameId: response.gameId } });
        } else {
          this.errorMessage = 'Failed to start game';
        }
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Failed to start game';
      }
    });
  }

  ngOnDestroy() {
    // Clean up polling interval
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
    }
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}