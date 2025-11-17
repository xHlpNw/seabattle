import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs'; // <-- импортируем
import { GameApi } from '../../core/api/game.api';

interface Ship {
  size: number;
  placed: boolean;
}

@Component({
  selector: 'page-setup',
  templateUrl: './setup.html',
  styleUrls: ['./setup.scss'],
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule]
})
export class SetupComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private gameApi = inject(GameApi);

  gameId: string | null = null;

  grid: number[][] = [];
  ships: Ship[] = [
    { size: 4, placed: false },
    { size: 3, placed: false },
    { size: 3, placed: false },
    { size: 2, placed: false },
    { size: 2, placed: false },
    { size: 2, placed: false },
    { size: 1, placed: false },
    { size: 1, placed: false },
    { size: 1, placed: false },
    { size: 1, placed: false }
  ];

  selectedShip: Ship | null = null;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.gameId = params['gameId'] || null;
    });

    this.grid = this.createEmptyGrid();
  }

  createEmptyGrid(): number[][] {
    return Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  selectShip(ship: Ship) {
    this.selectedShip = ship;
  }

  placeShip(x: number, y: number) {
    if (!this.selectedShip) return;

    const size = this.selectedShip.size;
    if (y + size > 10) return;

    for (let i = 0; i < size; i++) {
      this.grid[x][y + i] = 1;
    }

    this.selectedShip.placed = true;
    this.selectedShip = null;
  }

  async autoPlaceShips() {
    if (!this.gameId) return;

    try {
      const response = await firstValueFrom(this.gameApi.placeShipsAuto(this.gameId));
      if (response && Array.isArray(response.grid)) {
        this.grid = response.grid;
      } else {
        console.error('Неверный формат ответа сервера:', response);
        return;
      }

      this.ships.forEach(ship => ship.placed = true);

      if (response.message) {
        console.log(response.message);
      }
    } catch (err) {
      console.error('Ошибка автоматической расстановки:', err);
    }
  }

  async ready() {
    if (!this.gameId) return;

    try {
      await firstValueFrom(this.gameApi.placeShips(this.gameId, this.grid));
      console.log('Доска успешно сохранена');

      const board = await firstValueFrom(this.gameApi.getBoard(this.gameId));
      this.grid = board?.grid || this.createEmptyGrid();

      this.router.navigate(['/game', this.gameId, 'play']); // Переход только после сохранения
    } catch (err) {
      console.error('Ошибка сохранения доски:', err);
    }
  }

}
