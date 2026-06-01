import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
    router = TestBed.inject(Router);
  });

  const run = () =>
    TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

  it('allows navigation when authenticated', () => {
    auth.isAuthenticated.and.returnValue(true);
    expect(run()).toBeTrue();
  });

  it('redirects to /login when not authenticated', () => {
    auth.isAuthenticated.and.returnValue(false);
    const result = run();
    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
