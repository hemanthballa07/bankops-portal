import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MlRiskBandService } from './ml-risk-band.service';
import { environment } from '../../environments/environment';

describe('MlRiskBandService', () => {
  const base = `${environment.apiUrl}/ml-risk-bands`;
  let service: MlRiskBandService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MlRiskBandService],
    });
    service = TestBed.inject(MlRiskBandService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getBands GETs /ml-risk-bands', () => {
    service.getBands().subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush({ medThreshold: 0.4, highThreshold: 0.7, updatedAt: null });
  });

  it('update PUTs /ml-risk-bands with both thresholds', () => {
    service.update(0.35, 0.65).subscribe();
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ medThreshold: 0.35, highThreshold: 0.65 });
    req.flush({ medThreshold: 0.35, highThreshold: 0.65, updatedAt: null });
  });
});
