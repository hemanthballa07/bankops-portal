import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AccountService } from './account.service';
import { environment } from '../../environments/environment';

describe('AccountService', () => {
  const base = environment.apiUrl;
  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AccountService],
    });
    service = TestBed.inject(AccountService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createAccount POSTs to /customers/{cid}/accounts', () => {
    service.createAccount(3, { type: 'CHEQUING' }).subscribe();
    const req = httpMock.expectOne(`${base}/customers/3/accounts`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'CHEQUING' });
    req.flush({ id: 1 });
  });

  it('getAccountsByCustomerId GETs /customers/{cid}/accounts', () => {
    service.getAccountsByCustomerId(3).subscribe();
    const req = httpMock.expectOne(`${base}/customers/3/accounts`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getAccountById GETs /accounts/{id}', () => {
    service.getAccountById(7).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7 });
  });

  it('updateAccount PATCHes /accounts/{id}', () => {
    service.updateAccount(7, { status: 'FROZEN', overdraftEnabled: true }).subscribe();
    const req = httpMock.expectOne(`${base}/accounts/7`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'FROZEN', overdraftEnabled: true });
    req.flush({ id: 7 });
  });
});
