import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GameApi } from '../../core/api/game.api'

export interface Player {
  username: string;
  rating: number;
}

@Component({
  selector: 'page-home',
  templateUrl: './home.html',
  styleUrls: ['./home.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule]
})
export class HomeComponent {

  private auth = inject(AuthService);
  private router = inject(Router);
  private gameApi = inject(GameApi);

  isLoggedIn = false;
  topPlayers: Player[] = [];

  showUnfinishedGamesModal: boolean = false;
  unfinishedGames: { gameId: string; createdAt: string }[] = [];

  ngOnInit() {
    this.auth.isLoggedIn$.subscribe(v => this.isLoggedIn = v);

    this.gameApi.getTopPlayers(5).subscribe({
      next: (players) => {
        this.topPlayers = players;
      },
      error: (err) => {
        console.error('Ошибка при получении топ игроков:', err);
      }
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }

  goProfile() {
    this.router.navigate(['/profile']);
  }

  playBot() {
    console.log('Токен:', this.auth.getToken());
    if (!this.isLoggedIn) {
      this.router.navigate(['/login']);
      return;
    }

    // First check for unfinished bot games
    this.gameApi.getUnfinishedBotGames().subscribe({
      next: (unfinishedGames) => {
        if (unfinishedGames.length > 0) {
          // Show modal to choose what to do with unfinished games
          this.unfinishedGames = unfinishedGames;
          this.showUnfinishedGamesModal = true;
        } else {
          // No unfinished games, create new one
          this.createNewBotGame();
        }
      },
      error: (err) => {
        console.error('❌ Ошибка при проверке незавершенных игр:', err);
        // If we can't check, proceed with creating new game
        this.createNewBotGame();
      }
    });
  }

  private createNewBotGame() {
    this.gameApi.createBotGame().subscribe({
      next: (res) => {
        console.log('✅ Игра с ботом создана:', res);
        this.router.navigate(['/setup'], { queryParams: { gameId: res.gameId } });
      },
      error: (err) => {
        console.error('❌ Ошибка при создании игры с ботом:', err);
      }
    });
  }

  playOnline() {
    this.router.navigate(['/lobby']);
  }

  continueUnfinishedGame(gameId: string) {
    this.showUnfinishedGamesModal = false;
    // Navigate to the game page to continue
    this.router.navigate(['/game'], { queryParams: { gameId: gameId } });
  }

  surrenderAndStartNew() {
    // Surrender all unfinished games and start a new one
    const surrenderPromises = this.unfinishedGames.map(game =>
      firstValueFrom(this.gameApi.surrender(game.gameId))
    );

    Promise.all(surrenderPromises).then(() => {
      this.showUnfinishedGamesModal = false;
      this.createNewBotGame();
    }).catch(err => {
      console.error('❌ Ошибка при сдаче игр:', err);
      // Still try to create new game
      this.showUnfinishedGamesModal = false;
      this.createNewBotGame();
    });
  }

  cancelUnfinishedGamesModal() {
    this.showUnfinishedGamesModal = false;
  }
}
