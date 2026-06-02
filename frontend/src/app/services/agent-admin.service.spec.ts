import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AgentAdminService } from './agent-admin.service';
import { environment } from '../../environments/environment';

describe('AgentAdminService', () => {
  const base = `${environment.apiUrl}/agents`;
  let service: AgentAdminService;
  let httpMock: HttpTestingController;

  const agent = {
    id: 1, name: 'Ada', email: 'ada@bank.test', active: true,
    maxActiveCases: 10, currentActiveCount: 0, skills: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AgentAdminService],
    });
    service = TestBed.inject(AgentAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list GETs /agents', () => {
    service.list().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush([agent]);
  });

  it('create POSTs to /agents', () => {
    const body = { name: 'Grace', email: 'grace@bank.test', maxActiveCases: 8 };
    service.create(body).subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(agent);
  });

  it('update PUTs /agents/{id}', () => {
    const body = { name: 'Ada Updated', maxActiveCases: 12, active: true };
    service.update(1, body).subscribe();
    const req = httpMock.expectOne(`${base}/1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush(agent);
  });

  it('setActive PATCHes /agents/{id}/active with the active flag', () => {
    service.setActive(1, false).subscribe();
    const req = httpMock.expectOne(`${base}/1/active`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: false });
    req.flush({ ...agent, active: false });
  });
});
