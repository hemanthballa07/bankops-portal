import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule
  ],
  template: `
    <mat-toolbar color="primary">
      <span>BankOps Portal</span>
    </mat-toolbar>
    <mat-sidenav-container>
      <mat-sidenav mode="side" opened>
        <mat-nav-list>
          <a mat-list-item routerLink="/customers" routerLinkActive="active">
            <mat-icon>people</mat-icon>
            <span>Customers</span>
          </a>
          <a mat-list-item routerLink="/cases" routerLinkActive="active">
            <mat-icon>support</mat-icon>
            <span>Cases</span>
          </a>
          <a mat-list-item routerLink="/incidents" routerLinkActive="active">
            <mat-icon>bug_report</mat-icon>
            <span>Incident Console</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content>
        <div class="content">
          <router-outlet></router-outlet>
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    mat-sidenav-container {
      height: calc(100vh - 64px);
    }
    
    mat-sidenav {
      width: 250px;
    }
    
    .content {
      padding: 20px;
    }
    
    mat-nav-list a.active {
      background-color: rgba(63, 81, 181, 0.15);
    }
    
    mat-icon {
      margin-right: 8px;
    }
  `]
})
export class AppComponent {
  title = 'BankOps Portal';
}
