import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { MonthlyChartComponent } from './monthly-chart.component';
import { TransactionService } from '../../services/transaction.service';
import { MonthlySpending } from '../../models/transaction.model';

describe('MonthlyChartComponent', () => {
  let component: MonthlyChartComponent;
  let fixture: ComponentFixture<MonthlyChartComponent>;
  let txService: jasmine.SpyObj<TransactionService>;

  const rows: MonthlySpending[] = [
    { month: '2026-01', totalAmount: 100 },
    { month: '2026-02', totalAmount: 200 },
  ];

  beforeEach(async () => {
    txService = jasmine.createSpyObj('TransactionService', ['getMonthlySpending']);
    txService.getMonthlySpending.and.returnValue(of(rows));

    await TestBed.configureTestingModule({
      imports: [MonthlyChartComponent],
      providers: [provideNoopAnimations(), { provide: TransactionService, useValue: txService }],
    }).compileComponents();

    fixture = TestBed.createComponent(MonthlyChartComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnChanges reloads when an input changes', () => {
    component.accountId = 1;
    component.ngOnChanges({ accountId: { currentValue: 1, previousValue: undefined, firstChange: true, isFirstChange: () => true } });
    expect(txService.getMonthlySpending).toHaveBeenCalledWith(1, undefined, undefined);
  });

  it('loadData does nothing without an accountId', () => {
    component.accountId = 0 as unknown as number;
    component.loadData();
    expect(txService.getMonthlySpending).not.toHaveBeenCalled();
  });

  it('loadData sets the data and clears loading on success', () => {
    component.accountId = 1;
    component.loadData();
    expect(component.monthlyData.length).toBe(2);
    expect(component.loading).toBeFalse();
  });

  it('loadData logs and clears loading on error', () => {
    const errSpy = spyOn(console, 'error');
    txService.getMonthlySpending.and.returnValue(throwError(() => new Error('x')));
    component.accountId = 1;
    component.loadData();
    expect(errSpy).toHaveBeenCalled();
    expect(component.loading).toBeFalse();
  });

  it('formatMonth renders a long month + year', () => {
    expect(component.formatMonth('2026-01')).toBe('January 2026');
    expect(component.formatMonth('2026-12')).toBe('December 2026');
  });
});
