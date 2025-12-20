import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Account, CreateAccountRequest, UpdateAccountRequest } from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  createAccount(customerId: number, request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.apiUrl}/customers/${customerId}/accounts`, request);
  }

  getAccountsByCustomerId(customerId: number): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiUrl}/customers/${customerId}/accounts`);
  }

  getAccountById(id: number): Observable<Account> {
    return this.http.get<Account>(`${this.apiUrl}/accounts/${id}`);
  }

  updateAccount(id: number, request: UpdateAccountRequest): Observable<Account> {
    return this.http.patch<Account>(`${this.apiUrl}/accounts/${id}`, request);
  }
}

