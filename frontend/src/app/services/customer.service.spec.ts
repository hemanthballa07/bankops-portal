import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CustomerService } from './customer.service';
import { environment } from '../../environments/environment';

describe('CustomerService', () => {
  const base = `${environment.apiUrl}/customers`;
  let service: CustomerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CustomerService],
    });
    service = TestBed.inject(CustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createCustomer POSTs to /customers', () => {
    const body = { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@bank.test', phone: '555-0100' };
    service.createCustomer(body).subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1, ...body, createdAt: '2026-06-01T00:00:00Z' });
  });

  it('searchCustomers GETs /customers with no params by default', () => {
    service.searchCustomers().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('searchCustomers passes the query param when given', () => {
    service.searchCustomers('ada').subscribe();
    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.params.get('query')).toBe('ada');
    req.flush([]);
  });

  it('getCustomerById GETs /customers/{id}', () => {
    service.getCustomerById(7).subscribe();
    const req = httpMock.expectOne(`${base}/7`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@bank.test', phone: '555-0100', createdAt: '2026-06-01T00:00:00Z' });
  });
});
