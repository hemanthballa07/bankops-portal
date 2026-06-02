import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { TransactionService } from './transaction.service';
import { environment } from '../../environments/environment';

describe('TransactionService', () => {
  const base = environment.apiUrl;
  let service: TransactionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TransactionService],
    });
    service = TestBed.inject(TransactionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createTransaction POSTs the request to the account transactions endpoint', () => {
    const body = { type: 'DEPOSIT', amount: 100, merchant: 'Amazon Marketplace' };
    service.createTransaction(1, body).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/1/transactions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1 });
  });

  it('getHeldTransactions GETs /transactions with the HELD status param', () => {
    service.getHeldTransactions().subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === `${base}/transactions` && r.params.get('status') === 'HELD',
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('releaseTransaction POSTs an empty body when no request is given', () => {
    service.releaseTransaction(1, 42).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/1/transactions/42/release`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ id: 42, status: 'RELEASED' });
  });

  it('releaseTransaction forwards a provided review request', () => {
    service.releaseTransaction(1, 42, { actorId: 'admin', notes: 'legit' }).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/1/transactions/42/release`);
    expect(req.request.body).toEqual({ actorId: 'admin', notes: 'legit' });
    req.flush({ id: 42 });
  });

  it('rejectTransaction POSTs to the reject endpoint', () => {
    service.rejectTransaction(1, 42).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/1/transactions/42/reject`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ id: 42, status: 'REJECTED' });
  });

  it('getTransactionsByAccountId applies the provided filters as query params', () => {
    service
      .getTransactionsByAccountId(1, { status: 'HELD', type: 'DEPOSIT', page: 0, size: 20 })
      .subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/accounts/1/transactions`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('HELD');
    expect(req.request.params.get('type')).toBe('DEPOSIT');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('getSpendingSummary GETs the spending-summary endpoint with date params', () => {
    service.getSpendingSummary(1, '2026-01-01', '2026-02-01').subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/accounts/1/transactions/spending-summary`);
    expect(req.request.params.get('startDate')).toBe('2026-01-01');
    expect(req.request.params.get('endDate')).toBe('2026-02-01');
    req.flush([]);
  });

  it('getMonthlySpending GETs the monthly-spending endpoint', () => {
    service.getMonthlySpending(1).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/1/transactions/monthly-spending`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
