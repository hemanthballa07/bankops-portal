import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';

@Component({
    selector: 'app-login',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatProgressSpinnerModule
    ],
    template: `
    <div class="login-container">
      <mat-card class="login-card">
        <mat-card-header>
          <mat-card-title>BankOps Portal Login</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="error" class="error-msg">{{ error }}</div>
          
          <form (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Username</mat-label>
              <input matInput [(ngModel)]="username" name="username" required [disabled]="loading">
            </mat-form-field>
            
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput type="password" [(ngModel)]="password" name="password" required [disabled]="loading">
            </mat-form-field>
            
            <div class="actions">
              <button mat-raised-button color="primary" type="submit" [disabled]="loading || !username || !password">
                <mat-spinner diameter="20" *ngIf="loading" class="spinner"></mat-spinner>
                <span *ngIf="!loading">Sign In</span>
              </button>
            </div>
          </form>
        </mat-card-content>
        <mat-card-footer class="footer">
          <p>Default: user / password</p>
          <p>Support: support / password</p>
        </mat-card-footer>
      </mat-card>
    </div>
  `,
    styles: [`
    .login-container {
      display: flex; justify-content: center; align-items: center;
      height: 100vh; background: #f5f5f5;
    }
    .login-card { width: 100%; max-width: 400px; padding: 20px; }
    .full-width { width: 100%; margin-bottom: 10px; }
    .actions { display: flex; justify-content: flex-end; margin-top: 20px; }
    .footer { padding: 16px; color: #777; font-size: 12px; text-align: center; }
    .error-msg { color: red; background: #ffebee; padding: 10px; border-radius: 4px; margin-bottom: 16px; }
    .spinner { display: inline-block; margin-right: 8px; vertical-align: middle; }
  `]
})
export class LoginComponent {
    username = '';
    password = '';
    loading = false;
    error = '';

    constructor(private authService: AuthService, private router: Router) { }

    onSubmit() {
        this.loading = true;
        this.error = '';

        this.authService.login(this.username, this.password).subscribe({
            next: () => {
                this.loading = false;
                this.router.navigate(['/']);
            },
            error: (err) => {
                this.loading = false;
                console.error(err);
                this.error = 'Invalid credentials or server error';
            }
        });
    }
}
