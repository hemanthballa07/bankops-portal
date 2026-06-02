import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';

import { NotificationsRailComponent } from './notifications-rail.component';
import { NotificationsSummary } from '../../models/notification.model';

describe('NotificationsRailComponent', () => {
  let fixture: ComponentFixture<NotificationsRailComponent>;
  let component: NotificationsRailComponent;
  let router: jasmine.SpyObj<Router>;

  const summary: NotificationsSummary = {
    items: [
      { id: 'HELD-1', category: 'FRAUD_HOLD', severity: 'CRITICAL', title: 'Fraud hold', detail: 'd',
        entityType: 'TRANSACTION', entityId: 1, link: '/fraud-review', timestamp: null },
      { id: 'BACKLOG', category: 'BACKLOG', severity: 'INFO', title: 'Case backlog', detail: '3 open · 2 unassigned',
        entityType: 'CASE', entityId: null, link: '/cases', timestamp: null },
    ],
    counts: { critical: 1, warning: 0, info: 1, total: 2 },
  };

  beforeEach(async () => {
    router = jasmine.createSpyObj('Router', ['navigate']);
    await TestBed.configureTestingModule({
      imports: [NotificationsRailComponent],
      providers: [provideNoopAnimations(), { provide: Router, useValue: router }],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationsRailComponent);
    component = fixture.componentInstance;
    component.summary = summary;
    fixture.detectChanges();
  });

  it('alertItems excludes BACKLOG; backlog getter returns it', () => {
    expect(component.alertItems.length).toBe(1);
    expect(component.alertItems[0].id).toBe('HELD-1');
    expect(component.backlog?.id).toBe('BACKLOG');
  });

  it('openItem navigates to the item link and emits closeRail', () => {
    const closeSpy = jasmine.createSpy('close');
    component.closeRail.subscribe(closeSpy);
    component.openItem(summary.items[0]);
    expect(router.navigate).toHaveBeenCalledWith(['/fraud-review']);
    expect(closeSpy).toHaveBeenCalled();
  });

  it('severityClass maps severity to a css class', () => {
    expect(component.severityClass('CRITICAL')).toBe('sev-critical');
  });

  it('shows empty state when no alert items', () => {
    component.summary = { items: [], counts: { critical: 0, warning: 0, info: 0, total: 0 } };
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('All clear');
  });
});
