// ╔══════════════════════════════════════════════════════════════╗
// ║           NgRx Root State — App State Shape                  ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Redux / Flux
//   Single source of truth — entire app state lives here.
//   Components READ from store via selectors (never mutate directly).
//   Components WRITE via dispatching actions.
//
// State slices:
//   auth     — current user, JWT tokens, login status
//   cart     — cart items, total, item count
//   products — product list, selected product, search filters
//
// SYSTEM DESIGN: Why centralised state?
//   - Predictable: given same state + action → same new state (pure functions)
//   - Debuggable: Redux DevTools shows every state change with time-travel
//   - Testable: reducers are pure functions — easy to unit test
//   - Scalable: any component can read any slice without prop-drilling

import { ActionReducerMap, MetaReducer } from '@ngrx/store';
import { authReducer, AuthState }           from './auth/auth.reducer';
import { cartReducer, CartState }           from './cart/cart.reducer';
import { productReducer, ProductState }     from './product/product.reducer';

// ── Root State Interface ────────────────────────────────────────
export interface AppState {
  auth:     AuthState;
  cart:     CartState;
  products: ProductState;
}

// ── Root Reducer Map ────────────────────────────────────────────
export const reducers: ActionReducerMap<AppState> = {
  auth:     authReducer,
  cart:     cartReducer,
  products: productReducer,
};

// ── Meta Reducers ───────────────────────────────────────────────
// Meta-reducers wrap every reducer — useful for logging, hydration
// SYSTEM DESIGN: hydrationMetaReducer restores state from localStorage
// on page refresh (e.g. cart items survive browser close)
export const metaReducers: MetaReducer<AppState>[] = [];
