import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { CaseTimelineComponent } from './case-timeline.component';
import { CaseTimelineEventDto, EventType } from '../../models/timeline.model';

describe('CaseTimelineComponent', () => {
  let component: CaseTimelineComponent;
  let fixture: ComponentFixture<CaseTimelineComponent>;
  let httpMock: HttpTestingController;
  let dialogRef: jasmine.SpyObj<MatDialogRef<CaseTimelineComponent>>;

  const event = (id: number, over: Partial<CaseTimelineEventDto> = {}): CaseTimelineEventDto => ({
    id, timestamp: '2026-06-01T00:00:00Z', eventType: EventType.STATE_CHANGE,
    summary: `event ${id}`, details: {}, actor: 'admin', correlationId: `c-${id}`, ...over,
  });

  const TIMELINE_URL = '/api/cases/7/timeline';
  const REPLAY_URL = '/api/cases/7/replay';

  beforeEach(async () => {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [CaseTimelineComponent],
      providers: [
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: { caseId: 7 } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CaseTimelineComponent);
    component = fixture.componentInstance;
    // The constructor fires the initial page-0 load. The component builds the URL by
    // string concatenation, so the query lives in req.url (not req.params).
    httpMock.expectOne((r) => r.url.startsWith(TIMELINE_URL)).flush([event(1)]);
  });

  afterEach(() => httpMock.verify());

  it('loads the initial timeline from the dialog data caseId', () => {
    expect(component.caseId).toBe(7);
    expect(component.timeline.length).toBe(1);
    expect(component.loading).toBeFalse();
  });

  it('loadMore appends the next page', () => {
    component.loadMore();
    expect(component.page).toBe(1);
    const req = httpMock.expectOne((r) => r.url.startsWith(TIMELINE_URL) && r.url.includes('page=1'));
    req.flush([event(2)]);
    expect(component.timeline.map((e) => e.id)).toEqual([1, 2]);
  });

  it('filterByEventType resets paging and adds the eventType param', () => {
    component.selectedEventType = 'STATE_CHANGE';
    component.filterByEventType();
    expect(component.page).toBe(0);
    const req = httpMock.expectOne((r) => r.url.includes('eventType=STATE_CHANGE'));
    req.flush([event(3)]);
    expect(component.timeline.map((e) => e.id)).toEqual([3]);
  });

  it('sets an error message when the load fails', () => {
    component.loadTimeline();
    const req = httpMock.expectOne((r) => r.url.startsWith(TIMELINE_URL));
    req.flush('nope', { status: 500, statusText: 'Server Error' });
    expect(component.error).toContain('Failed to load timeline');
    expect(component.loading).toBeFalse();
  });

  it('replayAt loads a snapshot for the event timestamp', () => {
    component.replayAt(event(1));
    const req = httpMock.expectOne((r) => r.url.startsWith(REPLAY_URL));
    expect(req.request.url).toContain('at=');
    req.flush({ snapshotAt: '2026-06-01T00:00:00Z', state: 'OPEN' });
    expect(component.replaySnapshot?.state).toBe('OPEN');
    expect(component.replayLoading).toBeFalse();
  });

  it('closeReplay clears the snapshot', () => {
    component.replaySnapshot = { snapshotAt: 'x', state: 'OPEN' };
    component.closeReplay();
    expect(component.replaySnapshot).toBeNull();
  });

  it('toggleDetails toggles the expanded event id', () => {
    component.toggleDetails(5);
    expect(component.expandedEventId).toBe(5);
    component.toggleDetails(5);
    expect(component.expandedEventId).toBeNull();
  });

  it('getEventTypeBadgeClass maps event types to badges', () => {
    expect(component.getEventTypeBadgeClass(EventType.STATE_CHANGE)).toBe('badge-state');
    expect(component.getEventTypeBadgeClass(EventType.SLA_CHANGE)).toBe('badge-sla');
    expect(component.getEventTypeBadgeClass(EventType.ASSIGNMENT)).toBe('badge-assignment');
    expect(component.getEventTypeBadgeClass('OTHER' as EventType)).toBe('badge-default');
  });

  it('formatRelativeTime renders minutes, hours, and days', () => {
    expect(component.formatRelativeTime(new Date(Date.now() - 5 * 60 * 1000).toISOString())).toBe('5m ago');
    expect(component.formatRelativeTime(new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString())).toBe('3h ago');
    expect(component.formatRelativeTime(new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString())).toBe('2d ago');
  });

  it('close delegates to the dialog ref', () => {
    component.close();
    expect(dialogRef.close).toHaveBeenCalled();
  });
});
