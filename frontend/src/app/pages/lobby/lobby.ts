import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { GameApi } from '../../core/api/game.api';

@Component({
  selector: 'page-lobby',
  templateUrl: './lobby.html',
  styleUrls: ['./lobby.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class LobbyComponent implements OnInit {

  private router = inject(Router);
  private gameApi = inject(GameApi);

  lobbyCode: string = '';
  isCreatingLobby: boolean = false;
  inviteLink: string = '';

  ngOnInit() {
    // Generate a lobby code when component initializes
    this.generateLobbyCode();
  }

  generateLobbyCode() {
    // Generate a random 6-character code
    const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    this.lobbyCode = '';
    for (let i = 0; i < 6; i++) {
      this.lobbyCode += characters.charAt(Math.floor(Math.random() * characters.length));
    }
    this.inviteLink = `${window.location.origin}/lobby/join/${this.lobbyCode}`;
  }

  copyInviteLink() {
    navigator.clipboard.writeText(this.inviteLink).then(() => {
      alert('Invite link copied to clipboard!');
    }).catch(err => {
      console.error('Failed to copy link:', err);
      // Fallback for older browsers
      const textArea = document.createElement('textarea');
      textArea.value = this.inviteLink;
      document.body.appendChild(textArea);
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
      alert('Invite link copied to clipboard!');
    });
  }

  createOnlineGame() {
    this.isCreatingLobby = true;
    // TODO: Implement online game creation
    // For now, just show the lobby interface
    console.log('Creating online game with lobby code:', this.lobbyCode);
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}

