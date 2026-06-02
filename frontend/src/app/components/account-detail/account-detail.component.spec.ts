import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AccountDetailComponent } from './account-detail.component';
import { AccountService } from '../../services/account.service';
import { TransactionService } from '../../services/transaction.service';
import { Account } from '../../models/account.model';
import { Transaction, PagedResponse } from '../../models/transaction.model';

describe('AccountDetailComponent', () => {
  let component: AccountDetailComponent;
  let fixture: ComponentFixture<AccountDetailComponent>;
  let accountService: jasmine.SpyObj<AccountService>;
  let txService: jasmine.SpyObj<TransactionService>;
  let router: jasmine.SpyObj<Router>;

  const account: Account = {
    id: 1, customerId: 1, type: 'CHEQUING', status: 'ACTIVE',
    balance: 1000, overdraftEnabled: false, createdAt: '2026-06-01T00:00:00Z',
  };
  const heldTx: Transaction = {
    id: 5, accountId: 1, type: 'WITHDRAWAL', amount: 999, status: 'HELD',
    correlationId: 'c-5', createdAt: '2026-06-01T00:00:00Z',
  };
  const page = (content: Transaction[] = []): PagedResponse<Transaction> => ({
    content, page: 0, size: 20, totalElements: content.length, totalPages: 1,
  });

  beforeEach(async () => {
    accountService = jasmine.createSpyObj('AccountService', ['getAccountById']);
    txService = jasmine.createSpyObj('TransactionService', [
      'getTransactionsByAccountId', 'createTransaction', 'releaseTransaction', 'rejectTransaction',
    ]);
    router = jasmine.createSpyObj('Router', ['navigate']);

    accountService.getAccountById.and.returnValue(of(account));
    txService.getTransactionsByAccountId.and.returnValue(of(page([heldTx])));
    txService.createTransaction.and.returnValue(of({ ...heldTx, id: 9, correlationId: 'c-9' }));
    txService.releaseTransaction.and.returnValue(of({ ...heldTx, status: 'RELEASED' }));
    txService.rejectTransaction.and.returnValue(of({ ...heldTx, status: 'REJECTED' }));

    await TestBed.configureTestingModule({
      imports: [AccountDetailComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        { provide: Router, useValue: router },
        { provide: AccountService, useValue: accountService },
        { provide: TransactionService, useValue: txService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the account and transactions from the route id on init', () => {
    component.ngOnInit();
    expect(accountService.getAccountById).toHaveBeenCalledWith(1);
    expect(txService.getTransactionsByAccountId).toHaveBeenCalledWith(1, { page: 0, size: 20 });
    expect(component.account).toEqual(account);
    expect(component.transactions.length).toBe(1);
    expect(component.totalElements).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('loadTransactions clears loading and logs on error', () => {
    const errSpy = spyOn(console, 'error');
    txService.getTransactionsByAccountId.and.returnValue(throwError(() => new Error('x')));
    component.loadTransactions(1);
    expect(errSpy).toHaveBeenCalled();
    expect(component.loading).toBeFalse();
  });

  it('onFilterChange resets to the first page and reloads', () => {
    component.account = account;
    component.pageIndex = 3;
    component.onFilterChange({ status: 'HELD', startDate: '2026-01-01' });
    expect(component.pageIndex).toBe(0);
    expect(component.currentFilters).toEqual({ status: 'HELD', startDate: '2026-01-01' });
    expect(txService.getTransactionsByAccountId).toHaveBeenCalled();
  });

  it('onFilterChange is a no-op without an account', () => {
    component.account = undefined;
    component.onFilterChange({ status: 'HELD' });
    expect(txService.getTransactionsByAccountId).not.toHaveBeenCalled();
  });

  it('onPageChange forwards the new page to the service and syncs from the response', () => {
    // loadTransactions re-syncs pageIndex/pageSize from the server response, so echo the request back.
    txService.getTransactionsByAccountId.and.returnValue(
      of({ content: [heldTx], page: 2, size: 50, totalElements: 100, totalPages: 2 }),
    );
    component.account = account;
    component.onPageChange({ pageIndex: 2, pageSize: 50, length: 100 });
    expect(txService.getTransactionsByAccountId).toHaveBeenCalledWith(1, jasmine.objectContaining({ page: 2, size: 50 }));
    expect(component.pageIndex).toBe(2);
    expect(component.pageSize).toBe(50);
  });

  it('onCreateTransaction rejects a non-positive amount without calling the service', () => {
    const alertSpy = spyOn(window, 'alert');
    component.account = account;
    component.newTransaction = { type: 'DEPOSIT', amount: 0 };
    component.onCreateTransaction();
    expect(alertSpy).toHaveBeenCalledWith('Amount must be greater than 0');
    expect(txService.createTransaction).not.toHaveBeenCalled();
  });

  it('onCreateTransaction posts, reloads, resets, and confirms with the correlation id', () => {
    const alertSpy = spyOn(window, 'alert');
    component.account = account;
    accountService.getAccountById.calls.reset();
    component.newTransaction = { type: 'DEPOSIT', amount: 250, description: 'pay', category: 'OTHER' };
    component.onCreateTransaction();
    expect(txService.createTransaction).toHaveBeenCalledWith(1, jasmine.objectContaining({ amount: 250 }));
    expect(accountService.getAccountById).toHaveBeenCalledWith(1);
    expect(component.showTransactionForm).toBeFalse();
    expect(component.newTransaction).toEqual({ type: 'DEPOSIT', amount: 0, description: '', category: 'OTHER' });
    expect(alertSpy).toHaveBeenCalledWith(jasmine.stringMatching('c-9'));
  });

  it('viewIncident navigates to the incident route', () => {
    component.viewIncident('c-5');
    expect(router.navigate).toHaveBeenCalledWith(['/incidents', 'c-5']);
  });

  it('releaseHeld is gated on confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    component.account = account;
    component.releaseHeld(heldTx);
    expect(txService.releaseTransaction).not.toHaveBeenCalled();
  });

  it('releaseHeld releases and reloads when confirmed', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.account = account;
    component.releaseHeld(heldTx);
    expect(txService.releaseTransaction).toHaveBeenCalledWith(1, 5);
    expect(component.reviewingTransactionId).toBeNull();
    expect(accountService.getAccountById).toHaveBeenCalled();
  });

  it('rejectHeld rejects and reloads transactions when confirmed', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    component.account = account;
    txService.getTransactionsByAccountId.calls.reset();
    component.rejectHeld(heldTx);
    expect(txService.rejectTransaction).toHaveBeenCalledWith(1, 5);
    expect(component.reviewingTransactionId).toBeNull();
    expect(txService.getTransactionsByAccountId).toHaveBeenCalled();
  });

  it('releaseHeld alerts and clears the reviewing flag on error', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    const alertSpy = spyOn(window, 'alert');
    txService.releaseTransaction.and.returnValue(throwError(() => ({ message: 'boom' })));
    component.account = account;
    component.releaseHeld(heldTx);
    expect(alertSpy).toHaveBeenCalled();
    expect(component.reviewingTransactionId).toBeNull();
  });

  it('toggleTransactionForm flips the flag', () => {
    expect(component.showTransactionForm).toBeFalse();
    component.toggleTransactionForm();
    expect(component.showTransactionForm).toBeTrue();
  });
});
