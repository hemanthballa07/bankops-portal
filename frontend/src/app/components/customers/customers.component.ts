import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { CustomerService } from '../../services/customer.service';
import { Customer, CreateCustomerRequest } from '../../models/customer.model';

@Component({
  selector: 'app-customers',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    MatIconModule,
    MatDialogModule,
    MatCardModule
  ],
  templateUrl: './customers.component.html',
  styleUrls: ['./customers.component.scss']
})
export class CustomersComponent implements OnInit {
  customers: Customer[] = [];
  displayedColumns: string[] = ['id', 'firstName', 'lastName', 'email', 'phone', 'actions'];
  searchQuery: string = '';
  showCreateForm: boolean = false;
  
  newCustomer: CreateCustomerRequest = {
    firstName: '',
    lastName: '',
    email: '',
    phone: ''
  };

  constructor(private customerService: CustomerService) {}

  ngOnInit(): void {
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.customerService.searchCustomers(this.searchQuery).subscribe({
      next: (data) => this.customers = data,
      error: (error) => console.error('Error loading customers:', error)
    });
  }

  onSearch(): void {
    this.loadCustomers();
  }

  onCreateCustomer(): void {
    this.customerService.createCustomer(this.newCustomer).subscribe({
      next: () => {
        this.loadCustomers();
        this.showCreateForm = false;
        this.newCustomer = { firstName: '', lastName: '', email: '', phone: '' };
      },
      error: (error) => {
        console.error('Error creating customer:', error);
        alert('Failed to create customer: ' + (error.error?.message || error.message));
      }
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
  }
}





