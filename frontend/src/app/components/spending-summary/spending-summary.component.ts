import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { SpendingSummary } from '../../models/transaction.model';
import { TransactionService } from '../../services/transaction.service';

@Component({
    selector: 'app-spending-summary',
    standalone: true,
    imports: [
        CommonModule,
        MatCardModule,
        MatTableModule,
        MatProgressBarModule
    ],
    template: `
    <mat-card class="spending-summary-card">
      <mat-card-header>
        <mat-card-title>Spending Summary</mat-card-title>
        <mat-card-subtitle>Breakdown by category (withdrawals only)</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        <div *ngIf="loading" class="loading">Loading summary...</div>
        
        <div *ngIf="!loading && summaryData.length === 0" class="empty-state">
          <p>No spending data available for the selected period</p>
        </div>
        
        <table mat-table [dataSource]="summaryData" class="summary-table" *ngIf="!loading && summaryData.length > 0">
          <ng-container matColumnDef="category">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let item">
              <span class="category-name">{{ formatCategory(item.category) }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef>Total Amount</th>
            <td mat-cell *matCellDef="let item">
              <strong>\${{ item.totalAmount | number:'1.2-2' }}</strong>
            </td>
          </ng-container>

          <ng-container matColumnDef="count">
            <th mat-header-cell *matHeaderCellDef>Transactions</th>
            <td mat-cell *matCellDef="let item">{{ item.transactionCount }}</td>
          </ng-container>

          <ng-container matColumnDef="percentage">
            <th mat-header-cell *matHeaderCellDef>% of Total</th>
            <td mat-cell *matCellDef="let item">
              <div class="percentage-cell">
                <span>{{ getPercentage(item.totalAmount) }}%</span>
                <mat-progress-bar 
                  mode="determinate" 
                  [value]="getPercentage(item.totalAmount)"
                  class="percentage-bar">
                </mat-progress-bar>
              </div>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <div class="total-row" *ngIf="!loading && summaryData.length > 0">
          <strong>Total Spending:</strong> \${{ totalSpending | number:'1.2-2' }}
        </div>
      </mat-card-content>
    </mat-card>
  `,
    styles: [`
    .spending-summary-card {
      margin-bottom: 20px;
    }

    .loading, .empty-state {
      text-align: center;
      padding: 40px;
      color: #666;
    }

    .summary-table {
      width: 100%;
      margin-top: 16px;
    }

    .category-name {
      font-weight: 500;
      text-transform: capitalize;
    }

    .percentage-cell {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .percentage-bar {
      flex: 1;
      height: 8px;
      border-radius: 4px;
    }

    .total-row {
      margin-top: 20px;
      padding: 16px;
      background: #f5f5f5;
      border-radius: 4px;
      text-align: right;
      font-size: 16px;
    }
  `]
})
export class SpendingSummaryComponent implements OnChanges {
    @Input() accountId!: number;
    @Input() startDate?: string;
    @Input() endDate?: string;

    summaryData: SpendingSummary[] = [];
    displayedColumns: string[] = ['category', 'amount', 'count', 'percentage'];
    loading: boolean = false;
    totalSpending: number = 0;

    constructor(private transactionService: TransactionService) { }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['accountId'] || changes['startDate'] || changes['endDate']) {
            this.loadSummary();
        }
    }

    loadSummary(): void {
        if (!this.accountId) return;

        this.loading = true;
        this.transactionService.getSpendingSummary(this.accountId, this.startDate, this.endDate).subscribe({
            next: (data) => {
                this.summaryData = data;
                this.totalSpending = data.reduce((sum, item) => sum + item.totalAmount, 0);
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading spending summary:', error);
                this.loading = false;
            }
        });
    }

    formatCategory(category: string): string {
        return category.toLowerCase().replace(/_/g, ' ');
    }

    getPercentage(amount: number): number {
        if (this.totalSpending === 0) return 0;
        return Math.round((amount / this.totalSpending) * 100);
    }
}
