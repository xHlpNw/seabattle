import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { GameApi } from '../../core/api/game.api';

interface Ship {
  size: number;
  placed: boolean;
  cells?: { x: number, y: number }[]; // добавим клетки для каждого корабля
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
  hoverCells: { x: number, y: number }[] = [];
  orientation: 'horizontal' | 'vertical' = 'horizontal';

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
    if (ship.placed) return;
    this.selectedShip = ship;
    this.hoverCells = [];
  }

  get allShipsPlaced(): boolean {
    return this.ships.every(ship => ship.placed);
  }

  toggleOrientation() {
    this.orientation = this.orientation === 'horizontal' ? 'vertical' : 'horizontal';
    if (this.selectedShip && this.hoverCells.length > 0) {
      const firstCell = this.hoverCells[0];
      this.onMouseMove(new MouseEvent('mousemove'), firstCell.x, firstCell.y);
    }
  }

  onMouseMove(event: MouseEvent, rowIndex: number, colIndex: number) {
    if (!this.selectedShip) {
      this.hoverCells = [];
      return;
    }

    const size = this.selectedShip.size;
    const temp: { x: number, y: number }[] = [];

    if (this.orientation === 'horizontal') {
      if (colIndex + size <= 10) {
        for (let i = 0; i < size; i++) temp.push({ x: rowIndex, y: colIndex + i });
      }
    } else {
      if (rowIndex + size <= 10) {
        for (let i = 0; i < size; i++) temp.push({ x: rowIndex + i, y: colIndex });
      }
    }

    this.hoverCells = temp;
  }

  isHoverCell(i: number, j: number): boolean {
    return this.hoverCells?.some(c => c.x === i && c.y === j) ?? false;
  }

  placeShip(x: number, y: number) {
    if (!this.selectedShip) return;
    const size = this.selectedShip.size;

    if (this.orientation === 'horizontal' && y + size > 10) return;
    if (this.orientation === 'vertical' && x + size > 10) return;

    const cells: { x: number, y: number }[] = [];

    for (let i = 0; i < size; i++) {
      if (this.orientation === 'horizontal') {
        this.grid[x][y + i] = 1;
        cells.push({ x, y: y + i });
      } else {
        this.grid[x + i][y] = 1;
        cells.push({ x: x + i, y });
      }
    }

    this.selectedShip.placed = true;
    this.selectedShip.cells = cells; // сохраняем клетки
    this.selectedShip = null;
    this.hoverCells = [];
  }

  removeShip(x: number, y: number) {
    // находим корабль, которому принадлежит эта клетка
    const ship = this.ships.find(s => s.cells?.some(c => c.x === x && c.y === y));
    if (!ship) return;

    // очищаем клетки с доски
    ship.cells?.forEach(c => {
      this.grid[c.x][c.y] = 0;
    });

    ship.placed = false;
    ship.cells = [];
  }

  onRightClick(event: MouseEvent) {
    event.preventDefault();
    if (this.selectedShip) {
      this.toggleOrientation();
    }
  }

  onCellClick(x: number, y: number, event: MouseEvent) {
    if (event.shiftKey) {
      // Shift + клик = удалить корабль
      this.removeShip(x, y);
    } else {
      this.placeShip(x, y);
    }
  }

  async autoPlaceShips() {
    if (!this.gameId) return;

    try {
      const response = await firstValueFrom(this.gameApi.placeShipsAuto(this.gameId));
      if (response && Array.isArray(response.grid)) this.grid = response.grid;
      this.ships.forEach(ship => ship.placed = true);
      if (response.message) console.log(response.message);
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

      this.router.navigate(['/game', this.gameId, 'play']);
    } catch (err) {
      console.error('Ошибка сохранения доски:', err);
    }
  }
}
