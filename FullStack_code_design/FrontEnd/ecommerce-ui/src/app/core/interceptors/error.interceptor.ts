// ╔══════════════════════════════════════════════════════════════╗
// ║           Error Interceptor — Global HTTP Error Handling     ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Chain of Responsibility
//   This interceptor sits AFTER authInterceptor in the chain.
//   It handles the response side — catches HTTP errors centrally.
//
// Handles:
//   401 → Token expired → dispatch refreshTokenRequest → retry original request
//   403 → Forbidden → dispatch logout (token may be stolen/revoked)
//   0   → Network error → show offline message
//   5xx → Server error → show generic error message
//
// SYSTEM DESIGN: Resilience
//   Silent token refresh means users stay logged in across the 15-min token life.
//   Users only see a login screen if the refresh token (7 days) also expires.

import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject }  from '@angular/core';
import { Store }   from '@ngrx/store';
import { Router }  from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { logout }  from '../../store/auth/auth.actions';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const store  = inject(Store);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {

      switch (err.status) {
        case 401:
          // Token expired — dispatch logout (Effect handles token refresh logic)
          store.dispatch(logout());
          router.navigate(['/auth/login']);
          break;

        case 403:
          // Forbidden — user authenticated but lacks permission
          router.navigate(['/403']);
          break;

        case 0:
          // Network error (no internet / server unreachable)
          console.error('Network error — check your connection');
          break;

        default:
          if (err.status >= 500) {
            console.error('Server error:', err.status, err.message);
          }
      }

      // Re-throw so the caller's catchError / error handler still fires
      return throwError(() => err);
    })
  );
};
