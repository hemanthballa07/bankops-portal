import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, fromEvent, merge, of, timer } from 'rxjs';
import { catchError, filter, switchMap, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { EMPTY_SUMMARY, NotificationsSummary } from '../models/notification.model';

const POLL_INTERVAL_MS = 30000;

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly apiUrl = `${environment.apiUrl}/notifications`;
  private http = inject(HttpClient);

  private readonly refresh$ = new Subject<void>();
  private readonly summarySubject = new BehaviorSubject<NotificationsSummary>(EMPTY_SUMMARY);
  /** Latest summary; drives the bell badge + rail. */
  readonly summary$ = this.summarySubject.asObservable();

  getNotifications(): Observable<NotificationsSummary> {
    return this.http.get<NotificationsSummary>(this.apiUrl);
  }

  /**
   * 30s poll + manual refresh + tab-visibility wake. Fetch errors keep the last-good
   * summary (the shell must never break). Subscribe once (in AppComponent) for the lifetime
   * of the app; read state via summary$.
   */
  startPolling(): Observable<NotificationsSummary> {
    // `timer(0, …)` owns the initial + periodic fetch (one request on subscribe);
    // visibilitychange only RESUMES on real tab transitions (no synchronous seed),
    // so the stream fires exactly once on load.
    const visibleWake$ = fromEvent(document, 'visibilitychange').pipe(
      filter(() => document.visibilityState === 'visible'),
    );
    return merge(timer(0, POLL_INTERVAL_MS), this.refresh$, visibleWake$).pipe(
      switchMap(() =>
        this.getNotifications().pipe(catchError(() => of(this.summarySubject.value))),
      ),
      tap(summary => this.summarySubject.next(summary)),
    );
  }

  refresh(): void {
    this.refresh$.next();
  }
}
