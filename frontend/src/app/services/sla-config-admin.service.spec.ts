import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SlaConfigAdminService } from './sla-config-admin.service';
import { environment } from '../../environments/environment';

describe('SlaConfigAdminService', () => {
  const base = `${environment.apiUrl}/admin/sla-config`;
  let service: SlaConfigAdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), SlaConfigAdminService],
    });
    service = TestBed.inject(SlaConfigAdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list GETs /admin/sla-config', () => {
    service.list().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('update PUTs /admin/sla-config/{priority} with durationSeconds', () => {
    service.update('P1', 3600).subscribe();
    const req = httpMock.expectOne(`${base}/P1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ durationSeconds: 3600 });
    req.flush({ priority: 'P1', durationSeconds: 3600, updatedAt: null });
  });
});
