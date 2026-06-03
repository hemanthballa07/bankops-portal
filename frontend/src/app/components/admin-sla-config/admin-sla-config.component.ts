import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SlaConfigAdminService } from '../../services/sla-config-admin.service';
import { SlaConfig } from '../../models/sla-config.model';

interface SlaRow extends SlaConfig {
  hours: number;
  saving: boolean;
}

@Component({
  selector: 'app-admin-sla-config',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './admin-sla-config.component.html',
  styleUrl: './admin-sla-config.component.scss',
})
export class AdminSlaConfigComponent implements OnInit {
  rows: SlaRow[] = [];
  loading = false;
  error: string | null = null;

  constructor(private slaConfigService: SlaConfigAdminService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    this.slaConfigService.list().subscribe({
      next: (configs) => {
        this.rows = configs.map(c => ({
          ...c,
          hours: Math.round((c.durationSeconds / 3600) * 100) / 100,
          saving: false,
        }));
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load SLA config';
        this.loading = false;
      },
    });
  }

  save(row: SlaRow): void {
    if (!(row.hours > 0)) {
      this.error = 'Hours must be greater than 0';
      return;
    }
    this.error = null;
    const seconds = Math.round(row.hours * 3600);
    row.saving = true;
    this.slaConfigService.update(row.priority, seconds).subscribe({
      next: (updated) => {
        row.durationSeconds = updated.durationSeconds;
        row.updatedAt = updated.updatedAt;
        row.saving = false;
      },
      error: () => {
        this.error = `Failed to save ${row.priority}`;
        row.saving = false;
      },
    });
  }
}
