import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { CreateTransactionRequest } from '../../models/transaction.model';

@Component({
  selector: 'app-transaction-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule
  ],
  template: `
    <div class="transaction-form">
      <mat-form-field appearance="outline">
        <mat-label>Type</mat-label>
        <mat-select [(ngModel)]="transaction.type">
          <mat-option value="DEPOSIT">Deposit</mat-option>
          <mat-option value="WITHDRAWAL">Withdrawal</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Amount</mat-label>
        <input matInput type="number" [(ngModel)]="transaction.amount" 
               [min]="0.01" [step]="0.01" required>
      </mat-form-field>
      <button mat-raised-button color="primary" 
              [disabled]="!isValid()" 
              (click)="onSubmit()">
        Submit
      </button>
      <button mat-button (click)="onCancel()">Cancel</button>
    </div>
  `,
  styles: [`
    .transaction-form {
      display: flex;
      gap: 16px;
      align-items: center;
      flex-wrap: wrap;
    }
    
    mat-form-field {
      flex: 1;
      min-width: 150px;
    }
  `]
})
export class TransactionFormComponent {
  @Input() transaction: CreateTransactionRequest = {
    type: 'DEPOSIT',
    amount: 0
  };
  
  @Output() submit = new EventEmitter<CreateTransactionRequest>();
  @Output() cancel = new EventEmitter<void>();

  isValid(): boolean {
    return this.transaction.amount > 0;
  }

  onSubmit(): void {
    if (this.isValid()) {
      this.submit.emit(this.transaction);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }
}





