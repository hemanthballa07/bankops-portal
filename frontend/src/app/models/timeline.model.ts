export enum EventType {
    STATE_CHANGE = 'STATE_CHANGE',
    SLA_CHANGE = 'SLA_CHANGE',
    ASSIGNMENT = 'ASSIGNMENT'
}

export interface CaseTimelineEventDto {
    id: number;
    timestamp: string;
    eventType: EventType;
    summary: string;
    details: { [key: string]: any };
    actor: string;
    correlationId: string;
}

export interface CaseSnapshotDto {
    snapshotAt: string;
    state: string;
    assigneeName?: string;
    assigneeId?: number;
    slaStatus?: string;
    slaDueAt?: string;
    severity?: string;
    createdAt?: string;
    additionalFields?: { [key: string]: any };
}
