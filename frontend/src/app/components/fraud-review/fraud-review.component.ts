import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { TransactionService } from '../../services/transaction.service';
import { Transaction } from '../../models/transaction.model';
import { MlRiskBandService } from '../../services/ml-risk-band.service';

interface HeldRow extends Transaction {
  selected: boolean;
  actioning: boolean;
}

@Component({
  selector: 'app-fraud-review',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSnackBarModule,
  ],
  templateUrl: './fraud-review.component.html',
  styleUrls: ['./fraud-review.component.scss']
})
export class FraudReviewComponent implements OnInit {
  rows: HeldRow[] = [];
  loading = true;
  batchActioning = false;
  sortDir: 'desc' | 'asc' | null = null;
  pageIndex = 0;
  pageSize = 20;
  totalElements = 0;
  bands = { medThreshold: 0.4, highThreshold: 0.7 };

  constructor(
    private txService: TransactionService,
    private snack: MatSnackBar,
    private bandService: MlRiskBandService,
  ) {}

  ngOnInit(): void {
    this.load();
    this.bandService.getBands().subscribe({
      next: (b) => (this.bands = b),
      error: () => {/* keep defaults */},
    });
  }

  load(): void {
    this.loading = true;
    const sort = this.sortDir === 'desc' ? 'mlScore,desc' : this.sortDir === 'asc' ? 'mlScore,asc' : undefined;
    this.txService.getHeldTransactionsPaged(this.pageIndex, this.pageSize, sort).subscribe({
      next: (resp) => {
        this.rows = resp.content.map(tx => ({ ...tx, selected: false, actioning: false }));
        this.totalElements = resp.totalElements;
        this.loading = false;
      },
      error: () => {
        this.snack.open('Failed to load fraud holds', 'Dismiss', { duration: 4000 });
        this.loading = false;
      }
    });
  }

  onPageChange(e: { pageIndex: number; pageSize: number }): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  get selectedRows(): HeldRow[] {
    return this.rows.filter(r => r.selected);
  }

  get allSelected(): boolean {
    return this.rows.length > 0 && this.rows.every(r => r.selected);
  }

  get someSelected(): boolean {
    return this.rows.some(r => r.selected) && !this.allSelected;
  }

  toggleAll(checked: boolean): void {
    this.rows.forEach(r => r.selected = checked);
  }

  release(row: HeldRow): void {
    row.actioning = true;
    this.txService.releaseTransaction(row.accountId, row.id).subscribe({
      next: () => {
        this.rows = this.rows.filter(r => r.id !== row.id);
        this.totalElements = Math.max(0, this.totalElements - 1);
        this.snack.open(`Transaction #${row.id} released`, '', { duration: 3000 });
      },
      error: () => {
        row.actioning = false;
        this.snack.open('Release failed', 'Dismiss', { duration: 4000 });
      }
    });
  }

  reject(row: HeldRow): void {
    row.actioning = true;
    this.txService.rejectTransaction(row.accountId, row.id).subscribe({
      next: () => {
        this.rows = this.rows.filter(r => r.id !== row.id);
        this.totalElements = Math.max(0, this.totalElements - 1);
        this.snack.open(`Transaction #${row.id} rejected`, '', { duration: 3000 });
      },
      error: () => {
        row.actioning = false;
        this.snack.open('Reject failed', 'Dismiss', { duration: 4000 });
      }
    });
  }

  batchRelease(): void {
    const targets = [...this.selectedRows];
    if (!targets.length) return;
    this.batchActioning = true;
    let done = 0;
    targets.forEach(row => {
      this.txService.releaseTransaction(row.accountId, row.id).subscribe({
        next: () => {
          this.rows = this.rows.filter(r => r.id !== row.id);
          this.totalElements = Math.max(0, this.totalElements - 1);
          if (++done === targets.length) {
            this.batchActioning = false;
            this.snack.open(`${done} transaction(s) released`, '', { duration: 3000 });
          }
        },
        error: () => {
          if (++done === targets.length) {
            this.batchActioning = false;
            this.snack.open('Some releases failed', 'Dismiss', { duration: 4000 });
          }
        }
      });
    });
  }

  batchReject(): void {
    const targets = [...this.selectedRows];
    if (!targets.length) return;
    this.batchActioning = true;
    let done = 0;
    targets.forEach(row => {
      this.txService.rejectTransaction(row.accountId, row.id).subscribe({
        next: () => {
          this.rows = this.rows.filter(r => r.id !== row.id);
          this.totalElements = Math.max(0, this.totalElements - 1);
          if (++done === targets.length) {
            this.batchActioning = false;
            this.snack.open(`${done} transaction(s) rejected`, '', { duration: 3000 });
          }
        },
        error: () => {
          if (++done === targets.length) {
            this.batchActioning = false;
            this.snack.open('Some rejects failed', 'Dismiss', { duration: 4000 });
          }
        }
      });
    });
  }

  formatAmount(amount: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount);
  }

  mlBand(score: number): 'low' | 'med' | 'high' {
    if (score >= this.bands.highThreshold) return 'high';
    if (score >= this.bands.medThreshold) return 'med';
    return 'low';
  }

  mlPercent(score: number): string {
    return `${Math.round(score * 100)}%`;
  }

  bandLabel(score: number): string {
    const band = this.mlBand(score);
    return band === 'high' ? 'High' : band === 'med' ? 'Med' : 'Low';
  }

  chipTooltip(row: HeldRow): string {
    const base = 'Advisory ML fraud probability — not used for the hold decision';
    return row.evaluatedBy ? `${base} (scored by ${row.evaluatedBy})` : base;
  }

  get sortAria(): 'ascending' | 'descending' | 'none' {
    return this.sortDir === 'desc' ? 'descending' : this.sortDir === 'asc' ? 'ascending' : 'none';
  }

  sortByMlRisk(): void {
    this.sortDir = this.sortDir === null ? 'desc' : this.sortDir === 'desc' ? 'asc' : null;
    this.pageIndex = 0;
    this.load();
  }

  timeAgo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    return `${Math.floor(hrs / 24)}d ago`;
  }
}
