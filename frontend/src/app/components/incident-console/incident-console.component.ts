import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { IncidentService } from '../../services/incident.service';
import { IncidentResponse, LogEvent } from '../../models/incident.model';

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
    MatListModule,
    MatChipsModule
  ],
  templateUrl: './incident-console.component.html',
  styleUrls: ['./incident-console.component.scss']
})
export class IncidentConsoleComponent implements OnInit {
  correlationId: string = '';
  incident?: IncidentResponse;
  loading: boolean = false;
  error?: string;

  constructor(
    private route: ActivatedRoute,
    private incidentService: IncidentService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('correlationId');
    if (id) {
      this.correlationId = id;
      this.searchIncident();
    }
  }

  searchIncident(): void {
    if (!this.correlationId.trim()) {
      this.error = 'Please enter a correlation ID';
      return;
    }

    this.loading = true;
    this.error = undefined;
    this.incident = undefined;

    this.incidentService.getIncidentByCorrelationId(this.correlationId.trim()).subscribe({
      next: (data) => {
        this.incident = data;
        this.loading = false;
      },
      error: (error) => {
        this.error = error.error?.message || 'Incident not found';
        this.loading = false;
      }
    });
  }

  getLogLevelClass(level: string): string {
    switch (level.toLowerCase()) {
      case 'error':
        return 'log-error';
      case 'warn':
        return 'log-warn';
      case 'info':
        return 'log-info';
      case 'debug':
        return 'log-debug';
      default:
        return '';
    }
  }
}

