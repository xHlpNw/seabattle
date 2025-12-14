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
    component: LobbyComponent,
    canActivate: [AuthGuard]
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
    component: ProfileComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'setup',
    component: SetupComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'game',
    component: GameComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'game/:gameId',
    component: GameComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'game/:gameId/play',
    component: GameComponent,
    canActivate: [AuthGuard]
  }
];
