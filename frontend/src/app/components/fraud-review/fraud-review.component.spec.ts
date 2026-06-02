import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { FraudReviewComponent } from './fraud-review.component';
import { TransactionService } from '../../services/transaction.service';
import { Transaction } from '../../models/transaction.model';

describe('FraudReviewComponent', () => {
  let component: FraudReviewComponent;
  let fixture: ComponentFixture<FraudReviewComponent>;
  let txService: jasmine.SpyObj<TransactionService>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  const held = (id: number, accountId = 1): Transaction => ({
    id,
    accountId,
    type: 'DEPOSIT',
    amount: 99999,
    status: 'HELD',
    correlationId: `corr-${id}`,
    description: 'luxury car',
    createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  });

  beforeEach(async () => {
    txService = jasmine.createSpyObj('TransactionService', [
      'getHeldTransactions',
      'releaseTransaction',
      'rejectTransaction',
    ]);
    txService.getHeldTransactions.and.returnValue(of([held(1), held(2)]));
    txService.releaseTransaction.and.returnValue(of(held(1)));
    txService.rejectTransaction.and.returnValue(of(held(1)));

    await TestBed.configureTestingModule({
      imports: [FraudReviewComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: TransactionService, useValue: txService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FraudReviewComponent);
    component = fixture.componentInstance;
    snack = jasmine.createSpyObj('MatSnackBar', ['open']);
    // Redirect the component's injected MatSnackBar to our spy.
    (component as unknown as { snack: MatSnackBar }).snack = snack;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('load', () => {
    it('maps held transactions to selectable rows and clears loading', () => {
      component.load();
      expect(txService.getHeldTransactions).toHaveBeenCalled();
      expect(component.rows.length).toBe(2);
      expect(component.rows.every((r) => r.selected === false && r.actioning === false)).toBeTrue();
      expect(component.loading).toBeFalse();
    });

    it('shows a snackbar and clears loading when the load fails', () => {
      txService.getHeldTransactions.and.returnValue(throwError(() => new Error('boom')));
      component.load();
      expect(snack.open).toHaveBeenCalledWith('Failed to load fraud holds', 'Dismiss', { duration: 4000 });
      expect(component.loading).toBeFalse();
      expect(component.rows.length).toBe(0);
    });

    it('runs on init', () => {
      fixture.detectChanges(); // triggers ngOnInit
      expect(txService.getHeldTransactions).toHaveBeenCalled();
      expect(component.rows.length).toBe(2);
    });
  });

  describe('selection getters', () => {
    beforeEach(() => component.load());

    it('selectedRows returns only the selected rows', () => {
      component.rows[0].selected = true;
      expect(component.selectedRows.length).toBe(1);
      expect(component.selectedRows[0].id).toBe(1);
    });

    it('allSelected is true only when every row is selected and rows exist', () => {
      expect(component.allSelected).toBeFalse();
      component.toggleAll(true);
      expect(component.allSelected).toBeTrue();
    });

    it('allSelected is false when there are no rows', () => {
      component.rows = [];
      expect(component.allSelected).toBeFalse();
    });

    it('someSelected is true when a subset is selected', () => {
      component.rows[0].selected = true;
      expect(component.someSelected).toBeTrue();
      component.toggleAll(true);
      expect(component.someSelected).toBeFalse();
    });

    it('toggleAll flips every row', () => {
      component.toggleAll(true);
      expect(component.rows.every((r) => r.selected)).toBeTrue();
      component.toggleAll(false);
      expect(component.rows.every((r) => !r.selected)).toBeTrue();
    });
  });

  describe('release', () => {
    beforeEach(() => component.load());

    it('removes the row and notifies on success', () => {
      const row = component.rows[0];
      component.release(row);
      expect(txService.releaseTransaction).toHaveBeenCalledWith(row.accountId, row.id);
      expect(component.rows.find((r) => r.id === row.id)).toBeUndefined();
      expect(snack.open).toHaveBeenCalledWith('Transaction #1 released', '', { duration: 3000 });
    });

    it('keeps the row, clears actioning, and notifies on failure', () => {
      txService.releaseTransaction.and.returnValue(throwError(() => new Error('nope')));
      const row = component.rows[0];
      component.release(row);
      expect(component.rows.find((r) => r.id === row.id)).toBeDefined();
      expect(row.actioning).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('Release failed', 'Dismiss', { duration: 4000 });
    });
  });

  describe('reject', () => {
    beforeEach(() => component.load());

    it('removes the row and notifies on success', () => {
      const row = component.rows[1];
      component.reject(row);
      expect(txService.rejectTransaction).toHaveBeenCalledWith(row.accountId, row.id);
      expect(component.rows.find((r) => r.id === row.id)).toBeUndefined();
      expect(snack.open).toHaveBeenCalledWith('Transaction #2 rejected', '', { duration: 3000 });
    });

    it('keeps the row, clears actioning, and notifies on failure', () => {
      txService.rejectTransaction.and.returnValue(throwError(() => new Error('nope')));
      const row = component.rows[0];
      component.reject(row);
      expect(component.rows.find((r) => r.id === row.id)).toBeDefined();
      expect(row.actioning).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('Reject failed', 'Dismiss', { duration: 4000 });
    });
  });

  describe('batch actions', () => {
    beforeEach(() => component.load());

    it('batchRelease is a no-op with nothing selected', () => {
      component.batchRelease();
      expect(txService.releaseTransaction).not.toHaveBeenCalled();
      expect(component.batchActioning).toBeFalse();
    });

    it('batchRelease releases every selected row and reports the count', () => {
      component.toggleAll(true);
      component.batchRelease();
      expect(txService.releaseTransaction).toHaveBeenCalledTimes(2);
      expect(component.rows.length).toBe(0);
      expect(component.batchActioning).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('2 transaction(s) released', '', { duration: 3000 });
    });

    it('batchReject is a no-op with nothing selected', () => {
      component.batchReject();
      expect(txService.rejectTransaction).not.toHaveBeenCalled();
      expect(component.batchActioning).toBeFalse();
    });

    it('batchReject rejects every selected row and reports the count', () => {
      component.toggleAll(true);
      component.batchReject();
      expect(txService.rejectTransaction).toHaveBeenCalledTimes(2);
      expect(component.rows.length).toBe(0);
      expect(component.batchActioning).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('2 transaction(s) rejected', '', { duration: 3000 });
    });

    it('batchRelease surfaces a failure message when a release errors', () => {
      txService.releaseTransaction.and.returnValue(throwError(() => new Error('x')));
      component.toggleAll(true);
      component.batchRelease();
      expect(component.batchActioning).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('Some releases failed', 'Dismiss', { duration: 4000 });
    });
  });

  describe('formatting helpers', () => {
    it('formatAmount renders USD currency', () => {
      expect(component.formatAmount(1234.5)).toBe('$1,234.50');
    });

    it('timeAgo renders minutes, hours, and days', () => {
      expect(component.timeAgo(new Date(Date.now() - 5 * 60 * 1000).toISOString())).toBe('5m ago');
      expect(component.timeAgo(new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString())).toBe('3h ago');
      expect(component.timeAgo(new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString())).toBe('2d ago');
    });
  });
});
