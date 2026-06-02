import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuditTimelineComponent } from './audit-timeline.component';
import { AuditService } from '../../services/audit.service';
import { AuditEvent, PagedAuditResponse } from '../../models/audit-event.model';

describe('AuditTimelineComponent', () => {
  let component: AuditTimelineComponent;
  let fixture: ComponentFixture<AuditTimelineComponent>;
  let auditService: jasmine.SpyObj<AuditService>;

  const event = (over: Partial<AuditEvent> = {}): AuditEvent => ({
    id: 'a1', entityType: 'CASE', entityId: 1, action: 'UPDATE',
    oldValue: '{"status":"OPEN"}', newValue: '{"status":"CLOSED"}',
    performedBy: 'admin', timestamp: '2026-06-01T00:00:00Z', ...over,
  });
  const resp = (content: AuditEvent[] = []): PagedAuditResponse => ({
    content, page: 0, size: 20, totalElements: content.length, totalPages: 1,
  });

  beforeEach(async () => {
    auditService = jasmine.createSpyObj('AuditService', ['getAuditTimeline']);
    auditService.getAuditTimeline.and.returnValue(of(resp([event()])));

    await TestBed.configureTestingModule({
      imports: [AuditTimelineComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuditService, useValue: auditService },
        { provide: ActivatedRoute, useValue: { params: of({ entityType: 'CASE', entityId: '1' }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditTimelineComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('reads route params on init and loads the timeline', () => {
    component.ngOnInit();
    expect(component.entityType).toBe('CASE');
    expect(component.entityId).toBe(1);
    expect(auditService.getAuditTimeline).toHaveBeenCalledWith('CASE', 1, 0, 20);
    expect(component.auditEvents.length).toBe(1);
    expect(component.totalElements).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('sets the server error message on failure', () => {
    auditService.getAuditTimeline.and.returnValue(throwError(() => ({ error: { message: 'boom' } })));
    component.entityType = 'CASE';
    component.entityId = 1;
    component.loadAuditEvents();
    expect(component.error).toBe('boom');
    expect(component.loading).toBeFalse();
  });

  it('falls back to a default error message', () => {
    auditService.getAuditTimeline.and.returnValue(throwError(() => ({})));
    component.loadAuditEvents();
    expect(component.error).toBe('Failed to load audit timeline');
  });

  it('onPageChange applies the page event and reloads', () => {
    component.entityType = 'CASE';
    component.entityId = 1;
    component.onPageChange({ pageIndex: 2, pageSize: 50, length: 100 });
    expect(component.page).toBe(2);
    expect(component.size).toBe(50);
    expect(auditService.getAuditTimeline).toHaveBeenCalledWith('CASE', 1, 2, 50);
  });

  it('getActionColor maps actions to material palettes', () => {
    expect(component.getActionColor('CREATE')).toBe('primary');
    expect(component.getActionColor('UPDATE')).toBe('accent');
    expect(component.getActionColor('STATUS_CHANGE')).toBe('warn');
    expect(component.getActionColor('FLAG_TOGGLE')).toBe('');
    expect(component.getActionColor('OTHER')).toBe('');
  });

  it('parseJsonValue parses JSON and returns the raw value on failure', () => {
    expect(component.parseJsonValue('{"a":1}')).toEqual({ a: 1 });
    expect(component.parseJsonValue('not json')).toBe('not json');
  });

  it('getChangedFields returns the union of changed keys, or [] when a side is empty', () => {
    const e = event({ oldValue: '{"a":1,"b":2}', newValue: '{"b":3,"c":4}' });
    expect(component.getChangedFields(e).sort()).toEqual(['a', 'b', 'c']);
    expect(component.getChangedFields(event({ oldValue: 'null', newValue: '{"a":1}' }))).toEqual([]);
  });

  it('getFieldChange returns the old and new value for a field', () => {
    const change = component.getFieldChange(event(), 'status');
    expect(change).toEqual({ old: 'OPEN', new: 'CLOSED' });
  });
});
