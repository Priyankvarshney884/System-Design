// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth State — Actions                               ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Command Pattern via NgRx Actions
//   Each action is a self-describing command: "[Source] Event Name"
//   Source prefix tells you WHERE it was dispatched from.
//
// Action naming convention:  "[Feature] Verb Noun"
//   [Auth] Login Request     — user initiated
//   [Auth] Login Success     — API responded OK
//   [Auth] Login Failure     — API responded with error
//
// INTERVIEW TIP: Actions are the "what happened" — pure data, no logic.
// Logic lives in Reducers (sync state update) and Effects (async side effects).

import { createAction, props } from '@ngrx/store';

// ── Login ────────────────────────────────────────────────────────
export const loginRequest = createAction(
  '[Auth] Login Request',
  props<{ email: string; password: string }>()
);

export const loginSuccess = createAction(
  '[Auth] Login Success',
  props<{ user: AuthUser; accessToken: string; refreshToken: string }>()
);

export const loginFailure = createAction(
  '[Auth] Login Failure',
  props<{ error: string }>()
);

// ── Register ─────────────────────────────────────────────────────
export const registerRequest = createAction(
  '[Auth] Register Request',
  props<{ name: string; email: string; password: string }>()
);

export const registerSuccess = createAction(
  '[Auth] Register Success',
  props<{ user: AuthUser; accessToken: string; refreshToken: string }>()
);

export const registerFailure = createAction(
  '[Auth] Register Failure',
  props<{ error: string }>()
);

// ── Logout ────────────────────────────────────────────────────────
export const logout = createAction('[Auth] Logout');
export const logoutSuccess = createAction('[Auth] Logout Success');

// ── Token Refresh ─────────────────────────────────────────────────
// SYSTEM DESIGN: Access token expires in 15 min.
// On 401, interceptor dispatches this → Effect silently refreshes.
export const refreshTokenRequest = createAction('[Auth] Refresh Token Request');
export const refreshTokenSuccess = createAction(
  '[Auth] Refresh Token Success',
  props<{ accessToken: string }>()
);
export const refreshTokenFailure = createAction(
  '[Auth] Refresh Token Failure',
  props<{ error: string }>()
);

// ── Restore Session (on page load) ────────────────────────────────
export const restoreSession = createAction('[Auth] Restore Session');

// ── Types ─────────────────────────────────────────────────────────
export interface AuthUser {
  id:        string;
  name:      string;
  email:     string;
  roles:     string[];   // ['USER', 'ADMIN']
  avatarUrl: string | null;
}
