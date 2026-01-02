export interface AuditEvent {
    id: string;
    entityType: 'ACCOUNT' | 'CASE' | 'TRANSACTION';
    entityId: number;
    action: 'CREATE' | 'UPDATE' | 'STATUS_CHANGE' | 'FLAG_TOGGLE';
    oldValue: any;
    newValue: any;
    performedBy: string;
    timestamp: string;
}

export interface PagedAuditResponse {
    content: AuditEvent[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
