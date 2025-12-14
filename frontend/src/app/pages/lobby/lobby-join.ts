import { Component, inject, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RoomApi } from '../../core/api/room.api';
import { WebSocketService } from '../../core/ws/ws.service';

@Component({
  selector: 'page-lobby-join',
  templateUrl: './lobby-join.html',
  styleUrls: ['./lobby.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class LobbyJoinComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private roomApi = inject(RoomApi);
  private wsService = inject(WebSocketService);

  roomToken: string = '';
  isJoining: boolean = true;
  joinError: string = '';
  hostUsername: string = '';

  ngOnInit() {
    // Get the room token from the URL
    this.route.params.subscribe(params => {
      this.roomToken = params['token'];
      if (this.roomToken) {
        this.joinRoom();
      } else {
        this.joinError = 'Invalid room link';
        this.isJoining = false;
      }
    });
  }

  joinRoom() {
    this.roomApi.joinRoom(this.roomToken).subscribe({
      next: (response) => {
        this.isJoining = false;
        this.hostUsername = response.hostUsername;

        // If game already exists, redirect to game
        if (response.gameId) {
          this.router.navigate(['/game', response.gameId, 'play']);
        } else {
          // Notify other players that we joined via WebSocket
          this.wsService.connect().subscribe({
            next: () => {
              this.wsService.sendMessage('join', { roomToken: this.roomToken });
            },
            error: (error) => {
              console.error('Failed to connect to WebSocket for join notification:', error);
            }
          });
          // Send WebSocket notification that we joined
          this.wsService.sendMessage('join', { roomToken: this.roomToken });
          // Redirect to lobby with the room token
          this.router.navigate(['/lobby'], { queryParams: { roomToken: this.roomToken } });
        }
      },
      error: (error) => {
        this.isJoining = false;
        this.joinError = error.error?.message || 'Failed to join room';
      }
    });
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}