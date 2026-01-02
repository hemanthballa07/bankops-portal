import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An unknown error occurred';

      if (error.error instanceof ErrorEvent) {
        errorMessage = `Error: ${error.error.message}`;
      } else {
        switch (error.status) {
          case 401:
            errorMessage = 'Unauthorized. Please check your credentials.';
            break;
          case 403:
            errorMessage = 'Forbidden. You do not have permission to access this resource.';
            break;
          case 404:
            errorMessage = error.error?.message || 'Resource not found.';
            break;
          case 400:
            errorMessage = error.error?.message || 'Invalid request. Please check your input.';
            break;
          case 500:
            errorMessage = error.error?.message || 'Server error. Please try again later.';
            break;
          default:
            errorMessage = error.error?.message || `Error: ${error.status} ${error.statusText}`;
        }
      }

      console.error('HTTP Error:', errorMessage, error);
      return throwError(() => ({ ...error, userMessage: errorMessage }));
    })
  );
};



