// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth State — Reducer                               ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Reducer = Pure Function
//   (currentState, action) => newState
//   NO side effects, NO async, NO mutations — always returns a new object.
//
// SYSTEM DESIGN: Immutability
//   NgRx (like Redux) requires immutable state updates.
//   Spreading {...state, field: newValue} creates a new object reference.
//   Angular's ChangeDetection can detect changes via reference equality check
//   → O(1) instead of O(n) deep comparison.

import { createReducer, on } from '@ngrx/store';
import { AuthUser, loginFailure, loginRequest, loginSuccess,
         logout, logoutSuccess, refreshTokenSuccess,
         registerFailure, registerRequest, registerSuccess,
         restoreSession } from './auth.actions';

// ── State Shape ──────────────────────────────────────────────────
export interface AuthState {
  user:         AuthUser | null;
  accessToken:  string | null;
  isLoading:    boolean;
  error:        string | null;
  isLoggedIn:   boolean;
}

// ── Initial State ────────────────────────────────────────────────
const initialState: AuthState = {
  user:        null,
  accessToken: null,
  isLoading:   false,
  error:       null,
  isLoggedIn:  false,
};

// ── Reducer ──────────────────────────────────────────────────────
export const authReducer = createReducer(
  initialState,

  // Login flow
  on(loginRequest, state => ({
    ...state,
    isLoading: true,
    error: null,           // clear previous errors on new attempt
  })),

  on(loginSuccess, (state, { user, accessToken }) => ({
    ...state,
    user,
    accessToken,
    isLoading:  false,
    isLoggedIn: true,
    error:      null,
  })),

  on(loginFailure, (state, { error }) => ({
    ...state,
    isLoading: false,
    error,
  })),

  // Register flow (same shape as login — server returns tokens on register)
  on(registerRequest, state => ({ ...state, isLoading: true, error: null })),
  on(registerSuccess, (state, { user, accessToken }) => ({
    ...state, user, accessToken, isLoading: false, isLoggedIn: true, error: null,
  })),
  on(registerFailure, (state, { error }) => ({ ...state, isLoading: false, error })),

  // Logout — clear all auth state
  on(logout, state => ({ ...state, isLoading: true })),
  on(logoutSuccess, () => ({ ...initialState })),  // full reset

  // Silent token refresh — only updates the access token
  on(refreshTokenSuccess, (state, { accessToken }) => ({
    ...state,
    accessToken,
  })),

  // Page reload: restore session from localStorage (handled in Effect)
  on(restoreSession, state => ({ ...state, isLoading: true })),
);
