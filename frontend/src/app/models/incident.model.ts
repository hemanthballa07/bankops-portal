import { Transaction } from './transaction.model';
import { SupportCase } from './case.model';

export interface LogEvent {
  id: number;
  correlationId: string;
  level: string;
  message: string;
  contextJson?: string;
  createdAt: string;
}

export interface IncidentResponse {
  correlationId: string;
  transaction?: Transaction;
  case_?: SupportCase;
  logEvents: LogEvent[];
}

export interface IncidentSummary {
  id: string;
  timestamp: string;
  service: string;
  endpoint: string;
  status: number;
  latency: number;
  correlationId: string;
  actor: string;
  severity: 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
  statusText: string;
}

export interface IncidentKpis {
  openIncidents: number;
  failedRequests: number;
  p95Latency: number;
  activeCases: number;
}

export interface IncidentFilters {
  correlationId: string;
  timeRange: '15m' | '1h' | '24h' | '7d' | 'custom';
  status: 'all' | '2xx' | '4xx' | '5xx';
  service: string;
  severity: 'all' | 'SEV1' | 'SEV2' | 'SEV3' | 'SEV4';
}

