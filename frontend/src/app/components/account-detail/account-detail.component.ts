import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AccountService } from '../../services/account.service';
import { TransactionService } from '../../services/transaction.service';
import { Account } from '../../models/account.model';
import { Transaction, CreateTransactionRequest, TransactionFilterRequest } from '../../models/transaction.model';
import { TransactionFilterComponent } from '../transaction-filter/transaction-filter.component';
import { SpendingSummaryComponent } from '../spending-summary/spending-summary.component';
import { MonthlyChartComponent } from '../monthly-chart/monthly-chart.component';

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    TransactionFilterComponent,
    SpendingSummaryComponent,
    MonthlyChartComponent
  ],
  templateUrl: './account-detail.component.html',
  styleUrls: ['./account-detail.component.scss']
})
export class AccountDetailComponent implements OnInit {
  account?: Account;
  transactions: Transaction[] = [];
  displayedColumns: string[] = ['id', 'type', 'amount', 'status', 'description', 'correlationId', 'createdAt'];
  showTransactionForm: boolean = false;
  loading: boolean = false;

  // Pagination
  totalElements: number = 0;
  pageSize: number = 20;
  pageIndex: number = 0;

  // Filters
  currentFilters?: TransactionFilterRequest;

  newTransaction: CreateTransactionRequest = {
    type: 'DEPOSIT',
    amount: 0,
    description: '',
    category: 'OTHER'
  };

  // Track dates for spending summary
  filterStartDate?: string;
  filterEndDate?: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private accountService: AccountService,
    private transactionService: TransactionService
  ) { }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadAccount(Number(id));
      this.loadTransactions(Number(id));
    }
  }

  loadAccount(id: number): void {
    this.accountService.getAccountById(id).subscribe({
      next: (data) => this.account = data,
      error: (error) => console.error('Error loading account:', error)
    });
  }

  loadTransactions(accountId: number, filters?: TransactionFilterRequest): void {
    this.loading = true;
    const requestFilters = {
      ...filters,
      page: filters?.page ?? this.pageIndex,
      size: filters?.size ?? this.pageSize
    };

    this.transactionService.getTransactionsByAccountId(accountId, requestFilters).subscribe({
      next: (response) => {
        this.transactions = response.content;
        this.totalElements = response.totalElements;
        this.pageIndex = response.page;
        this.pageSize = response.size;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading transactions:', error);
        this.loading = false;
      }
    });
  }

  onFilterChange(filters: TransactionFilterRequest): void {
    if (!this.account) return;
    this.currentFilters = filters;
    this.filterStartDate = filters.startDate;
    this.filterEndDate = filters.endDate;
    this.pageIndex = 0; // Reset to first page
    this.loadTransactions(this.account.id, filters);
  }

  onPageChange(event: PageEvent): void {
    if (!this.account) return;
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;

    const filters = {
      ...this.currentFilters,
      page: this.pageIndex,
      size: this.pageSize
    };

    this.loadTransactions(this.account.id, filters);
  }

  onCreateTransaction(): void {
    if (!this.account) return;

    // Validate amount
    if (this.newTransaction.amount <= 0) {
      alert('Amount must be greater than 0');
      return;
    }

    this.transactionService.createTransaction(this.account.id, this.newTransaction).subscribe({
      next: (transaction) => {
        this.loadAccount(this.account!.id);
        this.loadTransactions(this.account!.id, this.currentFilters);
        this.showTransactionForm = false;
        this.newTransaction = { type: 'DEPOSIT', amount: 0, description: '', category: 'OTHER' };
        alert(`Transaction created! Correlation ID: ${transaction.correlationId}`);
      },
      error: (error) => {
        console.error('Error creating transaction:', error);
        alert('Failed to create transaction: ' + (error.error?.message || error.message));
      }
    });
  }

  toggleTransactionForm(): void {
    this.showTransactionForm = !this.showTransactionForm;
  }

  viewIncident(correlationId: string): void {
    this.router.navigate(['/incidents', correlationId]);
  }
}





