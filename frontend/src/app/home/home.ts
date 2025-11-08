import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-home',
  templateUrl: './home.html',
  styleUrls: ['./home.scss'],
  standalone: true,       // <--- делаем standalone
  imports: [CommonModule] // <--- импортируем директивы вроде *ngIf
})
export class HomeComponent implements OnInit {
  user: any;

  constructor(private auth: AuthService) {}

  ngOnInit() {
    this.auth.user$.subscribe(u => this.user = u);
  }
}
