import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatChipsModule } from '@angular/material/chips';

export interface QueueKPIs {
    openCases: number;
    openTrend: number; // + or - percentage
    unassigned: number;
    slaAtRisk: number;
    highSeverity: number;
}

export interface SavedView {
    id: string;
    name: string;
    count?: number;
}

@Component({
    selector: 'app-case-queue-header',
    standalone: true,
    imports: [
        CommonModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatMenuModule,
        MatChipsModule
    ],
    templateUrl: './case-queue-header.component.html',
    styleUrls: ['./case-queue-header.component.scss']
})
export class CaseQueueHeaderComponent {
    @Input() kpis: QueueKPIs = {
        openCases: 0,
        openTrend: 0,
        unassigned: 0,
        slaAtRisk: 0,
        highSeverity: 0
    };

    @Input() savedViews: SavedView[] = [
        { id: 'all', name: 'All Cases' },
        { id: 'my-cases', name: 'My Cases' },
        { id: 'unassigned', name: 'Unassigned' },
        { id: 'sla-risk', name: 'SLA at Risk' },
        { id: 'high-sev', name: 'High Severity' }
    ];

    @Input() activeViewId = 'all';
    @Input() selectedCount = 0;

    @Output() viewChanged = new EventEmitter<string>();
    @Output() bulkAction = new EventEmitter<string>();

    selectView(viewId: string) {
        this.activeViewId = viewId;
        this.viewChanged.emit(viewId);
    }

    onBulkAction(action: string) {
        this.bulkAction.emit(action);
    }
}
