import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditEvent, PagedAuditResponse } from '../models/audit-event.model';
import { environment } from '../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class AuditService {
    private apiUrl = `${environment.apiUrl}/audit`;

    constructor(private http: HttpClient) { }

    /**
     * Get audit timeline for a specific entity
     * @param entityType Type of entity (ACCOUNT, CASE, TRANSACTION)
     * @param entityId ID of the entity
     * @param page Page number (0-indexed)
     * @param size Page size
     */
    getAuditTimeline(
        entityType: string,
        entityId: number,
        page: number = 0,
        size: number = 20
    ): Observable<PagedAuditResponse> {
        const params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString());

        return this.http.get<PagedAuditResponse>(
            `${this.apiUrl}/${entityType}/${entityId}`,
            { params }
        );
    }
}
