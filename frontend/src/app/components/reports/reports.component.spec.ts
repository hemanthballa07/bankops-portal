import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { ReportsComponent } from './reports.component';
import { ReportsService } from '../../services/reports.service';
import { ReportSummary } from '../../models/report.model';

describe('ReportsComponent', () => {
  let component: ReportsComponent;
  let fixture: ComponentFixture<ReportsComponent>;
  let service: jasmine.SpyObj<ReportsService>;

  const mockSummary: ReportSummary = {
    transactionsByStatus: { COMPLETED: 5, HELD: 2, PENDING: 1 },
    casesByState: { NEW: 3 },
    casesBySeverity: { HIGH: 2, LOW: 1 },
    mlRiskByBand: { LOW: 1, MED: 1, HIGH: 1 },
    caseKpis: {
      openCases: 3, unassignedCases: 1, slaAtRiskCases: 0,
      highSeverityCases: 2, unassignedHighSeverity: 1,
    },
    totalTransactions: 8,
    totalCases: 3,
  };

  beforeEach(async () => {
    service = jasmine.createSpyObj('ReportsService', ['getSummary']);
    service.getSummary.and.returnValue(of(mockSummary));

    await TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [provideNoopAnimations(), { provide: ReportsService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the summary on init', () => {
    fixture.detectChanges(); // triggers ngOnInit
    expect(service.getSummary).toHaveBeenCalled();
    expect(component.summary).toEqual(mockSummary);
    expect(component.loading).toBeFalse();
  });

  it('sets an error when the summary fails to load', () => {
    service.getSummary.and.returnValue(throwError(() => new Error('boom')));
    fixture.detectChanges();
    expect(component.error).toBe('Failed to load analytics');
    expect(component.loading).toBeFalse();
  });

  it('toRows sorts descending by value and applies the class fn', () => {
    const rows = component.toRows({ A: 1, B: 5, C: 3 }, () => 'bar-info');
    expect(rows.map((r) => r.label)).toEqual(['B', 'C', 'A']);
    expect(rows[0].cssClass).toBe('bar-info');
  });

  it('toRows returns [] for an undefined map', () => {
    expect(component.toRows(undefined, () => 'x')).toEqual([]);
  });

  it('pct computes a percentage and guards divide-by-zero', () => {
    expect(component.pct(5, 10)).toBe(50);
    expect(component.pct(5, 0)).toBe(0);
  });

  it('txClass maps transaction statuses to semantic bars', () => {
    expect(component.txClass('HELD')).toBe('bar-warning');
    expect(component.txClass('REJECTED')).toBe('bar-error');
    expect(component.txClass('COMPLETED')).toBe('bar-success');
    expect(component.txClass('PENDING')).toBe('bar-info');
  });

  it('severityClass maps case severities', () => {
    expect(component.severityClass('CRITICAL')).toBe('bar-error');
    expect(component.severityClass('MEDIUM')).toBe('bar-warning');
    expect(component.severityClass('LOW')).toBe('bar-info');
  });

  it('mlBandClass maps band names to bar classes', () => {
    expect(component.mlBandClass('LOW')).toBe('bar-success');
    expect(component.mlBandClass('MED')).toBe('bar-warning');
    expect(component.mlBandClass('HIGH')).toBe('bar-error');
  });
});
