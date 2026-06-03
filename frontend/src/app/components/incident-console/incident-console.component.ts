import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTabsModule } from '@angular/material/tabs';
import { IncidentService } from '../../services/incident.service';
import { IncidentResponse, LogEvent, IncidentSummary, IncidentKpis, IncidentFilters } from '../../models/incident.model';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-incident-console',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSidenavModule,
    MatTabsModule
  ],
  templateUrl: './incident-console.component.html',
  styleUrls: ['./incident-console.component.scss']
})
export class IncidentConsoleComponent implements OnInit {
  // Search and filters
  filters: IncidentFilters = {
    correlationId: '',
    timeRange: '24h',
    status: 'all',
    service: 'all',
    severity: 'all'
  };

  // KPIs
  kpis: IncidentKpis = {
    openIncidents: 0,
    failedRequests: 0,
    p95Latency: 0,
    activeCases: 0
  };

  // Results table
  incidents: IncidentSummary[] = [];
  displayedColumns: string[] = ['timestamp', 'service', 'endpoint', 'status', 'latency', 'correlationId', 'actor'];

  // Pagination
  pageSize = 20;
  pageIndex = 0;
  totalElements = 0;

  // Detail drawer
  selectedIncident?: IncidentSummary;
  incidentDetail?: IncidentResponse;
  drawerOpen = false;

  // States
  loading = false;
  error?: string;

  // Service options
  services = ['all', 'transaction-service', 'account-service', 'case-service', 'logging-service'];

  constructor(
    private route: ActivatedRoute,
    private incidentService: IncidentService
  ) { }

  ngOnInit(): void {
    // Load initial data
    this.loadKpis();
    this.loadIncidents();

    // Check for correlation ID in route
    const id = this.route.snapshot.paramMap.get('correlationId');
    if (id) {
      this.filters.correlationId = id;
      this.searchIncidents();
    }
  }

  loadKpis(): void {
    // Mock KPIs for now - replace with real API call
    this.kpis = {
      openIncidents: 12,
      failedRequests: 47,
      p95Latency: 245,
      activeCases: 8
    };
  }

  loadIncidents(): void {
    // The incident-list API is not yet wired. Show demo rows only outside production
    // so mock/random data never ships to a production build.
    this.incidents = environment.production ? [] : this.generateMockIncidents();
    this.totalElements = this.incidents.length;
  }

  searchIncidents(): void {
    if (this.filters.correlationId.trim()) {
      this.loading = true;
      this.error = undefined;

      this.incidentService.getIncidentByCorrelationId(this.filters.correlationId.trim()).subscribe({
        next: (data) => {
          // Convert to IncidentSummary format
          const summary: IncidentSummary = {
            id: data.correlationId,
            timestamp: data.logEvents[0]?.createdAt || new Date().toISOString(),
            service: 'transaction-service',
            endpoint: '/api/accounts/*/transactions',
            status: data.transaction ? 200 : 500,
            latency: Math.random() * 500,
            correlationId: data.correlationId,
            actor: 'user',
            severity: data.transaction ? 'SEV4' : 'SEV2',
            statusText: data.transaction ? 'COMPLETED' : 'FAILED'
          };
          this.incidents = [summary];
          this.totalElements = 1;
          this.loading = false;
        },
        error: (error) => {
          this.error = error.error?.message || 'Incident not found';
          this.loading = false;
        }
      });
    } else {
      this.loadIncidents();
    }
  }

  onFilterChange(): void {
    this.searchIncidents();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    // In real implementation, fetch new page from API
  }

  viewIncidentDetail(incident: IncidentSummary): void {
    this.selectedIncident = incident;
    this.drawerOpen = true;

    // Load full incident details
    this.incidentService.getIncidentByCorrelationId(incident.correlationId).subscribe({
      next: (data) => {
        this.incidentDetail = data;
      },
      error: (error) => {
        console.error('Failed to load incident details:', error);
      }
    });
  }

  closeDrawer(): void {
    this.drawerOpen = false;
    this.selectedIncident = undefined;
    this.incidentDetail = undefined;
  }

  getStatusClass(status: number): string {
    if (status >= 200 && status < 300) return 'status-success';
    if (status >= 400 && status < 500) return 'status-warning';
    if (status >= 500) return 'status-error';
    return '';
  }

  getSeverityColor(severity: string): string {
    switch (severity) {
      case 'SEV1': return 'warn';
      case 'SEV2': return 'accent';
      case 'SEV3': return 'primary';
      case 'SEV4': return '';
      default: return '';
    }
  }

  getLogLevelClass(level: string): string {
    switch (level.toLowerCase()) {
      case 'error': return 'log-error';
      case 'warn': return 'log-warn';
      case 'info': return 'log-info';
      case 'debug': return 'log-debug';
      default: return '';
    }
  }

  private generateMockIncidents(): IncidentSummary[] {
    const services = ['transaction-service', 'account-service', 'case-service'];
    const endpoints = ['/api/accounts/*/transactions', '/api/accounts/*', '/api/cases', '/api/customers/*'];
    const statuses = [200, 200, 200, 400, 500];
    const severities: Array<'SEV1' | 'SEV2' | 'SEV3' | 'SEV4'> = ['SEV4', 'SEV4', 'SEV3', 'SEV2', 'SEV1'];

    return Array.from({ length: 50 }, (_, i) => ({
      id: `incident-${i}`,
      timestamp: new Date(Date.now() - Math.random() * 86400000).toISOString(),
      service: services[Math.floor(Math.random() * services.length)],
      endpoint: endpoints[Math.floor(Math.random() * endpoints.length)],
      status: statuses[Math.floor(Math.random() * statuses.length)],
      latency: Math.floor(Math.random() * 1000),
      correlationId: `corr-${Math.random().toString(36).substr(2, 9)}`,
      actor: `user-${Math.floor(Math.random() * 100)}`,
      severity: severities[Math.floor(Math.random() * severities.length)],
      statusText: statuses[Math.floor(Math.random() * statuses.length)] >= 400 ? 'FAILED' : 'COMPLETED'
    }));
  }
}
