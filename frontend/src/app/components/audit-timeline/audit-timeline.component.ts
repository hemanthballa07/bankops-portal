import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { AuditService } from '../../services/audit.service';
import { AuditEvent } from '../../models/audit-event.model';

@Component({
    selector: 'app-audit-timeline',
    standalone: true,
    imports: [
        CommonModule,
        MatCardModule,
        MatProgressSpinnerModule,
        MatPaginatorModule,
        MatChipsModule,
        MatIconModule
    ],
    templateUrl: './audit-timeline.component.html',
    styleUrl: './audit-timeline.component.css'
})
export class AuditTimelineComponent implements OnInit {
    auditEvents: AuditEvent[] = [];
    loading = false;
    error: string | null = null;

    // Pagination
    page = 0;
    size = 20;
    totalElements = 0;
    totalPages = 0;

    // Route params
    entityType: string = '';
    entityId: number = 0;

    constructor(
        private auditService: AuditService,
        private route: ActivatedRoute
    ) { }

    ngOnInit(): void {
        this.route.params.subscribe(params => {
            this.entityType = params['entityType'];
            this.entityId = +params['entityId'];
            this.loadAuditEvents();
        });
    }

    loadAuditEvents(): void {
        this.loading = true;
        this.error = null;

        this.auditService.getAuditTimeline(this.entityType, this.entityId, this.page, this.size)
            .subscribe({
                next: (response) => {
                    this.auditEvents = response.content;
                    this.totalElements = response.totalElements;
                    this.totalPages = response.totalPages;
                    this.loading = false;
                },
                error: (err) => {
                    this.error = err.error?.message || 'Failed to load audit timeline';
                    this.loading = false;
                }
            });
    }

    onPageChange(event: PageEvent): void {
        this.page = event.pageIndex;
        this.size = event.pageSize;
        this.loadAuditEvents();
    }

    getActionColor(action: string): string {
        switch (action) {
            case 'CREATE': return 'primary';
            case 'UPDATE': return 'accent';
            case 'STATUS_CHANGE': return 'warn';
            case 'FLAG_TOGGLE': return '';
            default: return '';
        }
    }

    formatTimestamp(timestamp: string): string {
        return new Date(timestamp).toLocaleString();
    }

    parseJsonValue(value: string): any {
        try {
            return JSON.parse(value);
        } catch {
            return value;
        }
    }

    getChangedFields(event: AuditEvent): string[] {
        const oldValue = this.parseJsonValue(event.oldValue);
        const newValue = this.parseJsonValue(event.newValue);

        if (!oldValue || !newValue) return [];

        const fields = new Set([...Object.keys(oldValue), ...Object.keys(newValue)]);
        return Array.from(fields);
    }

    getFieldChange(event: AuditEvent, field: string): { old: any, new: any } {
        const oldValue = this.parseJsonValue(event.oldValue);
        const newValue = this.parseJsonValue(event.newValue);

        return {
            old: oldValue?.[field],
            new: newValue?.[field]
        };
    }
}
