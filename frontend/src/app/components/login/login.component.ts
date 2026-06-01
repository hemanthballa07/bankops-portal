import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="login-bg">
    <div class="login-card">
      <div class="brand">
        <span class="brand-icon material-icons">account_balance</span>
        <div class="brand-text"><span class="brand-name">BankOps</span><span class="brand-tag">OPS PORTAL</span></div>
      </div>
      <h1 class="title">Sign in</h1>
      <p class="subtitle">Authorized banking-operations personnel only</p>
      <div *ngIf="error" class="error-msg">{{ error }}</div>
      <form (ngSubmit)="onSubmit()">
        <label class="field"><span>Username</span>
          <input [(ngModel)]="username" name="username" required [disabled]="loading" autocomplete="username">
        </label>
        <label class="field"><span>Password</span>
          <input type="password" [(ngModel)]="password" name="password" required [disabled]="loading" autocomplete="current-password">
        </label>
        <button class="signin" type="submit" [disabled]="loading || !username || !password">
          <span *ngIf="!loading">Sign In</span><span *ngIf="loading">Signing in…</span>
        </button>
      </form>
      <div class="hint">support / password &nbsp;·&nbsp; admin / password &nbsp;·&nbsp; user / password</div>
    </div>
  </div>
  `,
  styles: [`
  .login-bg { display:flex; justify-content:center; align-items:center; min-height:100vh; background:#0F172A; }
  .login-card { width:100%; max-width:380px; padding:32px; background:#1E293B; border:1px solid rgba(255,255,255,0.08); border-radius:12px; box-shadow:0 10px 40px rgba(0,0,0,0.4); }
  .brand { display:flex; align-items:center; gap:10px; margin-bottom:24px; }
  .brand-icon { color:#3B82F6; font-size:28px; }
  .brand-name { color:#F8FAFC; font-weight:700; font-size:18px; margin-right:8px; }
  .brand-tag { color:#94A3B8; font-size:11px; letter-spacing:1px; }
  .title { color:#F8FAFC; font-size:22px; margin:0 0 4px; }
  .subtitle { color:#94A3B8; font-size:13px; margin:0 0 20px; }
  .field { display:block; margin-bottom:14px; }
  .field span { display:block; color:#CBD5E1; font-size:12px; margin-bottom:6px; }
  .field input { width:100%; box-sizing:border-box; padding:10px 12px; background:#0F172A; color:#F8FAFC; border:1px solid rgba(255,255,255,0.12); border-radius:8px; outline:none; }
  .field input:focus { border-color:#3B82F6; }
  .signin { width:100%; margin-top:8px; padding:11px; background:#3B82F6; color:#fff; border:none; border-radius:8px; font-weight:600; cursor:pointer; }
  .signin:disabled { opacity:0.5; cursor:not-allowed; }
  .error-msg { color:#FCA5A5; background:rgba(239,68,68,0.12); padding:10px; border-radius:8px; margin-bottom:16px; font-size:13px; }
  .hint { margin-top:18px; color:#64748B; font-size:11px; text-align:center; }
  `],
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  loading = false;
  error = '';

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

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
