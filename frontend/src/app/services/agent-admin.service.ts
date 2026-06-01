import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Agent, CreateAgentRequest, UpdateAgentRequest } from '../models/agent.model';

@Injectable({ providedIn: 'root' })
export class AgentAdminService {
  private readonly apiUrl = `${environment.apiUrl}/agents`;

  constructor(private http: HttpClient) {}

  list(): Observable<Agent[]> { return this.http.get<Agent[]>(this.apiUrl); }
  create(req: CreateAgentRequest): Observable<Agent> { return this.http.post<Agent>(this.apiUrl, req); }
  update(id: number, req: UpdateAgentRequest): Observable<Agent> { return this.http.put<Agent>(`${this.apiUrl}/${id}`, req); }
  setActive(id: number, active: boolean): Observable<Agent> { return this.http.patch<Agent>(`${this.apiUrl}/${id}/active`, { active }); }
}
