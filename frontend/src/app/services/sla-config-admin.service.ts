import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SlaConfig } from '../models/sla-config.model';

@Injectable({ providedIn: 'root' })
export class SlaConfigAdminService {
  private readonly apiUrl = `${environment.apiUrl}/admin/sla-config`;

  constructor(private http: HttpClient) {}

  list(): Observable<SlaConfig[]> {
    return this.http.get<SlaConfig[]>(this.apiUrl);
  }

  update(priority: string, durationSeconds: number): Observable<SlaConfig> {
    return this.http.put<SlaConfig>(`${this.apiUrl}/${priority}`, { durationSeconds });
  }
}
