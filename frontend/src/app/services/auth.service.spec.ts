import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  const apiUrl = environment.apiUrl;
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear(); // ensure the constructor sees no stored credential (no hydrate)
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('reports unauthenticated with no stored credential', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getAuthHeader()).toBeNull();
  });

  it('login GETs /whoami with a Basic header, stores the session, and emits the user', () => {
    let user: any = null;
    service.currentUser$.subscribe((u) => (user = u));

    service.login('admin', 'password').subscribe();
    const req = httpMock.expectOne(`${apiUrl}/whoami`);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Basic ' + btoa('admin:password'));
    req.flush({ username: 'admin', roles: ['ROLE_ADMIN'] });

    expect(service.getAuthHeader()).toBe('Basic ' + btoa('admin:password'));
    expect(service.isAuthenticated()).toBeTrue();
    expect(user).toEqual({ username: 'admin', roles: ['ROLE_ADMIN'] });
  });

  it('login falls back to the entered username and empty roles when the response omits them', () => {
    let user: any = null;
    service.currentUser$.subscribe((u) => (user = u));

    service.login('bob', 'pw').subscribe();
    httpMock.expectOne(`${apiUrl}/whoami`).flush({});
    expect(user).toEqual({ username: 'bob', roles: [] });
  });
});
