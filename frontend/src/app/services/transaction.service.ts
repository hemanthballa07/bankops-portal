import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Transaction, CreateTransactionRequest, TransactionFilterRequest, PagedResponse, SpendingSummary, MonthlySpending } from '../models/transaction.model';

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  createTransaction(accountId: number, request: CreateTransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.apiUrl}/accounts/${accountId}/transactions`, request);
  }

  getTransactionsByAccountId(accountId: number, filters?: TransactionFilterRequest): Observable<PagedResponse<Transaction>> {
    let params = new HttpParams();

    if (filters) {
      if (filters.startDate) params = params.set('startDate', filters.startDate);
      if (filters.endDate) params = params.set('endDate', filters.endDate);
      if (filters.type) params = params.set('type', filters.type);
      if (filters.status) params = params.set('status', filters.status);
      if (filters.searchText) params = params.set('searchText', filters.searchText);
      if (filters.page !== undefined) params = params.set('page', filters.page.toString());
      if (filters.size !== undefined) params = params.set('size', filters.size.toString());
    }

    return this.http.get<PagedResponse<Transaction>>(`${this.apiUrl}/accounts/${accountId}/transactions`, { params });
  }

  getSpendingSummary(accountId: number, startDate?: string, endDate?: string): Observable<SpendingSummary[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<SpendingSummary[]>(`${this.apiUrl}/accounts/${accountId}/transactions/spending-summary`, { params });
  }

  getMonthlySpending(accountId: number, startDate?: string, endDate?: string): Observable<MonthlySpending[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<MonthlySpending[]>(`${this.apiUrl}/accounts/${accountId}/transactions/monthly-spending`, { params });
  }
}





