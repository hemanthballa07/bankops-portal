import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReportsService } from '../../services/reports.service';
import { ReportSummary } from '../../models/report.model';

interface BarRow { label: string; value: number; cssClass: string; }

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './reports.component.html',
  styleUrl: './reports.component.scss',
})
export class ReportsComponent implements OnInit {
  summary: ReportSummary | null = null;
  loading = false;
  error: string | null = null;

  constructor(private reports: ReportsService) {}

  ngOnInit(): void {
    this.loading = true;
    this.reports.getSummary().subscribe({
      next: (s) => { this.summary = s; this.loading = false; },
      error: () => { this.error = 'Failed to load analytics'; this.loading = false; },
    });
  }

  toRows(map: Record<string, number> | undefined, classFn: (k: string) => string): BarRow[] {
    if (!map) return [];
    return Object.entries(map)
      .map(([label, value]) => ({ label, value, cssClass: classFn(label) }))
      .sort((a, b) => b.value - a.value);
  }

  max(rows: BarRow[]): number {
    return rows.reduce((m, r) => Math.max(m, r.value), 0);
  }

  pct(value: number, max: number): number {
    return max > 0 ? Math.round((value / max) * 100) : 0;
  }

  txClass(status: string): string {
    switch (status) {
      case 'HELD': return 'bar-warning';
      case 'REJECTED': case 'FAILED': return 'bar-error';
      case 'COMPLETED': case 'RELEASED': return 'bar-success';
      default: return 'bar-info';
    }
  }

  severityClass(sev: string): string {
    switch (sev) {
      case 'CRITICAL': case 'HIGH': return 'bar-error';
      case 'MEDIUM': return 'bar-warning';
      default: return 'bar-info';
    }
  }

  neutralClass(_: string): string { return 'bar-info'; }
}
