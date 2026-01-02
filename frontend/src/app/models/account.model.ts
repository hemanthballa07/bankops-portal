export interface Account {
  id: number;
  customerId: number;
  type: string;
  status: string;
  balance: number;
  overdraftEnabled: boolean;
  createdAt: string;
}

export interface CreateAccountRequest {
  type: string;
}

export interface UpdateAccountRequest {
  status?: string;
  overdraftEnabled?: boolean;
}





