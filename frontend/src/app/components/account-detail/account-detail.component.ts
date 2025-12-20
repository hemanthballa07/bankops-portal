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
import { AccountService } from '../../services/account.service';
import { TransactionService } from '../../services/transaction.service';
import { Account } from '../../models/account.model';
import { Transaction, CreateTransactionRequest } from '../../models/transaction.model';

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
    MatIconModule
  ],
  templateUrl: './account-detail.component.html',
  styleUrls: ['./account-detail.component.scss']
})
export class AccountDetailComponent implements OnInit {
  account?: Account;
  transactions: Transaction[] = [];
  displayedColumns: string[] = ['id', 'type', 'amount', 'status', 'correlationId', 'createdAt'];
  showTransactionForm: boolean = false;
  
  newTransaction: CreateTransactionRequest = {
    type: 'DEPOSIT',
    amount: 0
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private accountService: AccountService,
    private transactionService: TransactionService
  ) {}

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

  loadTransactions(accountId: number): void {
    this.transactionService.getTransactionsByAccountId(accountId).subscribe({
      next: (data) => this.transactions = data,
      error: (error) => console.error('Error loading transactions:', error)
    });
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
        this.loadTransactions(this.account!.id);
        this.showTransactionForm = false;
        this.newTransaction = { type: 'DEPOSIT', amount: 0 };
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

