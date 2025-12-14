import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { GameApi } from '../../core/api/game.api';
import { RoomApi, RoomStatus } from '../../core/api/room.api';
import { WebSocketService, RoomUpdate } from '../../core/ws/ws.service';

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
  private gameApi = inject(GameApi);
  private roomApi = inject(RoomApi);
  private wsService = inject(WebSocketService);

  roomToken: string = '';
  isCreatingLobby: boolean = false;
  isJoiningRoom: boolean = false;
  inviteLink: string = '';
  roomStatus: RoomStatus | null = null;
  isHost: boolean = false;
  opponentJoined: boolean = false;
  hostUsername: string = '';
  errorMessage: string = '';

  ngOnInit() {
    // Connect to WebSocket early
    this.connectToWebSocket();

    // Check if joining an existing room via query parameter
    this.route.queryParams.subscribe(params => {
      const roomToken = params['roomToken'];
      if (roomToken) {
        this.isJoiningRoom = true;
        this.roomToken = roomToken;
        this.loadRoomStatus();
      } else {
        // Create a new room
        this.createRoom();
      }
    });
  }

  createRoom() {
    this.isCreatingLobby = true;
    this.roomApi.createRoom().subscribe({
      next: (response) => {
        this.roomToken = response.roomToken;
        this.inviteLink = response.shareableLink || `${window.location.origin}/lobby/join/${this.roomToken}`;
        this.isCreatingLobby = false;
        // Subscribe to room updates now that we have the token
        this.subscribeToRoomUpdates();
        this.loadRoomStatus();
      },
      error: (error) => {
        this.isCreatingLobby = false;
        this.errorMessage = 'Failed to create room';
        console.error('Failed to create room:', error);
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

        // Check if guest has already joined
        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
          this.errorMessage = '';
        }

        if (status.status === 'IN_GAME') {
          // Game has started, redirect to game
          this.checkForExistingGame();
        } else {
          // Connect to WebSocket for real-time updates
          this.connectToWebSocket();
        }
      },
      error: (error) => {
        this.errorMessage = 'Failed to load room status';
        console.error('Failed to load room status:', error);
      }
    });
  }

  connectToWebSocket() {
    this.wsService.connect().subscribe({
      next: () => {
        // Subscribe to room updates if we have a room token
        if (this.roomToken) {
          this.subscribeToRoomUpdates();
        }
      },
      error: (error) => {
        console.error('Failed to connect to WebSocket:', error);
        // Fallback to polling if WebSocket fails
        this.startFallbackPolling();
      }
    });
  }

  subscribeToRoomUpdates() {
    if (this.roomToken) {
      this.wsService.subscribeToRoomUpdates(this.roomToken).subscribe((update: RoomUpdate | null) => {
        if (update) {
          this.handleRoomUpdate(update);
        }
      });
    }
  }

  handleRoomUpdate(update: RoomUpdate) {
    if (update.type === 'playerJoined' && update.username !== this.roomStatus?.hostUsername) {
      this.opponentJoined = true;
      this.errorMessage = '';
      // Refresh room status to get updated guest info
      this.checkIfOpponentJoined();
    }
  }

  checkIfOpponentJoined() {
    // Check room status to see if a guest has joined
    this.roomApi.getRoomStatus(this.roomToken).subscribe({
      next: (status) => {
        // Update room status with latest data
        this.roomStatus = status;
        // Check if guest has joined (not null and not the host)
        if (status.guestUsername && status.guestUsername !== status.hostUsername) {
          this.opponentJoined = true;
          this.errorMessage = '';
        }
      },
      error: (error) => {
        console.log('Error checking room status:', error);
      }
    });
  }

  startFallbackPolling() {
    // Fallback polling if WebSocket fails
    const pollInterval = setInterval(() => {
      this.checkIfOpponentJoined();
    }, 5000);
  }


  checkForExistingGame() {
    // If game exists, redirect to it
    // This would be called when joining a room that already has a game
    this.roomApi.joinRoom(this.roomToken).subscribe({
      next: (response) => {
        if (response.gameId) {
          this.router.navigate(['/game', response.gameId, 'play']);
        }
      },
      error: (error) => {
        console.error('Failed to check for existing game:', error);
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

  createOnlineGame() {
    if (!this.roomToken) return;

    this.isCreatingLobby = true;
    // Start the game (only host can do this)
    this.roomApi.startGame(this.roomToken).subscribe({
      next: (response) => {
        this.isCreatingLobby = false;
        if (response.gameId) {
          // Notify other players that game is starting
          this.wsService.sendMessage('status', {
            roomToken: this.roomToken,
            status: 'starting'
          });
          this.router.navigate(['/game', response.gameId, 'play']);
        } else {
          this.errorMessage = 'Failed to start game';
        }
      },
      error: (error) => {
        this.isCreatingLobby = false;
        this.errorMessage = error.error?.message || 'Failed to start game';
      }
    });
  }

  ngOnDestroy() {
    // Clean up WebSocket connection
    this.wsService.disconnect();
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}



