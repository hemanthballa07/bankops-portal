import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MonthlySpending } from '../../models/transaction.model';
import { TransactionService } from '../../services/transaction.service';

@Component({
    selector: 'app-monthly-chart',
    standalone: true,
    imports: [CommonModule, MatCardModule, MatListModule],
    template: `
    <mat-card class="monthly-card">
      <mat-card-header>
        <mat-card-title>Monthly Spending</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div *ngIf="loading" class="loading">Loading...</div>
        <div *ngIf="!loading && monthlyData.length === 0" class="empty-state">
          <p>No monthly data available</p>
        </div>
        <mat-list *ngIf="!loading && monthlyData.length > 0">
          <mat-list-item *ngFor="let item of monthlyData">
            <span class="month">{{ formatMonth(item.month) }}</span>
            <span class="amount">\${{ item.totalAmount | number:'1.2-2' }}</span>
          </mat-list-item>
        </mat-list>
      </mat-card-content>
    </mat-card>
  `,
    styles: [`
    .monthly-card {
      margin-bottom: 20px;
    }

    .loading, .empty-state {
      text-align: center;
      padding: 20px;
      color: #666;
    }

    mat-list-item {
      display: flex;
      justify-content: space-between;
      padding: 12px 16px;
      border-bottom: 1px solid #eee;
    }

    .month {
      font-weight: 500;
    }

    .amount {
      color: #3f51b5;
      font-weight: 600;
    }
  `]
})
export class MonthlyChartComponent implements OnChanges {
    @Input() accountId!: number;
    @Input() startDate?: string;
    @Input() endDate?: string;

    monthlyData: MonthlySpending[] = [];
    loading: boolean = false;

    constructor(private transactionService: TransactionService) { }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['accountId'] || changes['startDate'] || changes['endDate']) {
            this.loadData();
        }
    }

    loadData(): void {
        if (!this.accountId) return;

        this.loading = true;
        this.transactionService.getMonthlySpending(this.accountId, this.startDate, this.endDate).subscribe({
            next: (data) => {
                this.monthlyData = data;
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading monthly spending:', error);
                this.loading = false;
            }
        });
    }

    formatMonth(month: string): string {
        const [year, monthNum] = month.split('-');
        const date = new Date(parseInt(year), parseInt(monthNum) - 1);
        return date.toLocaleDateString('en-US', { year: 'numeric', month: 'long' });
    }
}
