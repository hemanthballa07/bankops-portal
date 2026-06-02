import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { NotificationService } from './notification.service';
import { environment } from '../../environments/environment';

describe('NotificationService', () => {
  const base = `${environment.apiUrl}/notifications`;
  let service: NotificationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), NotificationService],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getNotifications GETs /notifications', () => {
    let received: any = null;
    service.getNotifications().subscribe(s => (received = s));
    const req = httpMock.expectOne(base);
    expect(req.request.method).toBe('GET');
    req.flush({
      items: [{ id: 'HELD-1', category: 'FRAUD_HOLD', severity: 'CRITICAL', title: 't',
        detail: 'd', entityType: 'TRANSACTION', entityId: 1, link: '/fraud-review', timestamp: null }],
      counts: { critical: 1, warning: 0, info: 1, total: 2 },
    });
    expect(received.counts.critical).toBe(1);
    expect(received.items.length).toBe(1);
  });

  it('startPolling fetches once on start and again on refresh()', fakeAsync(() => {
    const sub = service.startPolling().subscribe();
    tick(0); // fire the single initial timer(0) emission deterministically
    httpMock.expectOne(base).flush({ items: [], counts: { critical: 0, warning: 0, info: 0, total: 0 } });

    service.refresh();
    httpMock.expectOne(base).flush({ items: [], counts: { critical: 2, warning: 0, info: 0, total: 2 } });

    sub.unsubscribe();
    discardPeriodicTasks(); // clear the pending 30s periodic timer from the fakeAsync queue
  }));
});
