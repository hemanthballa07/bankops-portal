import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { TransactionFilterRequest } from '../../models/transaction.model';
import { debounceTime, Subject } from 'rxjs';

@Component({
    selector: 'app-transaction-filter',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatButtonModule,
        MatDatepickerModule,
        MatNativeDateModule,
        MatIconModule
    ],
    template: `
    <div class="filter-container">
      <h3>Filter Transactions</h3>
      
      <div class="filter-row">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Start Date</mat-label>
          <input matInput [matDatepicker]="startPicker" [(ngModel)]="filters.startDate" 
                 (ngModelChange)="onFilterChange()">
          <mat-datepicker-toggle matIconSuffix [for]="startPicker"></mat-datepicker-toggle>
          <mat-datepicker #startPicker></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>End Date</mat-label>
          <input matInput [matDatepicker]="endPicker" [(ngModel)]="filters.endDate"
                 (ngModelChange)="onFilterChange()">
          <mat-datepicker-toggle matIconSuffix [for]="endPicker"></mat-datepicker-toggle>
          <mat-datepicker #endPicker></mat-datepicker>
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Type</mat-label>
          <mat-select [(ngModel)]="filters.type" (ngModelChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            <mat-option value="DEPOSIT">Deposit</mat-option>
            <mat-option value="WITHDRAWAL">Withdrawal</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="filters.status" (ngModelChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            <mat-option value="PENDING">Pending</mat-option>
            <mat-option value="COMPLETED">Completed</mat-option>
            <mat-option value="FAILED">Failed</mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <div class="search-row">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Search</mat-label>
          <input matInput [(ngModel)]="searchText" 
                 (ngModelChange)="onSearchChange($event)"
                 placeholder="Search by description or correlation ID">
          <mat-icon matPrefix>search</mat-icon>
        </mat-form-field>

        <button mat-raised-button color="warn" (click)="clearFilters()">
          <mat-icon>clear</mat-icon>
          Clear Filters
        </button>
      </div>
    </div>
  `,
    styles: [`
    .filter-container {
      background: #f5f5f5;
      padding: 16px;
      border-radius: 8px;
      margin-bottom: 20px;
    }

    h3 {
      margin: 0 0 16px 0;
      color: #333;
    }

    .filter-row {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 16px;
    }

    .filter-field {
      flex: 1;
      min-width: 200px;
    }

    .search-row {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .search-field {
      flex: 1;
    }

    button {
      height: 56px;
    }
  `]
})
export class TransactionFilterComponent {
    @Output() filterChange = new EventEmitter<TransactionFilterRequest>();

    filters: any = {
        startDate: null,
        endDate: null,
        type: null,
        status: null
    };

    searchText: string = '';
    private searchSubject = new Subject<string>();

    constructor() {
        // Debounce search input
        this.searchSubject.pipe(
            debounceTime(300)
        ).subscribe(searchText => {
            this.emitFilters(searchText);
        });
    }

    onFilterChange(): void {
        this.emitFilters(this.searchText);
    }

    onSearchChange(searchText: string): void {
        this.searchSubject.next(searchText);
    }

    clearFilters(): void {
        this.filters = {
            startDate: null,
            endDate: null,
            type: null,
            status: null
        };
        this.searchText = '';
        this.emitFilters('');
    }

    private emitFilters(searchText: string): void {
        const filterRequest: TransactionFilterRequest = {
            startDate: this.filters.startDate ? this.formatDate(this.filters.startDate) : undefined,
            endDate: this.filters.endDate ? this.formatDate(this.filters.endDate) : undefined,
            type: this.filters.type || undefined,
            status: this.filters.status || undefined,
            searchText: searchText || undefined,
            page: 0 // Reset to first page on filter change
        };

        this.filterChange.emit(filterRequest);
    }

    private formatDate(date: Date): string {
        return date.toISOString().split('T')[0];
    }
}
