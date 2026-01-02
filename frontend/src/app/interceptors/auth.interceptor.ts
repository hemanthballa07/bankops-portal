import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const username = 'user';
  const password = 'password';

  if (req.url.startsWith(environment.apiUrl)) {
    const authHeader = 'Basic ' + btoa(`${username}:${password}`);
    const authReq = req.clone({
      setHeaders: {
        Authorization: authHeader
      }
    });
    return next(authReq);
  }

  return next(req);
};



