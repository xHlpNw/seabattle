import { Routes } from '@angular/router';
import { LoginComponent } from './pages/auth/login/login';
import { RegisterComponent } from './pages/auth/register/register';
import { HomeComponent } from './pages/home/home';
import { ProfileComponent } from './pages/profile/profile';
import { SetupComponent } from './pages/setup/setup';
import { GameComponent } from './pages/game/game';
import { LobbyComponent } from './pages/lobby/lobby';
import { AuthGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent
  },
  {
    path: 'lobby',
    component: LobbyComponent
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'register',
    component: RegisterComponent
  },
  {
    path: 'profile',
    component: ProfileComponent
  },
  {
    path: 'setup',
    component: SetupComponent
  },
  {
    path: 'game',
    component: GameComponent
  },
  {
    path: 'game/:gameId',
    component: GameComponent
  },
  {
    path: 'game/:gameId/play',
    component: GameComponent
  }
];
