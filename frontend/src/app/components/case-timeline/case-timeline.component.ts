import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HttpClient } from '@angular/common/http';
import { CaseTimelineEventDto, CaseSnapshotDto, EventType } from '../../models/timeline.model';

@Component({
    selector: 'app-case-timeline',
    templateUrl: './case-timeline.component.html',
    styleUrls: ['./case-timeline.component.scss']
})
export class CaseTimelineComponent {
    caseId: number;
    timeline: CaseTimelineEventDto[] = [];
    filteredTimeline: CaseTimelineEventDto[] = [];
    loading = false;
    error: string | null = null;

    // Filters
    selectedEventType: string = 'ALL';
    eventTypes = ['ALL', 'STATE_CHANGE', 'SLA_CHANGE', 'ASSIGNMENT'];

    // Pagination
    page = 0;
    pageSize = 50;
    hasMore = true;

    // Replay
    replaySnapshot: CaseSnapshotDto | null = null;
    replayLoading = false;
    expandedEventId: number | null = null;

    constructor(
        @Inject(MAT_DIALOG_DATA) public data: { caseId: number },
        private dialogRef: MatDialogRef<CaseTimelineComponent>,
        private http: HttpClient
    ) {
        this.caseId = data.caseId;
        this.loadTimeline();
    }

    loadTimeline() {
        this.loading = true;
        this.error = null;

        let url = `/api/cases/${this.caseId}/timeline?page=${this.page}&size=${this.pageSize}`;

        if (this.selectedEventType !== 'ALL') {
            url += `&eventType=${this.selectedEventType}`;
        }

        this.http.get<CaseTimelineEventDto[]>(url).subscribe({
            next: (events) => {
                if (this.page === 0) {
                    this.timeline = events;
                } else {
                    this.timeline = [...this.timeline, ...events];
                }
                this.filteredTimeline = this.timeline;
                this.hasMore = events.length === this.pageSize;
                this.loading = false;
            },
            error: (err) => {
                this.error = 'Failed to load timeline: ' + err.message;
                this.loading = false;
            }
        });
    }

    filterByEventType() {
        this.page = 0;
        this.timeline = [];
        this.loadTimeline();
    }

    loadMore() {
        this.page++;
        this.loadTimeline();
    }

    replayAt(event: CaseTimelineEventDto) {
        this.replayLoading = true;
        this.replaySnapshot = null;

        const url = `/api/cases/${this.caseId}/replay?at=${encodeURIComponent(event.timestamp)}`;

        this.http.get<CaseSnapshotDto>(url).subscribe({
            next: (snapshot) => {
                this.replaySnapshot = snapshot;
                this.replayLoading = false;
            },
            error: (err) => {
                this.error = 'Failed to replay: ' + err.message;
                this.replayLoading = false;
            }
        });
    }

    closeReplay() {
        this.replaySnapshot = null;
    }

    toggleDetails(eventId: number) {
        this.expandedEventId = this.expandedEventId === eventId ? null : eventId;
    }

    getEventTypeBadgeClass(eventType: EventType): string {
        switch (eventType) {
            case EventType.STATE_CHANGE:
                return 'badge-state';
            case EventType.SLA_CHANGE:
                return 'badge-sla';
            case EventType.ASSIGNMENT:
                return 'badge-assignment';
            default:
                return 'badge-default';
        }
    }

    formatTimestamp(timestamp: string): string {
        return new Date(timestamp).toLocaleString();
    }

    formatRelativeTime(timestamp: string): string {
        const now = new Date();
        const eventTime = new Date(timestamp);
        const diffMs = now.getTime() - eventTime.getTime();
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 60) return `${diffMins}m ago`;
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        const diffDays = Math.floor(diffHours / 24);
        return `${diffDays}d ago`;
    }

    close() {
        this.dialogRef.close();
    }
}
