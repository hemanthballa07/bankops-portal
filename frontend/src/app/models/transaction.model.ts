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
  mlScore?: number; // Advisory ML fraud probability [0,1]; present on HELD txns.
}

export interface CreateTransactionRequest {
  type: string;
  amount: number;
  description?: string;
  category?: string;
  merchant?: string; // Optional; forwarded to Fluxa's blocked_merchant rule.
}

export interface ReviewTransactionRequest {
  actorId?: string;
  notes?: string;
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





