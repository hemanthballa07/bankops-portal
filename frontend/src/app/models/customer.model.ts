export interface Customer {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  createdAt: string;
}

export interface CreateCustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
}





