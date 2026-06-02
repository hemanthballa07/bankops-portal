import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { Subscription, filter, map, startWith } from 'rxjs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from './services/auth.service';
import { NotificationService } from './services/notification.service';
import { NotificationsRailComponent } from './components/notifications-rail/notifications-rail.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatButtonModule, MatIconModule, MatBadgeModule,
    MatSidenavModule, MatListModule, MatTooltipModule,
    NotificationsRailComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit, OnDestroy {
  // inject()-based fields, declared BEFORE the derived fields below. Under
  // tsconfig target ES2022 (useDefineForClassFields:true) field initializers
  // run top-to-bottom before the constructor body, so injected services must
  // come from inject() fields here — NOT from constructor params, which are
  // still undefined at field-init time.
  private auth = inject(AuthService);
  private router = inject(Router);
  private notifications = inject(NotificationService);

  title = 'BankOps Portal';
  sidebarCollapsed = false;
  railOpen = false;
  currentUser$ = this.auth.currentUser$;

  summary$ = this.notifications.summary$;
  actionableCount$ = this.notifications.summary$.pipe(
    map(s => s.counts.critical + s.counts.warning),
  );

  private pollSub?: Subscription;

  // Shell is hidden on the full-screen login route. Seed `false` so the very
  // first paint never flashes the ops chrome before the guard/NavigationEnd
  // resolves (router.url is '' at construction).
  showShell$ = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map(e => !e.urlAfterRedirects.startsWith('/login')),
    startWith(false),
  );

  ngOnInit(): void {
    this.pollSub = this.notifications.startPolling().subscribe();
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  toggleSidebar() {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  toggleRail() {
    this.railOpen = !this.railOpen;
    if (this.railOpen) {
      this.notifications.refresh();
    }
  }

  refreshNotifications() {
    this.notifications.refresh();
  }

  logout() {
    this.auth.logout();
  }
}
