// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth State — Effects (Side Effects)                ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Effects = Command Handler
//   Effects listen for Actions and perform async side effects (HTTP calls).
//   They dispatch new Actions on success/failure → Reducer updates state.
//
// Data flow:
//   Component dispatches loginRequest
//     → Effect catches it → calls AuthApiService
//       → on success: dispatches loginSuccess → Reducer stores user
//       → on failure: dispatches loginFailure → Reducer stores error
//
// SYSTEM DESIGN: Effects isolate ALL async logic from components.
//   Components only dispatch/select — no HTTP calls in components.

import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';

import { AuthApiService } from '../../core/services/auth-api.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import * as AuthActions from './auth.actions';

@Injectable()
export class AuthEffects {

  private readonly actions$      = inject(Actions);
  private readonly authApi       = inject(AuthApiService);
  private readonly tokenStorage  = inject(TokenStorageService);

  // ── Login Effect ────────────────────────────────────────────────
  // exhaustMap: if user double-clicks login, ignores subsequent clicks
  // until the first request completes — prevents duplicate API calls
  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loginRequest),
      exhaustMap(({ email, password }) =>
        this.authApi.login(email, password).pipe(
          map(response => {
            // Persist tokens to localStorage for session restore on refresh
            this.tokenStorage.saveTokens(response.accessToken, response.refreshToken);
            return AuthActions.loginSuccess({
              user:         response.user,
              accessToken:  response.accessToken,
              refreshToken: response.refreshToken,
            });
          }),
          catchError(err =>
            of(AuthActions.loginFailure({ error: err.error?.detail ?? 'Login failed' }))
          )
        )
      )
    )
  );

  // ── Register Effect ──────────────────────────────────────────────
  register$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.registerRequest),
      exhaustMap(({ name, email, password }) =>
        this.authApi.register(name, email, password).pipe(
          map(response => {
            this.tokenStorage.saveTokens(response.accessToken, response.refreshToken);
            return AuthActions.registerSuccess({
              user:         response.user,
              accessToken:  response.accessToken,
              refreshToken: response.refreshToken,
            });
          }),
          catchError(err =>
            of(AuthActions.registerFailure({ error: err.error?.detail ?? 'Registration failed' }))
          )
        )
      )
    )
  );

  // ── Logout Effect ────────────────────────────────────────────────
  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logout),
      exhaustMap(() =>
        this.authApi.logout().pipe(
          map(() => AuthActions.logoutSuccess()),
          catchError(() => of(AuthActions.logoutSuccess())) // always succeed locally
        )
      )
    )
  );

  // ── Clear tokens on logout ───────────────────────────────────────
  // tap = side effect that doesn't change the action stream
  clearTokensOnLogout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logoutSuccess),
      tap(() => this.tokenStorage.clearTokens()),
    ),
    { dispatch: false }  // this effect does NOT dispatch a new action
  );

  // ── Restore Session on App Load ──────────────────────────────────
  // SYSTEM DESIGN: Reads token from localStorage, validates it,
  // restores user state without forcing re-login on every page refresh.
  restoreSession$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.restoreSession),
      exhaustMap(() => {
        const token = this.tokenStorage.getAccessToken();
        if (!token) return of(AuthActions.logoutSuccess());
        return this.authApi.getMe().pipe(
          map(user => AuthActions.loginSuccess({
            user,
            accessToken:  token,
            refreshToken: this.tokenStorage.getRefreshToken() ?? '',
          })),
          catchError(() => {
            this.tokenStorage.clearTokens();
            return of(AuthActions.logoutSuccess());
          })
        );
      })
    )
  );
}
