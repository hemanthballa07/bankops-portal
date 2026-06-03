import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { FraudReviewComponent } from './fraud-review.component';
import { TransactionService } from '../../services/transaction.service';
import { Transaction } from '../../models/transaction.model';
import { MlRiskBandService } from '../../services/ml-risk-band.service';
import { MlRiskBandConfig } from '../../models/ml-risk-band.model';

describe('FraudReviewComponent', () => {
  let component: FraudReviewComponent;
  let fixture: ComponentFixture<FraudReviewComponent>;
  let txService: jasmine.SpyObj<TransactionService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let bandService: jasmine.SpyObj<MlRiskBandService>;

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

    bandService = jasmine.createSpyObj('MlRiskBandService', ['getBands', 'update']);
    bandService.getBands.and.returnValue(of({ medThreshold: 0.4, highThreshold: 0.7 } as MlRiskBandConfig));

    await TestBed.configureTestingModule({
      imports: [FraudReviewComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: TransactionService, useValue: txService },
        { provide: MlRiskBandService, useValue: bandService },
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

  describe('ML risk chip', () => {
    it('mlBand classifies low/med/high at the thresholds', () => {
      expect(component.mlBand(0.39)).toBe('low');
      expect(component.mlBand(0.4)).toBe('med');
      expect(component.mlBand(0.69)).toBe('med');
      expect(component.mlBand(0.7)).toBe('high');
      expect(component.mlBand(0.95)).toBe('high');
    });

    it('mlPercent renders a rounded percentage', () => {
      expect(component.mlPercent(0.78)).toBe('78%');
      expect(component.mlPercent(0.5)).toBe('50%');
    });

    it('renders a chip only for rows with mlScore > 0', () => {
      txService.getHeldTransactions.and.returnValue(
        of([
          { ...held(1), mlScore: 0.82 },
          { ...held(2), mlScore: 0 },
          { ...held(3), mlScore: undefined },
        ]),
      );
      component.load();
      fixture.detectChanges();
      const chips = fixture.nativeElement.querySelectorAll('.ml-chip');
      expect(chips.length).toBe(1);
      expect(chips[0].textContent).toContain('82%');
      expect(chips[0].classList).toContain('high');
    });

    it('sortByMlRisk cycles desc → asc → off and ranks scored rows, unscored last', () => {
      component.rows = [
        { ...held(1), mlScore: 0.2, selected: false, actioning: false },
        { ...held(2), mlScore: 0.8, selected: false, actioning: false },
        { ...held(3), mlScore: undefined, selected: false, actioning: false },
      ] as any;

      component.sortByMlRisk(); // desc
      expect(component.sortDir).toBe('desc');
      expect(component.rows.map((r) => r.id)).toEqual([2, 1, 3]);

      component.sortByMlRisk(); // asc
      expect(component.sortDir).toBe('asc');
      expect(component.rows.map((r) => r.id)).toEqual([1, 2, 3]);

      component.sortByMlRisk(); // off
      expect(component.sortDir).toBeNull();
      expect(component.rows.length).toBe(3);
    });

    it('mlBand uses the configured thresholds once bands load', () => {
      bandService.getBands.and.returnValue(of({ medThreshold: 0.5, highThreshold: 0.9 } as MlRiskBandConfig));
      fixture.detectChanges(); // ngOnInit fetches bands
      expect(component.mlBand(0.6)).toBe('med'); // 0.6 >= 0.5 (med) but < 0.9 (high)
      expect(component.mlBand(0.95)).toBe('high');
      expect(component.mlBand(0.4)).toBe('low'); // below configured med 0.5
    });
  });

  describe('accessibility', () => {
    it('bandLabel maps the score to a textual tier (not color-only)', () => {
      expect(component.bandLabel(0.2)).toBe('Low');
      expect(component.bandLabel(0.5)).toBe('Med');
      expect(component.bandLabel(0.85)).toBe('High');
    });

    it('sortAria reflects the sort direction for aria-sort', () => {
      expect(component.sortAria).toBe('none');
      component.sortByMlRisk();
      expect(component.sortAria).toBe('descending');
      component.sortByMlRisk();
      expect(component.sortAria).toBe('ascending');
    });

    it('renders the band in the chip text and aria-label', () => {
      txService.getHeldTransactions.and.returnValue(of([{ ...held(1), mlScore: 0.82 }]));
      component.load();
      fixture.detectChanges();
      const chip: HTMLElement = fixture.nativeElement.querySelector('.ml-chip');
      expect(chip.textContent).toContain('High');
      expect(chip.getAttribute('aria-label')).toContain('High');
      expect(chip.getAttribute('aria-label')).toContain('82%');
    });

    it('exposes the ML risk column as a keyboard-operable sort with aria-sort', () => {
      component.load();
      fixture.detectChanges();
      const th: HTMLElement = fixture.nativeElement.querySelector('th.col-ml');
      const btn = th.querySelector('button.col-sort-btn') as HTMLButtonElement | null;
      expect(btn).not.toBeNull();
      expect(th.getAttribute('aria-sort')).toBe('none');
      btn!.click();
      fixture.detectChanges();
      expect(th.getAttribute('aria-sort')).toBe('descending');
    });

    it('labels the icon-only release/reject buttons', () => {
      component.load();
      fixture.detectChanges();
      const release: HTMLElement = fixture.nativeElement.querySelector('.btn-release');
      const reject: HTMLElement = fixture.nativeElement.querySelector('.btn-reject');
      expect(release.getAttribute('aria-label')).toContain('Release');
      expect(reject.getAttribute('aria-label')).toContain('Reject');
    });

    it('chipTooltip includes the scoring model when present', () => {
      const base = 'Advisory ML fraud probability — not used for the hold decision';
      expect(component.chipTooltip({ ...held(1), mlScore: 0.8 } as any)).toBe(base);
      expect(component.chipTooltip({ ...held(1), mlScore: 0.8, evaluatedBy: 'fluxa-rules+ml-v1' } as any))
        .toContain('fluxa-rules+ml-v1');
    });
  });
});
