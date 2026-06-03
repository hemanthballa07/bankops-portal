export interface SupportCase {
  id: number;
  customerId: number;
  accountId?: number;
  transactionId?: number;
  mlScore?: number;     // advisory ML risk of the linked txn (provenance)
  evaluatedBy?: string; // Fluxa eval/model version that scored the linked txn
  status: string;
  severity: string;
  summary: string;
  createdAt: string;
  updatedAt: string;
  assignedTo?: string;
  notes?: CaseNote[];
  relatedTransactionIds?: number[];
  resolvedAt?: string;
  resolution?: string;
}

export interface CaseNote {
  id: number;
  author: string;
  content: string;
  createdAt: string;
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

export interface AssignCaseRequest {
  assignedTo: string;
}

export interface AddCaseNoteRequest {
  content: string;
}

export interface ResolveCaseRequest {
  resolution: string;
}

export interface CaseKpi {
  openCases: number;
  unassignedCases: number;
  slaAtRiskCases: number;
  highSeverityCases: number;
  unassignedHighSeverity: number;
}





