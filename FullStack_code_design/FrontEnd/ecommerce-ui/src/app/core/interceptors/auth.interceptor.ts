// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth Interceptor — Attach JWT to every request     ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Decorator / Interceptor (Chain of Responsibility)
//   Every outgoing HTTP request passes through this interceptor.
//   It attaches the Authorization header without touching individual services.
//
// Angular 21: Functional interceptors (HttpInterceptorFn — no class needed).
//
// SYSTEM DESIGN: Token Refresh (Silent Refresh)
//   When a 401 response is received (token expired):
//     1. Pause the failed request
//     2. Call /auth/refresh to get a new access token
//     3. Replay the original request with the new token
//   This is transparent to the user — no re-login required.
//   Handled in errorInterceptor to keep concerns separated.

import { HttpInterceptorFn } from '@angular/common/http';
import { inject }            from '@angular/core';
import { Store }             from '@ngrx/store';
import { selectAccessToken } from '../../store/auth/auth.selectors';
import { take, switchMap }   from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(Store);

  // Skip token attachment for auth endpoints (login, register, refresh)
  if (req.url.includes('/api/v1/auth')) {
    return next(req);
  }

  return store.select(selectAccessToken).pipe(
    take(1),  // read token ONCE — do not block request on store changes
    switchMap(token => {
      // Clone request — HttpRequest is immutable
      const authReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;
      return next(authReq);
    })
  );
};
