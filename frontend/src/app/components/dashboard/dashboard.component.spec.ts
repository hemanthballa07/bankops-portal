import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';
import { CaseService } from '../../services/case.service';
import { TransactionService } from '../../services/transaction.service';
import { CaseKpi, SupportCase } from '../../models/case.model';
import { Transaction } from '../../models/transaction.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let caseService: jasmine.SpyObj<CaseService>;
  let txService: jasmine.SpyObj<TransactionService>;

  const kpis: CaseKpi = {
    openCases: 4,
    unassignedCases: 2,
    slaAtRiskCases: 1,
    highSeverityCases: 3,
    unassignedHighSeverity: 1,
  };

  const tx = (id: number): Transaction => ({
    id,
    accountId: 1,
    type: 'DEPOSIT',
    amount: 1000 + id,
    status: 'HELD',
    correlationId: `corr-${id}`,
    createdAt: new Date(Date.now() - id * 60 * 1000).toISOString(),
  });

  const openCase = (id: number): SupportCase => ({
    id,
    customerId: 1,
    status: 'OPEN',
    severity: 'HIGH',
    summary: `case ${id}`,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  });

  beforeEach(async () => {
    caseService = jasmine.createSpyObj('CaseService', ['getKpis', 'getCases']);
    txService = jasmine.createSpyObj('TransactionService', ['getHeldTransactions']);

    caseService.getKpis.and.returnValue(of(kpis));
    caseService.getCases.and.returnValue(of([openCase(1)]));
    txService.getHeldTransactions.and.returnValue(of([tx(1), tx(2)]));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: CaseService, useValue: caseService },
        { provide: TransactionService, useValue: txService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads kpis, held transactions, and open cases on init', () => {
    component.ngOnInit();
    expect(caseService.getKpis).toHaveBeenCalled();
    expect(txService.getHeldTransactions).toHaveBeenCalled();
    expect(caseService.getCases).toHaveBeenCalledWith('OPEN');
    expect(component.kpis).toEqual(kpis);
    expect(component.heldTransactions.length).toBe(2);
    expect(component.recentCases.length).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('caps the held feed and case feed at five items', () => {
    txService.getHeldTransactions.and.returnValue(of([1, 2, 3, 4, 5, 6, 7].map(tx)));
    caseService.getCases.and.returnValue(of([1, 2, 3, 4, 5, 6].map(openCase)));
    component.ngOnInit();
    expect(component.heldTransactions.length).toBe(5);
    expect(component.recentCases.length).toBe(5);
  });

  it('falls back to null kpis when the kpi call fails', () => {
    caseService.getKpis.and.returnValue(throwError(() => new Error('boom')));
    component.ngOnInit();
    expect(component.kpis).toBeNull();
    expect(component.heldTransactions.length).toBe(2); // other feeds unaffected
    expect(component.loading).toBeFalse();
  });

  it('falls back to an empty held feed when that call fails', () => {
    txService.getHeldTransactions.and.returnValue(throwError(() => new Error('boom')));
    component.ngOnInit();
    expect(component.heldTransactions).toEqual([]);
    expect(component.loading).toBeFalse();
  });

  it('falls back to an empty case feed when that call fails', () => {
    caseService.getCases.and.returnValue(throwError(() => new Error('boom')));
    component.ngOnInit();
    expect(component.recentCases).toEqual([]);
    expect(component.loading).toBeFalse();
  });

  it('heldCount reflects the held feed length', () => {
    component.ngOnInit();
    expect(component.heldCount).toBe(2);
  });

  it('formatAmount renders USD currency', () => {
    expect(component.formatAmount(2500)).toBe('$2,500.00');
  });

  it('timeAgo renders minutes, hours, and days', () => {
    expect(component.timeAgo(new Date(Date.now() - 5 * 60 * 1000).toISOString())).toBe('5m ago');
    expect(component.timeAgo(new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString())).toBe('3h ago');
    expect(component.timeAgo(new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString())).toBe('2d ago');
  });
});
