import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TransactionFormComponent } from './transaction-form.component';
import { CreateTransactionRequest } from '../../models/transaction.model';

describe('TransactionFormComponent', () => {
  let component: TransactionFormComponent;
  let fixture: ComponentFixture<TransactionFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionFormComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should validate negative amounts', () => {
    component.transaction = {
      type: 'DEPOSIT',
      amount: -10
    };
    expect(component.isValid()).toBeFalse();
  });

  it('should validate zero amounts', () => {
    component.transaction = {
      type: 'DEPOSIT',
      amount: 0
    };
    expect(component.isValid()).toBeFalse();
  });

  it('should validate positive amounts', () => {
    component.transaction = {
      type: 'DEPOSIT',
      amount: 100
    };
    expect(component.isValid()).toBeTrue();
  });

  it('should disable submit button for invalid amounts', () => {
    component.transaction = {
      type: 'DEPOSIT',
      amount: -10
    };
    fixture.detectChanges();
    const submitButton = fixture.nativeElement.querySelector('button[color="primary"]');
    expect(submitButton.disabled).toBeTrue();
  });

  it('should emit submit event with valid transaction', () => {
    spyOn(component.submit, 'emit');
    component.transaction = {
      type: 'DEPOSIT',
      amount: 100
    };
    component.onSubmit();
    expect(component.submit.emit).toHaveBeenCalledWith(component.transaction);
  });

  it('should not emit submit event with invalid transaction', () => {
    spyOn(component.submit, 'emit');
    component.transaction = {
      type: 'DEPOSIT',
      amount: -10
    };
    component.onSubmit();
    expect(component.submit.emit).not.toHaveBeenCalled();
  });
});





