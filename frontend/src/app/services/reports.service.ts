import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReportSummary } from '../models/report.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly apiUrl = `${environment.apiUrl}/reports`;

  constructor(private http: HttpClient) {}

  getSummary(): Observable<ReportSummary> {
    return this.http.get<ReportSummary>(`${this.apiUrl}/summary`);
  }
}
