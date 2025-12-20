export interface SupportCase {
  id: number;
  customerId: number;
  accountId?: number;
  transactionId?: number;
  status: string;
  severity: string;
  summary: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCaseRequest {
  customerId: number;
  accountId?: number;
  transactionId?: number;
  summary: string;
  severity?: string;
}

export interface UpdateCaseRequest {
  status?: string;
}

