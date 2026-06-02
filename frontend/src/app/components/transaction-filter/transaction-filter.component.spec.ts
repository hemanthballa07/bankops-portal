import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { TransactionFilterComponent } from './transaction-filter.component';
import { TransactionFilterRequest } from '../../models/transaction.model';

describe('TransactionFilterComponent', () => {
  let component: TransactionFilterComponent;
  let fixture: ComponentFixture<TransactionFilterComponent>;
  let emitted: TransactionFilterRequest | undefined;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionFilterComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionFilterComponent);
    component = fixture.componentInstance;
    emitted = undefined;
    component.filterChange.subscribe((v) => (emitted = v));
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('onFilterChange emits the active filters with page reset to 0', () => {
    component.filters = { startDate: null, endDate: null, type: 'DEPOSIT', status: 'COMPLETED' };
    component.searchText = 'coffee';
    component.onFilterChange();
    expect(emitted).toEqual({
      startDate: undefined,
      endDate: undefined,
      type: 'DEPOSIT',
      status: 'COMPLETED',
      searchText: 'coffee',
      page: 0,
    });
  });

  it('formats Date filter values to yyyy-MM-dd', () => {
    component.filters = {
      startDate: new Date('2026-01-15T12:00:00Z'),
      endDate: new Date('2026-02-20T12:00:00Z'),
      type: null,
      status: null,
    };
    component.onFilterChange();
    expect(emitted!.startDate).toBe('2026-01-15');
    expect(emitted!.endDate).toBe('2026-02-20');
  });

  it('maps falsy type/status/searchText to undefined', () => {
    component.filters = { startDate: null, endDate: null, type: null, status: null };
    component.searchText = '';
    component.onFilterChange();
    expect(emitted).toEqual({
      startDate: undefined,
      endDate: undefined,
      type: undefined,
      status: undefined,
      searchText: undefined,
      page: 0,
    });
  });

  it('debounces search input and emits after 300ms', fakeAsync(() => {
    component.onSearchChange('amaz');
    expect(emitted).toBeUndefined(); // nothing yet
    component.onSearchChange('amazon');
    tick(299);
    expect(emitted).toBeUndefined(); // still debouncing
    tick(1);
    expect(emitted!.searchText).toBe('amazon'); // only the latest value emits
  }));

  it('clearFilters resets every filter and emits an empty request', () => {
    component.filters = { startDate: new Date('2026-01-15T12:00:00Z'), endDate: null, type: 'DEPOSIT', status: 'FAILED' };
    component.searchText = 'stale';
    component.clearFilters();
    expect(component.filters).toEqual({ startDate: null, endDate: null, type: null, status: null });
    expect(component.searchText).toBe('');
    expect(emitted).toEqual({
      startDate: undefined,
      endDate: undefined,
      type: undefined,
      status: undefined,
      searchText: undefined,
      page: 0,
    });
  });
});
