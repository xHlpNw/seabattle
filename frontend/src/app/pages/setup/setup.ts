import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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

  grid: number[][] = []; // 0 = пусто, 1 = корабль
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

    // создаём пустую сетку 10x10
    this.grid = Array.from({ length: 10 }, () => Array(10).fill(0));
  }

  selectShip(ship: Ship) {
    this.selectedShip = ship;
  }

  placeShip(x: number, y: number) {
    if (!this.selectedShip) return;

    const size = this.selectedShip.size;

    // простая проверка — помещаем вправо, если хватает места
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
      // Получаем ответ с сервера
      const response = await this.gameApi.placeShipsAuto(this.gameId).toPromise();

      // Проверяем, что response существует и имеет поле grid
      if (response && 'grid' in response && Array.isArray(response.grid)) {
        this.grid = response.grid as number[][];
      } else {
        console.error('Неверный формат ответа сервера:', response);
        return;
      }

      // Помечаем все корабли как размещённые
      this.ships.forEach(ship => ship.placed = true);

      // Можно вывести сообщение, если есть
      if (response && 'message' in response) {
        console.log(response.message);
      }

    } catch (err) {
      console.error('Ошибка автоматической расстановки:', err);
    }
  }


  async ready() {
    if (!this.gameId) return;
    try {
      await this.gameApi.placeShipsAuto(this.gameId).toPromise();
      this.router.navigate(['/game'], { queryParams: { gameId: this.gameId } });
    } catch (err) {
      console.error('Ошибка размещения кораблей:', err);
    }
  }

}
