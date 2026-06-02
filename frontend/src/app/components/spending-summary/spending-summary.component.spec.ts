import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { SpendingSummaryComponent } from './spending-summary.component';
import { TransactionService } from '../../services/transaction.service';
import { SpendingSummary } from '../../models/transaction.model';

describe('SpendingSummaryComponent', () => {
  let component: SpendingSummaryComponent;
  let fixture: ComponentFixture<SpendingSummaryComponent>;
  let txService: jasmine.SpyObj<TransactionService>;

  const rows: SpendingSummary[] = [
    { category: 'FOOD_DINING', totalAmount: 150, transactionCount: 3 },
    { category: 'TRAVEL', totalAmount: 50, transactionCount: 1 },
  ];

  beforeEach(async () => {
    txService = jasmine.createSpyObj('TransactionService', ['getSpendingSummary']);
    txService.getSpendingSummary.and.returnValue(of(rows));

    await TestBed.configureTestingModule({
      imports: [SpendingSummaryComponent],
      providers: [provideNoopAnimations(), { provide: TransactionService, useValue: txService }],
    }).compileComponents();

    fixture = TestBed.createComponent(SpendingSummaryComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ngOnChanges reloads the summary when an input changes', () => {
    component.accountId = 1;
    component.ngOnChanges({ accountId: { currentValue: 1, previousValue: undefined, firstChange: true, isFirstChange: () => true } });
    expect(txService.getSpendingSummary).toHaveBeenCalledWith(1, undefined, undefined);
  });

  it('loadSummary does nothing without an accountId', () => {
    component.accountId = 0 as unknown as number;
    component.loadSummary();
    expect(txService.getSpendingSummary).not.toHaveBeenCalled();
  });

  it('loadSummary sums the total and clears loading on success', () => {
    component.accountId = 1;
    component.loadSummary();
    expect(component.summaryData.length).toBe(2);
    expect(component.totalSpending).toBe(200);
    expect(component.loading).toBeFalse();
  });

  it('loadSummary logs and clears loading on error', () => {
    const errSpy = spyOn(console, 'error');
    txService.getSpendingSummary.and.returnValue(throwError(() => new Error('x')));
    component.accountId = 1;
    component.loadSummary();
    expect(errSpy).toHaveBeenCalled();
    expect(component.loading).toBeFalse();
  });

  it('formatCategory lowercases and de-underscores', () => {
    expect(component.formatCategory('FOOD_DINING')).toBe('food dining');
  });

  it('getPercentage rounds against the total and guards divide-by-zero', () => {
    component.totalSpending = 200;
    expect(component.getPercentage(50)).toBe(25);
    component.totalSpending = 0;
    expect(component.getPercentage(50)).toBe(0);
  });
});
