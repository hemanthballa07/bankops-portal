import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CustomerDetailComponent } from './customer-detail.component';
import { CustomerService } from '../../services/customer.service';
import { AccountService } from '../../services/account.service';
import { Customer } from '../../models/customer.model';
import { Account } from '../../models/account.model';

describe('CustomerDetailComponent', () => {
  let component: CustomerDetailComponent;
  let fixture: ComponentFixture<CustomerDetailComponent>;
  let customerService: jasmine.SpyObj<CustomerService>;
  let accountService: jasmine.SpyObj<AccountService>;
  let router: jasmine.SpyObj<Router>;

  const customer: Customer = {
    id: 1, firstName: 'Ada', lastName: 'Lovelace',
    email: 'ada@bank.test', phone: '555-0100', createdAt: '2026-06-01T00:00:00Z',
  };
  const account: Account = {
    id: 10, customerId: 1, type: 'CHEQUING', status: 'ACTIVE',
    balance: 5000, overdraftEnabled: false, createdAt: '2026-06-01T00:00:00Z',
  };

  beforeEach(async () => {
    customerService = jasmine.createSpyObj('CustomerService', ['getCustomerById']);
    accountService = jasmine.createSpyObj('AccountService', ['getAccountsByCustomerId', 'createAccount']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    customerService.getCustomerById.and.returnValue(of(customer));
    accountService.getAccountsByCustomerId.and.returnValue(of([account]));
    accountService.createAccount.and.returnValue(of(account));

    await TestBed.configureTestingModule({
      imports: [CustomerDetailComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        { provide: Router, useValue: router },
        { provide: CustomerService, useValue: customerService },
        { provide: AccountService, useValue: accountService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads the customer and accounts from the route id on init', () => {
    component.ngOnInit();
    expect(customerService.getCustomerById).toHaveBeenCalledWith(1);
    expect(accountService.getAccountsByCustomerId).toHaveBeenCalledWith(1);
    expect(component.customer).toEqual(customer);
    expect(component.accounts.length).toBe(1);
  });

  it('logs when the customer or accounts fail to load', () => {
    const errSpy = spyOn(console, 'error');
    customerService.getCustomerById.and.returnValue(throwError(() => new Error('x')));
    accountService.getAccountsByCustomerId.and.returnValue(throwError(() => new Error('y')));
    component.loadCustomer(1);
    component.loadAccounts(1);
    expect(errSpy).toHaveBeenCalledTimes(2);
  });

  it('onCreateAccount does nothing without a loaded customer', () => {
    component.customer = undefined;
    component.onCreateAccount();
    expect(accountService.createAccount).not.toHaveBeenCalled();
  });

  it('onCreateAccount creates, reloads accounts, hides the form, and resets the draft', () => {
    component.customer = customer;
    accountService.getAccountsByCustomerId.calls.reset();
    component.newAccount = { type: 'SAVINGS' };
    component.showCreateAccountForm = true;
    component.onCreateAccount();
    expect(accountService.createAccount).toHaveBeenCalledWith(1, { type: 'SAVINGS' });
    expect(accountService.getAccountsByCustomerId).toHaveBeenCalledWith(1);
    expect(component.showCreateAccountForm).toBeFalse();
    expect(component.newAccount).toEqual({ type: 'CHEQUING' });
  });

  it('onCreateAccount alerts on failure', () => {
    const alertSpy = spyOn(window, 'alert');
    spyOn(console, 'error');
    accountService.createAccount.and.returnValue(throwError(() => ({ message: 'boom' })));
    component.customer = customer;
    component.onCreateAccount();
    expect(alertSpy).toHaveBeenCalled();
  });

  it('viewAccount navigates to the account detail route', () => {
    component.viewAccount(10);
    expect(router.navigate).toHaveBeenCalledWith(['/accounts', 10]);
  });

  it('toggleCreateAccountForm flips the flag', () => {
    expect(component.showCreateAccountForm).toBeFalse();
    component.toggleCreateAccountForm();
    expect(component.showCreateAccountForm).toBeTrue();
  });
});
