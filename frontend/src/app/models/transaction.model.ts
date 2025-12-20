export interface Transaction {
  id: number;
  accountId: number;
  type: string;
  amount: number;
  status: string;
  correlationId: string;
  createdAt: string;
}

export interface CreateTransactionRequest {
  type: string;
  amount: number;
}

