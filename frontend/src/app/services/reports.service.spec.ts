import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ReportsService } from './reports.service';
import { environment } from '../../environments/environment';

describe('ReportsService', () => {
  const base = `${environment.apiUrl}/reports`;
  let service: ReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ReportsService],
    });
    service = TestBed.inject(ReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getSummary GETs /reports/summary', () => {
    service.getSummary().subscribe();
    const req = httpMock.expectOne(`${base}/summary`);
    expect(req.request.method).toBe('GET');
    req.flush({
      transactionsByStatus: { COMPLETED: 1 },
      casesByState: { NEW: 1 },
      casesBySeverity: { HIGH: 1 },
      caseKpis: {
        openCases: 1, unassignedCases: 0, slaAtRiskCases: 0,
        highSeverityCases: 1, unassignedHighSeverity: 0,
      },
      totalTransactions: 1,
      totalCases: 1,
    });
  });
});
