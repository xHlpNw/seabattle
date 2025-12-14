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
    this.isCreatingLobby = true;
    // TODO: Implement online game creation
    // For now, just show the lobby interface
    console.log('Creating online game with lobby code:', this.lobbyCode);
  }

  backToHome() {
    this.router.navigate(['/']);
  }
}



