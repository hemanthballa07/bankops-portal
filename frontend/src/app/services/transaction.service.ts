import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Transaction, CreateTransactionRequest, ReviewTransactionRequest, TransactionFilterRequest, PagedResponse, SpendingSummary, MonthlySpending } from '../models/transaction.model';

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

  releaseTransaction(accountId: number, transactionId: number, request?: ReviewTransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(
      `${this.apiUrl}/accounts/${accountId}/transactions/${transactionId}/release`,
      request ?? {}
    );
  }

  rejectTransaction(accountId: number, transactionId: number, request?: ReviewTransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(
      `${this.apiUrl}/accounts/${accountId}/transactions/${transactionId}/reject`,
      request ?? {}
    );
  }

  getSpendingSummary(accountId: number, startDate?: string, endDate?: string): Observable<SpendingSummary[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<SpendingSummary[]>(`${this.apiUrl}/accounts/${accountId}/transactions/spending-summary`, { params });
  }

  getHeldTransactions(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`${this.apiUrl}/transactions`, {
      params: new HttpParams().set('status', 'HELD')
    });
  }

  getHeldTransactionsPaged(page: number, size: number, sort?: string): Observable<PagedResponse<Transaction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (sort) { params = params.set('sort', sort); }
    return this.http.get<PagedResponse<Transaction>>(`${this.apiUrl}/transactions/held`, { params });
  }

  getMonthlySpending(accountId: number, startDate?: string, endDate?: string): Observable<MonthlySpending[]> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);

    return this.http.get<MonthlySpending[]>(`${this.apiUrl}/accounts/${accountId}/transactions/monthly-spending`, { params });
  }
}





