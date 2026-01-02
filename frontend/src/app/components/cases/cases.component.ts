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
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { CaseService } from '../../services/case.service';
import { CustomerService } from '../../services/customer.service';
import { SupportCase, CreateCaseRequest, UpdateCaseRequest, AddCaseNoteRequest, ResolveCaseRequest } from '../../models/case.model';
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
    MatIconModule,
    MatChipsModule,
    MatExpansionModule,
    MatDialogModule
  ],
  templateUrl: './cases.component.html',
  styleUrls: ['./cases.component.scss']
})
export class CasesComponent implements OnInit {
  cases: SupportCase[] = [];
  customers: Customer[] = [];
  displayedColumns: string[] = ['id', 'customerId', 'status', 'severity', 'summary', 'assignedTo', 'createdAt', 'actions'];
  showCreateForm: boolean = false;
  statusFilter: string = '';
  severityFilter: string = '';
  selectedCase: SupportCase | null = null;
  newNote: string = '';
  resolution: string = '';

  newCase: CreateCaseRequest = {
    customerId: 0,
    summary: '',
    severity: 'MEDIUM'
  };

  constructor(
    private caseService: CaseService,
    private customerService: CustomerService,
    private dialog: MatDialog
  ) { }

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

  assignToMe(caseItem: SupportCase): void {
    this.caseService.assignCase(caseItem.id, { assignedTo: 'admin' }).subscribe({
      next: () => this.loadCases(),
      error: (error) => {
        console.error('Error assigning case:', error);
        alert('Failed to assign case: ' + (error.error?.message || error.message));
      }
    });
  }

  selectCase(caseItem: SupportCase): void {
    this.selectedCase = caseItem;
    this.newNote = '';
    this.resolution = '';
  }

  addNote(): void {
    if (!this.selectedCase || !this.newNote.trim()) return;

    const request: AddCaseNoteRequest = { content: this.newNote };
    this.caseService.addCaseNote(this.selectedCase.id, request).subscribe({
      next: () => {
        this.newNote = '';
        this.loadCases();
        // Refresh selected case to show new note
        if (this.selectedCase) {
          const caseId = this.selectedCase.id;
          this.selectedCase = this.cases.find(c => c.id === caseId) || null;
        }
      },
      error: (error) => {
        console.error('Error adding note:', error);
        alert('Failed to add note: ' + (error.error?.message || error.message));
      }
    });
  }

  resolveCase(): void {
    if (!this.selectedCase || !this.resolution.trim()) return;

    const request: ResolveCaseRequest = { resolution: this.resolution };
    this.caseService.resolveCase(this.selectedCase.id, request).subscribe({
      next: () => {
        this.selectedCase = null;
        this.resolution = '';
        this.loadCases();
      },
      error: (error) => {
        console.error('Error resolving case:', error);
        alert('Failed to resolve case: ' + (error.error?.message || error.message));
      }
    });
  }

  closeDetails(): void {
    this.selectedCase = null;
  }
}





