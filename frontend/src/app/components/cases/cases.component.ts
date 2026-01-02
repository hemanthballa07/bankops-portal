import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatTabsModule } from '@angular/material/tabs';

import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CaseService } from '../../services/case.service';
import { CustomerService } from '../../services/customer.service';
import { SupportCase, CreateCaseRequest, UpdateCaseRequest, AddCaseNoteRequest, ResolveCaseRequest } from '../../models/case.model';
import { Customer } from '../../models/customer.model';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { CaseQueueHeaderComponent, QueueKPIs } from './case-queue-header/case-queue-header.component';
import { CaseTimelineComponent } from '../case-timeline/case-timeline.component';

interface EnhancedCase extends SupportCase {
  selected?: boolean;
  slaRemaining?: number;
  priority?: number;
  customerName?: string;
}

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
    MatDialogModule,
    MatMenuModule,
    MatCheckboxModule,
    MatSortModule,
    MatPaginatorModule,
    MatTabsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    CaseQueueHeaderComponent
  ],
  templateUrl: './cases.component.html',
  styleUrls: ['./cases.component.scss']
})
export class CasesComponent implements OnInit {
  @ViewChild(MatSort) sort!: MatSort;
  @ViewChild(MatPaginator) paginator!: MatPaginator;

  dataSource = new MatTableDataSource<EnhancedCase>([]);
  customers: Customer[] = [];
  displayedColumns: string[] = ['select', 'id', 'customer', 'status', 'severity', 'priority', 'sla', 'summary', 'assignedTo', 'quickActions'];

  showCreateForm: boolean = false;
  selectedCase: EnhancedCase | null = null;
  newNote: string = '';
  resolution: string = '';

  // KPI Data
  queueKpis: QueueKPIs = {
    openCases: 0,
    openTrend: 12,
    unassigned: 0,
    slaAtRisk: 0,
    highSeverity: 0
  };

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
    this.loadCustomers();
    this.loadCases();
  }

  loadCustomers(): void {
    this.customerService.searchCustomers().subscribe({
      next: (data) => this.customers = data,
      error: (error) => console.error('Error loading customers:', error)
    });
  }

  loadCases(): void {
    this.caseService.getCases().subscribe({
      next: (data) => {
        const enhancedData = data.map(c => this.enhanceCase(c));
        this.dataSource.data = enhancedData;
        this.dataSource.sort = this.sort;
        this.dataSource.paginator = this.paginator;
        this.updateKPIs(enhancedData);
      },
      error: (error) => console.error('Error loading cases:', error)
    });
  }

  enhanceCase(c: SupportCase): EnhancedCase {
    // Calculate SLA remaining (mock logic: 24h for High, 48h for Medium, 72h for Low)
    const created = new Date(c.createdAt).getTime();
    const now = new Date().getTime();
    const ageHrs = (now - created) / (1000 * 60 * 60);

    let slaTarget = 48;
    if (c.severity === 'HIGH') slaTarget = 24;
    if (c.severity === 'LOW') slaTarget = 72;

    const slaRemaining = Math.round(slaTarget - ageHrs);

    // Map Severity to Priority
    let priority = 2; // Medium
    if (c.severity === 'HIGH') priority = 1;
    if (c.severity === 'LOW') priority = 3;
    if (c.severity === 'CRITICAL') priority = 0;

    // Find customer name
    const customer = this.customers.find(cust => cust.id === c.customerId);

    return {
      ...c,
      slaRemaining,
      priority,
      customerName: customer ? `${customer.firstName} ${customer.lastName}` : `ID: ${c.customerId}`,
      selected: false
    };
  }

  updateKPIs(data: EnhancedCase[]): void {
    this.queueKpis = {
      openCases: data.filter(c => c.status === 'OPEN').length,
      openTrend: 5, // Mock trend
      unassigned: data.filter(c => !c.assignedTo).length,
      slaAtRisk: data.filter(c => (c.slaRemaining || 10) < 4 && c.status !== 'RESOLVED').length,
      highSeverity: data.filter(c => c.severity === 'HIGH' || c.severity === 'CRITICAL').length
    };
  }

  // Bulk Selection
  isAllSelected() {
    const numSelected = this.dataSource.data.filter(c => c.selected).length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  toggleAll(event: any) {
    const checked = event.checked;
    this.dataSource.data.forEach(c => c.selected = checked);
  }

  getSelectedCount(): number {
    return this.dataSource.data.filter(c => c.selected).length;
  }

  // Case Actions
  onCreateCase(): void {
    this.caseService.createCase(this.newCase).subscribe({
      next: () => {
        this.loadCases();
        this.showCreateForm = false;
        this.newCase = { customerId: 0, summary: '', severity: 'MEDIUM' };
      },
      error: (err) => alert('Failed to create case')
    });
  }

  updateStatus(caseId: number, status: string): void {
    this.caseService.updateCaseStatus(caseId, { status }).subscribe(() => this.loadCases());
  }

  assignToMe(caseId: number): void {
    this.caseService.assignCase(caseId, { assignedTo: 'admin' }).subscribe(() => this.loadCases());
  }

  openTimeline(caseId: number): void {
    this.dialog.open(CaseTimelineComponent, {
      width: '650px',
      maxHeight: '90vh',
      data: { caseId },
      panelClass: 'timeline-dialog'
    });
  }

  selectCase(c: EnhancedCase): void {
    this.selectedCase = c;
    this.newNote = '';
    this.resolution = '';
  }

  addNote(): void {
    if (!this.selectedCase || !this.newNote.trim()) return;
    this.caseService.addCaseNote(this.selectedCase.id, { content: this.newNote }).subscribe(() => {
      this.newNote = '';
      this.loadCases(); // Refresh to show note
      // Ideally we should just append the note locally
    });
    // Optimistic update for demo flow
    this.selectedCase.notes = [
      { id: 999, author: 'admin', content: this.newNote, createdAt: new Date().toISOString() },
      ...(this.selectedCase.notes || [])
    ];
  }

  resolveCase(): void {
    if (!this.selectedCase || !this.resolution.trim()) return;
    this.caseService.resolveCase(this.selectedCase.id, { resolution: this.resolution }).subscribe(() => {
      this.resolution = '';
      this.selectedCase = null;
      this.loadCases();
    });
  }

  closeDetails(): void {
    this.selectedCase = null;
  }

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
  }
}
