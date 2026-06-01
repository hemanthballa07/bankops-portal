import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getAuthHeader']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
  });
  afterEach(() => ctrl.verify());

  it('attaches the stored Basic header to API requests', () => {
    auth.getAuthHeader.and.returnValue('Basic c3VwcG9ydDpwYXNzd29yZA==');
    http.get(`${environment.apiUrl}/whoami`).subscribe();
    const req = ctrl.expectOne(`${environment.apiUrl}/whoami`);
    expect(req.request.headers.get('Authorization')).toBe('Basic c3VwcG9ydDpwYXNzd29yZA==');
    req.flush({});
  });

  it('does NOT attach a header when none is stored', () => {
    auth.getAuthHeader.and.returnValue(null);
    http.get(`${environment.apiUrl}/whoami`).subscribe();
    const req = ctrl.expectOne(`${environment.apiUrl}/whoami`);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('leaves non-API requests untouched', () => {
    auth.getAuthHeader.and.returnValue('Basic xxx');
    http.get('https://example.com/thing').subscribe();
    const req = ctrl.expectOne('https://example.com/thing');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('does not overwrite an Authorization header the caller already set', () => {
    auth.getAuthHeader.and.returnValue('Basic stored');
    http.get(`${environment.apiUrl}/whoami`, { headers: { Authorization: 'Basic caller' } }).subscribe();
    const req = ctrl.expectOne(`${environment.apiUrl}/whoami`);
    expect(req.request.headers.get('Authorization')).toBe('Basic caller');
    req.flush({});
  });
});
