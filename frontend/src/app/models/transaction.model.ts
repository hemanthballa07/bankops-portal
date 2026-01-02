export interface Transaction {
  id: number;
  accountId: number;
  type: string;
  amount: number;
  status: string;
  correlationId: string;
  description?: string;
  category?: string;
  createdAt: string;
}

export interface CreateTransactionRequest {
  type: string;
  amount: number;
  description?: string;
  category?: string;
}

export interface TransactionFilterRequest {
  startDate?: string; // ISO date format
  endDate?: string;
  type?: string;
  status?: string;
  searchText?: string;
  page?: number;
  size?: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SpendingSummary {
  category: string;
  totalAmount: number;
  transactionCount: number;
}

export interface MonthlySpending {
  month: string; // Format: YYYY-MM
  totalAmount: number;
}





