import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GameApi, Player } from '../../core/api/game.api';
import { RoomApi } from '../../core/api/room.api';

@Component({
  selector: 'page-home',
  templateUrl: './home.html',
  styleUrls: ['./home.scss'],
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink]
})
export class HomeComponent {

  private auth = inject(AuthService);
  private router = inject(Router);
  private gameApi = inject(GameApi);
  private roomApi = inject(RoomApi);

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
      error: () => {}
    });
  }

  goLogin() {
    this.router.navigate(['/login']);
  }

  goProfile() {
    this.router.navigate(['/profile']);
  }

  playBot() {
    if (!this.isLoggedIn) {
      this.router.navigate(['/login']);
      return;
    }

    this.gameApi.getUnfinishedBotGames().subscribe({
      next: (unfinishedGames) => {
        if (unfinishedGames.length > 0) {
          this.unfinishedGames = unfinishedGames;
          this.showUnfinishedGamesModal = true;
        } else {
          this.createNewBotGame();
        }
      },
      error: () => {
        this.createNewBotGame();
      }
    });
  }

  private createNewBotGame() {
    this.gameApi.createBotGame().subscribe({
      next: (res) => {
        this.router.navigate(['/setup'], { queryParams: { gameId: res.gameId } });
      },
      error: () => {}
    });
  }

  playOnline() {
    if (!this.isLoggedIn) {
      this.router.navigate(['/login']);
      return;
    }

    this.roomApi.createRoom().subscribe({
      next: (response) => {
        this.router.navigate(['/lobby'], { queryParams: { token: response.roomToken } });
      },
      error: () => {}
    });
  }

  continueUnfinishedGame(gameId: string) {
    this.showUnfinishedGamesModal = false;
    this.router.navigate(['/game'], { queryParams: { gameId: gameId } });
  }

  surrenderAndStartNew() {
    const surrenderPromises = this.unfinishedGames.map(game =>
      firstValueFrom(this.gameApi.surrender(game.gameId, true))
    );

    Promise.all(surrenderPromises).then(() => {
      this.showUnfinishedGamesModal = false;
      this.createNewBotGame();
    }).catch(() => {
      this.showUnfinishedGamesModal = false;
      this.createNewBotGame();
    });
  }

  cancelUnfinishedGamesModal() {
    this.showUnfinishedGamesModal = false;
  }
}
