import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // For Basic Auth, you would typically get credentials from a service
  // For now, using environment variables or defaults
  const username = 'user'; // In real app, get from auth service
  const password = 'password'; // In real app, get from auth service or token
  
  // Only add auth to API requests
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

