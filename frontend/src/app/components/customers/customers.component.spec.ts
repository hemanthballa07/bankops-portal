import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CustomersComponent } from './customers.component';
import { CustomerService } from '../../services/customer.service';
import { Customer } from '../../models/customer.model';

describe('CustomersComponent', () => {
  let component: CustomersComponent;
  let fixture: ComponentFixture<CustomersComponent>;
  let customerService: jasmine.SpyObj<CustomerService>;

  const customer: Customer = {
    id: 1, firstName: 'Ada', lastName: 'Lovelace',
    email: 'ada@bank.test', phone: '555-0100', createdAt: '2026-06-01T00:00:00Z',
  };

  beforeEach(async () => {
    customerService = jasmine.createSpyObj('CustomerService', ['searchCustomers', 'createCustomer']);
    customerService.searchCustomers.and.returnValue(of([customer]));
    customerService.createCustomer.and.returnValue(of(customer));

    await TestBed.configureTestingModule({
      imports: [CustomersComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: CustomerService, useValue: customerService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomersComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads customers on init and clears loading', () => {
    component.ngOnInit();
    expect(customerService.searchCustomers).toHaveBeenCalledWith('');
    expect(component.customers.length).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('clears loading even when the load fails', () => {
    customerService.searchCustomers.and.returnValue(throwError(() => new Error('x')));
    component.loadCustomers();
    expect(component.loading).toBeFalse();
  });

  it('onSearch re-queries with the current search term', () => {
    component.searchQuery = 'ada';
    component.onSearch();
    expect(customerService.searchCustomers).toHaveBeenCalledWith('ada');
  });

  it('onCreateCustomer posts, reloads, hides the form, and resets the draft', () => {
    customerService.searchCustomers.calls.reset();
    component.newCustomer = { firstName: 'Grace', lastName: 'Hopper', email: 'grace@bank.test', phone: '555-0101' };
    component.showCreateForm = true;
    component.onCreateCustomer();
    expect(customerService.createCustomer).toHaveBeenCalledWith({
      firstName: 'Grace', lastName: 'Hopper', email: 'grace@bank.test', phone: '555-0101',
    });
    expect(customerService.searchCustomers).toHaveBeenCalled();
    expect(component.showCreateForm).toBeFalse();
    expect(component.newCustomer).toEqual({ firstName: '', lastName: '', email: '', phone: '' });
  });

  it('onCreateCustomer logs and keeps the form open on error', () => {
    const errSpy = spyOn(console, 'error');
    customerService.createCustomer.and.returnValue(throwError(() => new Error('dup')));
    component.showCreateForm = true;
    component.onCreateCustomer();
    expect(errSpy).toHaveBeenCalled();
    expect(component.showCreateForm).toBeTrue();
  });

  it('toggleCreateForm flips the flag', () => {
    expect(component.showCreateForm).toBeFalse();
    component.toggleCreateForm();
    expect(component.showCreateForm).toBeTrue();
  });
});
