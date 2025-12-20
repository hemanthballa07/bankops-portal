import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { CaseService } from '../../services/case.service';
import { CustomerService } from '../../services/customer.service';
import { SupportCase, CreateCaseRequest, UpdateCaseRequest } from '../../models/case.model';
import { Customer } from '../../models/customer.model';

@Component({
  selector: 'app-cases',
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
  templateUrl: './cases.component.html',
  styleUrls: ['./cases.component.scss']
})
export class CasesComponent implements OnInit {
  cases: SupportCase[] = [];
  customers: Customer[] = [];
  displayedColumns: string[] = ['id', 'customerId', 'status', 'severity', 'summary', 'createdAt', 'actions'];
  showCreateForm: boolean = false;
  statusFilter: string = '';
  severityFilter: string = '';
  
  newCase: CreateCaseRequest = {
    customerId: 0,
    summary: '',
    severity: 'MEDIUM'
  };

  constructor(
    private caseService: CaseService,
    private customerService: CustomerService
  ) {}

  ngOnInit(): void {
    this.loadCases();
    this.loadCustomers();
  }

  loadCustomers(): void {
    this.customerService.searchCustomers().subscribe({
      next: (data) => this.customers = data,
      error: (error) => console.error('Error loading customers:', error)
    });
  }

  loadCases(): void {
    this.caseService.getCases(this.statusFilter || undefined, this.severityFilter || undefined).subscribe({
      next: (data) => this.cases = data,
      error: (error) => console.error('Error loading cases:', error)
    });
  }

  onFilter(): void {
    this.loadCases();
  }

  onCreateCase(): void {
    this.caseService.createCase(this.newCase).subscribe({
      next: () => {
        this.loadCases();
        this.showCreateForm = false;
        this.newCase = { customerId: 0, summary: '', severity: 'MEDIUM' };
      },
      error: (error) => {
        console.error('Error creating case:', error);
        alert('Failed to create case: ' + (error.error?.message || error.message));
      }
    });
  }

  updateCaseStatus(caseId: number, newStatus: string): void {
    const request: UpdateCaseRequest = { status: newStatus };
    this.caseService.updateCaseStatus(caseId, request).subscribe({
      next: () => this.loadCases(),
      error: (error) => {
        console.error('Error updating case:', error);
        alert('Failed to update case: ' + (error.error?.message || error.message));
      }
    });
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
  }
}

