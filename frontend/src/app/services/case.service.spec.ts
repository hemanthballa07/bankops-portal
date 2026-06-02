import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { CaseService } from './case.service';
import { environment } from '../../environments/environment';

describe('CaseService', () => {
  const base = `${environment.apiUrl}/cases`;
  let service: CaseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CaseService],
    });
    service = TestBed.inject(CaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('createCase POSTs to /cases', () => {
    const body = { customerId: 1, summary: 'fraud', severity: 'HIGH' };
    service.createCase(body).subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1 });
  });

  it('getCases GETs /cases with no params by default', () => {
    service.getCases().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('getCases passes status and severity filters when given', () => {
    service.getCases('OPEN', 'HIGH').subscribe();
    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('severity')).toBe('HIGH');
    req.flush([]);
  });

  it('updateCaseStatus PATCHes /cases/{id}', () => {
    service.updateCaseStatus(7, { status: 'CLOSED' }).subscribe();
    const req = httpMock.expectOne(`${base}/7`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'CLOSED' });
    req.flush({ id: 7 });
  });

  it('assignCase PUTs /cases/{id}/assign', () => {
    service.assignCase(7, { assignedTo: 'admin' }).subscribe();
    const req = httpMock.expectOne(`${base}/7/assign`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ assignedTo: 'admin' });
    req.flush({ id: 7 });
  });

  it('addCaseNote POSTs /cases/{id}/notes', () => {
    service.addCaseNote(7, { content: 'note body' }).subscribe();
    const req = httpMock.expectOne(`${base}/7/notes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ content: 'note body' });
    req.flush({ id: 1, author: 'admin', content: 'note body', createdAt: '2026-06-01T00:00:00Z' });
  });

  it('linkTransaction POSTs /cases/{id}/transactions/{txId}', () => {
    service.linkTransaction(7, 99).subscribe();
    const req = httpMock.expectOne(`${base}/7/transactions/99`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ id: 7 });
  });

  it('resolveCase PUTs /cases/{id}/resolve', () => {
    service.resolveCase(7, { resolution: 'legit' }).subscribe();
    const req = httpMock.expectOne(`${base}/7/resolve`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ resolution: 'legit' });
    req.flush({ id: 7, status: 'RESOLVED' });
  });

  it('getKpis GETs /cases/kpis', () => {
    service.getKpis().subscribe();
    const req = httpMock.expectOne(`${base}/kpis`);
    expect(req.request.method).toBe('GET');
    req.flush({
      openCases: 0, unassignedCases: 0, slaAtRiskCases: 0,
      highSeverityCases: 0, unassignedHighSeverity: 0,
    });
  });
});
