import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { AdminMlRiskBandsComponent } from './admin-ml-risk-bands.component';
import { MlRiskBandService } from '../../services/ml-risk-band.service';

describe('AdminMlRiskBandsComponent', () => {
  let component: AdminMlRiskBandsComponent;
  let fixture: ComponentFixture<AdminMlRiskBandsComponent>;
  let service: jasmine.SpyObj<MlRiskBandService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj('MlRiskBandService', ['getBands', 'update']);
    service.getBands.and.returnValue(of({ medThreshold: 0.4, highThreshold: 0.7, updatedAt: null }));
    service.update.and.returnValue(of({ medThreshold: 0.35, highThreshold: 0.65, updatedAt: '2026-06-03T00:00:00' }));

    await TestBed.configureTestingModule({
      imports: [AdminMlRiskBandsComponent],
      providers: [provideNoopAnimations(), { provide: MlRiskBandService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminMlRiskBandsComponent);
    component = fixture.componentInstance;
  });

  it('loads current bands on init', () => {
    fixture.detectChanges();
    expect(service.getBands).toHaveBeenCalled();
    expect(component.med).toBe(0.4);
    expect(component.high).toBe(0.7);
  });

  it('sets an error when load fails', () => {
    service.getBands.and.returnValue(throwError(() => new Error('x')));
    fixture.detectChanges();
    expect(component.error).toContain('Failed to load');
  });

  it('save PUTs valid thresholds and applies the response', () => {
    fixture.detectChanges();
    component.med = 0.35;
    component.high = 0.65;
    component.save();
    expect(service.update).toHaveBeenCalledWith(0.35, 0.65);
    expect(component.high).toBe(0.65);
  });

  it('save rejects med >= high without calling the service', () => {
    fixture.detectChanges();
    component.med = 0.8;
    component.high = 0.5;
    component.save();
    expect(component.error).toContain('Med');
    expect(service.update).not.toHaveBeenCalled();
  });

  it('sets an error when save fails', () => {
    fixture.detectChanges();
    service.update.and.returnValue(throwError(() => new Error('x')));
    component.med = 0.3;
    component.high = 0.6;
    component.save();
    expect(component.error).toContain('Failed to save');
  });

  it('marks the error banner as an alert region for screen readers', () => {
    service.getBands.and.returnValue(throwError(() => new Error('x')));
    fixture.detectChanges();
    const banner: HTMLElement = fixture.nativeElement.querySelector('.error-banner');
    expect(banner.getAttribute('role')).toBe('alert');
  });
});
