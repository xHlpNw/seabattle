import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { GameApi } from '../../core/api/game.api';

interface Ship {
  size: number;
  placed: boolean;
  cells?: { x: number, y: number }[];
  autoPlaced?: boolean;
}

interface ShipDTO {
  size: number;
  cells: { x: number, y: number }[];
}

interface AutoPlaceResponse {
  grid: number[][];
  ships?: ShipDTO[];
  message?: string;
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
  hoverCells: { x: number, y: number; valid: boolean }[] = [];
  orientation: 'horizontal' | 'vertical' = 'horizontal';
  autoPlacedDone: boolean = false;

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
    if (this.autoPlacedDone) return;
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
    const temp: { x: number, y: number; valid: boolean }[] = [];

    for (let i = 0; i < size; i++) {
      const x = this.orientation === 'horizontal' ? rowIndex : rowIndex + i;
      const y = this.orientation === 'horizontal' ? colIndex + i : colIndex;
      if (x >= 10 || y >= 10) {
        temp.push({ x, y, valid: false });
      } else {
        temp.push({ x, y, valid: this.canPlaceCell(x, y) });
      }
    }

    this.hoverCells = temp;
  }

  isHoverCell(i: number, j: number): boolean {
    return this.hoverCells.some(c => c.x === i && c.y === j);
  }

  isHoverValid(i: number, j: number): boolean {
    const cell = this.hoverCells.find(c => c.x === i && c.y === j);
    return cell ? cell.valid : true;
  }

  placeShip(x: number, y: number) {
    if (!this.selectedShip || this.autoPlacedDone) return;

    // проверяем все клетки
    if (this.hoverCells.some(c => !c.valid)) return;

    const cells: { x: number, y: number }[] = [];
    this.hoverCells.forEach(c => {
      this.grid[c.x][c.y] = 1;
      cells.push({ x: c.x, y: c.y });
    });

    this.selectedShip.placed = true;
    this.selectedShip.cells = cells;
    this.selectedShip = null;
    this.hoverCells = [];
  }

  removeShip(x: number, y: number) {
    const ship = this.ships.find(s => s.cells?.some(c => c.x === x && c.y === y) && s.autoPlaced);
    if (!ship) return;

    ship.cells?.forEach(c => this.grid[c.x][c.y] = 0);
    ship.placed = false;
    ship.cells = [];
    ship.autoPlaced = false;
    this.autoPlacedDone = false;
  }

  canPlaceCell(x: number, y: number): boolean {
    for (let i = x - 1; i <= x + 1; i++) {
      for (let j = y - 1; j <= y + 1; j++) {
        if (i >= 0 && i < 10 && j >= 0 && j < 10) {
          if (this.grid[i][j] === 1) return false;
        }
      }
    }
    return true;
  }

  onRightClick(event: MouseEvent) {
    event.preventDefault();
    if (this.selectedShip) this.toggleOrientation();
  }

  onCellClick(x: number, y: number, event: MouseEvent) {
    if (event.shiftKey) this.removeShip(x, y);
    else this.placeShip(x, y);
  }

  async autoPlaceShips() {
    if (!this.gameId) return;

    try {
      const response: AutoPlaceResponse = await firstValueFrom(this.gameApi.placeShipsAuto(this.gameId));

      if (response && Array.isArray(response.grid)) this.grid = response.grid;

      if (response.ships && response.ships.length === this.ships.length) {
        this.ships.forEach((ship, i) => {
          ship.cells = response.ships![i].cells;
          ship.placed = true;
          ship.autoPlaced = true;
        });
        this.autoPlacedDone = true;
      }

      if (response.message) console.log(response.message);
    } catch (err) {
      console.error('Ошибка авторасстановки:', err);
    }
  }

  async ready() {
    if (!this.gameId || !this.allShipsPlaced) return;

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
