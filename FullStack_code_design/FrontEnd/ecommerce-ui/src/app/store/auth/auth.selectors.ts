// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth State — Selectors                             ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Selector = Memoized Query
//   createSelector() returns a function that is memoized.
//   If inputs haven't changed, the selector returns the cached result.
//   Components only re-render when the slice they SELECT actually changes.
//
// SYSTEM DESIGN: Why selectors over direct store access?
//   - Decouples components from state shape (refactor state without touching components)
//   - Memoization prevents unnecessary re-renders
//   - Composable: build complex selectors from simpler ones
//
// Usage in component:
//   isLoggedIn = this.store.selectSignal(selectIsLoggedIn);  // Angular 21 Signal
//   user$ = this.store.select(selectCurrentUser);            // Observable

import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AuthState } from './auth.reducer';

// ── Feature Selector ─────────────────────────────────────────────
// Selects the entire 'auth' slice from root AppState
const selectAuthState = createFeatureSelector<AuthState>('auth');

// ── Leaf Selectors ────────────────────────────────────────────────
export const selectCurrentUser   = createSelector(selectAuthState, s => s.user);
export const selectAccessToken   = createSelector(selectAuthState, s => s.accessToken);
export const selectIsLoggedIn    = createSelector(selectAuthState, s => s.isLoggedIn);
export const selectAuthLoading   = createSelector(selectAuthState, s => s.isLoading);
export const selectAuthError     = createSelector(selectAuthState, s => s.error);

// ── Derived Selectors ─────────────────────────────────────────────
// Computed from other selectors — memoized automatically

/** Check if current user has a specific role */
export const selectHasRole = (role: string) =>
  createSelector(selectCurrentUser, user =>
    user?.roles?.includes(role) ?? false
  );

export const selectIsAdmin = selectHasRole('ADMIN');

/** User display name — null-safe */
export const selectUserDisplayName = createSelector(
  selectCurrentUser,
  user => user?.name ?? 'Guest'
);
