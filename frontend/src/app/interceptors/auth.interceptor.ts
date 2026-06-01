import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith(environment.apiUrl)) {
    const header = inject(AuthService).getAuthHeader();
    // Only attach when the caller hasn't already set Authorization — never
    // clobber AuthService.login()'s explicit /whoami probe header.
    if (header && !req.headers.has('Authorization')) {
      return next(req.clone({ setHeaders: { Authorization: header } }));
    }
  }
  return next(req);
};
