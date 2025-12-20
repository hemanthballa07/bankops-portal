import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { CustomerService } from '../../services/customer.service';
import { AccountService } from '../../services/account.service';
import { Customer, CreateCustomerRequest } from '../../models/customer.model';
import { Account, CreateAccountRequest } from '../../models/account.model';

@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule
  ],
  templateUrl: './customer-detail.component.html',
  styleUrls: ['./customer-detail.component.scss']
})
export class CustomerDetailComponent implements OnInit {
  customer?: Customer;
  accounts: Account[] = [];
  displayedColumns: string[] = ['id', 'type', 'status', 'balance', 'overdraftEnabled', 'actions'];
  showCreateAccountForm: boolean = false;
  
  newAccount: CreateAccountRequest = {
    type: 'CHEQUING'
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private customerService: CustomerService,
    private accountService: AccountService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCustomer(Number(id));
      this.loadAccounts(Number(id));
    }
  }

  loadCustomer(id: number): void {
    this.customerService.getCustomerById(id).subscribe({
      next: (data) => this.customer = data,
      error: (error) => console.error('Error loading customer:', error)
    });
  }

  loadAccounts(customerId: number): void {
    this.accountService.getAccountsByCustomerId(customerId).subscribe({
      next: (data) => this.accounts = data,
      error: (error) => console.error('Error loading accounts:', error)
    });
  }

  onCreateAccount(): void {
    if (!this.customer) return;
    
    this.accountService.createAccount(this.customer.id, this.newAccount).subscribe({
      next: () => {
        this.loadAccounts(this.customer!.id);
        this.showCreateAccountForm = false;
        this.newAccount = { type: 'CHEQUING' };
      },
      error: (error) => {
        console.error('Error creating account:', error);
        alert('Failed to create account: ' + (error.error?.message || error.message));
      }
    });
  }

  viewAccount(accountId: number): void {
    this.router.navigate(['/accounts', accountId]);
  }

  toggleCreateAccountForm(): void {
    this.showCreateAccountForm = !this.showCreateAccountForm;
  }
}

