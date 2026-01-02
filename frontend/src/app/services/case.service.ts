import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  SupportCase,
  CreateCaseRequest,
  UpdateCaseRequest,
  AssignCaseRequest,
  AddCaseNoteRequest,
  ResolveCaseRequest,
  CaseNote
} from '../models/case.model';

@Injectable({
  providedIn: 'root'
})
export class CaseService {
  private apiUrl = `${environment.apiUrl}/cases`;

  constructor(private http: HttpClient) { }

  createCase(request: CreateCaseRequest): Observable<SupportCase> {
    return this.http.post<SupportCase>(this.apiUrl, request);
  }

  getCases(status?: string, severity?: string): Observable<SupportCase[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    if (severity) {
      params = params.set('severity', severity);
    }
    return this.http.get<SupportCase[]>(this.apiUrl, { params });
  }

  updateCaseStatus(id: number, request: UpdateCaseRequest): Observable<SupportCase> {
    return this.http.patch<SupportCase>(`${this.apiUrl}/${id}`, request);
  }

  assignCase(id: number, request: AssignCaseRequest): Observable<SupportCase> {
    return this.http.put<SupportCase>(`${this.apiUrl}/${id}/assign`, request);
  }

  addCaseNote(id: number, request: AddCaseNoteRequest): Observable<CaseNote> {
    return this.http.post<CaseNote>(`${this.apiUrl}/${id}/notes`, request);
  }

  linkTransaction(id: number, transactionId: number): Observable<SupportCase> {
    return this.http.post<SupportCase>(`${this.apiUrl}/${id}/transactions/${transactionId}`, {});
  }

  resolveCase(id: number, request: ResolveCaseRequest): Observable<SupportCase> {
    return this.http.put<SupportCase>(`${this.apiUrl}/${id}/resolve`, request);
  }
}





