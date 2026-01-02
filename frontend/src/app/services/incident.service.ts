import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { IncidentResponse } from '../models/incident.model';

@Injectable({
  providedIn: 'root'
})
export class IncidentService {
  private apiUrl = `${environment.apiUrl}/incidents`;

  constructor(private http: HttpClient) {}

  getIncidentByCorrelationId(correlationId: string): Observable<IncidentResponse> {
    return this.http.get<IncidentResponse>(`${this.apiUrl}/${correlationId}`);
  }
}





