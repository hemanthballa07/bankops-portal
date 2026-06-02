import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'login']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    authService.isAuthenticated.and.returnValue(false);
    authService.login.and.returnValue(of({ username: 'admin', roles: ['ROLE_ADMIN'] }));

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('redirects an already-authenticated user to the dashboard', () => {
      authService.isAuthenticated.and.returnValue(true);
      component.ngOnInit();
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('stays on the login screen when not authenticated', () => {
      authService.isAuthenticated.and.returnValue(false);
      component.ngOnInit();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('onSubmit', () => {
    it('logs in with the entered credentials and navigates home on success', () => {
      component.username = 'admin';
      component.password = 'password';
      component.onSubmit();
      expect(authService.login).toHaveBeenCalledWith('admin', 'password');
      expect(router.navigate).toHaveBeenCalledWith(['/']);
      expect(component.loading).toBeFalse();
      expect(component.error).toBe('');
    });

    it('surfaces an error message and clears loading on failure', () => {
      const errSpy = spyOn(console, 'error');
      authService.login.and.returnValue(throwError(() => new Error('401')));
      component.username = 'admin';
      component.password = 'wrong';
      component.onSubmit();
      expect(component.error).toBe('Invalid credentials or server error');
      expect(component.loading).toBeFalse();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(errSpy).toHaveBeenCalled();
    });

    it('clears any prior error before retrying', () => {
      component.error = 'stale error';
      component.username = 'admin';
      component.password = 'password';
      component.onSubmit();
      expect(component.error).toBe('');
    });
  });
});
