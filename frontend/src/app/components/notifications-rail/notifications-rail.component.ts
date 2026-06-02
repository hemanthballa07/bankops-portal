import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  EMPTY_SUMMARY, Notification, NotificationSeverity, NotificationsSummary,
} from '../../models/notification.model';

@Component({
  selector: 'app-notifications-rail',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './notifications-rail.component.html',
  styleUrl: './notifications-rail.component.scss',
})
export class NotificationsRailComponent {
  @Input() open = false;
  @Input() summary: NotificationsSummary = EMPTY_SUMMARY;
  @Input() loading = false;
  @Input() error = false;
  @Output() closeRail = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();

  private router = inject(Router);

  get alertItems(): Notification[] {
    return this.summary.items.filter(i => i.category !== 'BACKLOG');
  }

  get backlog(): Notification | undefined {
    return this.summary.items.find(i => i.category === 'BACKLOG');
  }

  severityClass(severity: NotificationSeverity): string {
    return `sev-${severity.toLowerCase()}`;
  }

  severityIcon(severity: NotificationSeverity): string {
    switch (severity) {
      case 'CRITICAL': return 'error';
      case 'WARNING': return 'warning';
      default: return 'info';
    }
  }

  openItem(item: Notification): void {
    this.router.navigate([item.link]);
    this.closeRail.emit();
  }

  onClose(): void {
    this.closeRail.emit();
  }

  onRefresh(): void {
    this.refresh.emit();
  }
}
