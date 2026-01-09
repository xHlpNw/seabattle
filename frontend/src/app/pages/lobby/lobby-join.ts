import { Component, inject, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RoomApi } from '../../core/api/room.api';

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
          this.router.navigate(['/setup'], { queryParams: { gameId: response.gameId } });
        } else {
          // Successfully joined, redirect to lobby with the room token
          this.router.navigate(['/lobby'], { queryParams: { token: this.roomToken } });
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