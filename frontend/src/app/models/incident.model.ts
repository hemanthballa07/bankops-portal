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

