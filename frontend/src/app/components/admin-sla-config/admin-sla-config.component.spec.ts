import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { AdminSlaConfigComponent } from './admin-sla-config.component';
import { SlaConfigAdminService } from '../../services/sla-config-admin.service';
import { SlaConfig } from '../../models/sla-config.model';

describe('AdminSlaConfigComponent', () => {
  let component: AdminSlaConfigComponent;
  let fixture: ComponentFixture<AdminSlaConfigComponent>;
  let service: jasmine.SpyObj<SlaConfigAdminService>;

  const rows: SlaConfig[] = [
    { priority: 'P1', durationSeconds: 86400, updatedAt: null },
    { priority: 'P2', durationSeconds: 259200, updatedAt: null },
    { priority: 'P3', durationSeconds: 604800, updatedAt: null },
  ];

  beforeEach(async () => {
    service = jasmine.createSpyObj('SlaConfigAdminService', ['list', 'update']);
    service.list.and.returnValue(of(rows));
    service.update.and.returnValue(of({ priority: 'P1', durationSeconds: 3600, updatedAt: '2026-01-01T00:00:00' }));

    await TestBed.configureTestingModule({
      imports: [AdminSlaConfigComponent],
      providers: [provideNoopAnimations(), { provide: SlaConfigAdminService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSlaConfigComponent);
    component = fixture.componentInstance;
  });

  it('loads rows on init and maps seconds to hours', () => {
    fixture.detectChanges();
    expect(service.list).toHaveBeenCalled();
    expect(component.rows.length).toBe(3);
    expect(component.rows[0].hours).toBe(24); // 86400s
  });

  it('sets an error when load fails', () => {
    service.list.and.returnValue(throwError(() => new Error('x')));
    fixture.detectChanges();
    expect(component.error).toBe('Failed to load SLA config');
  });

  it('save converts hours to seconds and PUTs', () => {
    fixture.detectChanges();
    const row = component.rows[0];
    row.hours = 1;
    component.save(row);
    expect(service.update).toHaveBeenCalledWith('P1', 3600);
    expect(row.durationSeconds).toBe(3600);
  });

  it('save rejects non-positive hours without calling the service', () => {
    fixture.detectChanges();
    const row = component.rows[0];
    row.hours = 0;
    component.save(row);
    expect(component.error).toContain('greater than 0');
    expect(service.update).not.toHaveBeenCalled();
  });

  it('sets an error when save fails', () => {
    fixture.detectChanges();
    service.update.and.returnValue(throwError(() => new Error('x')));
    const row = component.rows[0];
    row.hours = 2;
    component.save(row);
    expect(component.error).toContain('Failed to save');
  });
});
