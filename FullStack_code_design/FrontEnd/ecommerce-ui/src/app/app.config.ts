// ╔══════════════════════════════════════════════════════════════╗
// ║          Application-Level Configuration — Angular 21        ║
// ╚══════════════════════════════════════════════════════════════╝
//
// This is the Angular equivalent of Spring Boot's @Configuration.
// All global providers are declared here ONCE, available everywhere.
//
// Angular 21 Modern Features used here:
//  ✅ Standalone Components (no NgModule)
//  ✅ Functional HTTP Interceptors
//  ✅ provideRouter with fine-grained features
//  ✅ NgRx Signal Store integration
//  ✅ provideBrowserGlobalErrorListeners (new in Angular 19+)

import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import {
  provideRouter,
  withPreloading,
  PreloadAllModules,
  withComponentInputBinding,
  withViewTransitions
} from '@angular/router';
import { provideHttpClient, withInterceptors, withFetch } from '@angular/common/http';
import { provideAnimationsAsync }                         from '@angular/platform-browser/animations/async';
import { provideStore }                                   from '@ngrx/store';
import { provideEffects }                                 from '@ngrx/effects';
import { provideRouterStore }                             from '@ngrx/router-store';
import { provideStoreDevtools }                           from '@ngrx/store-devtools';

import { routes }           from './app.routes';
import { reducers }         from './store/app.state';
import { AuthEffects }      from './store/auth/auth.effects';
import { CartEffects }      from './store/cart/cart.effects';
import { ProductEffects }   from './store/product/product.effects';
import { authInterceptor }  from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [

    // ── Angular 21: Global error listener (replaces ErrorHandler in many cases)
    provideBrowserGlobalErrorListeners(),

    // ── Routing ───────────────────────────────────────────────────
    // withPreloading: after initial load, Angular silently fetches other lazy chunks
    // withComponentInputBinding: route params bind directly as @Input() on components
    // withViewTransitions: native browser View Transitions API for page animations
    provideRouter(
      routes,
      withPreloading(PreloadAllModules),
      withComponentInputBinding(),
      withViewTransitions()
    ),

    // ── HTTP Client ───────────────────────────────────────────────
    // withFetch: uses native Fetch API instead of XHR (Angular 21 default)
    // withInterceptors: functional interceptors — executed in ORDER listed:
    //   1. authInterceptor   → attaches Bearer token
    //   2. errorInterceptor  → handles 401/403/5xx globally
    provideHttpClient(
      withFetch(),
      withInterceptors([
        authInterceptor,
        errorInterceptor,
      ])
    ),

    // ── Animations (async = loaded lazily, not in main bundle) ───
    provideAnimationsAsync(),

    // ── NgRx Store ────────────────────────────────────────────────
    // PATTERN: Redux — unidirectional data flow
    // Action → Reducer → State → Selector → Component (read-only)
    provideStore(reducers),

    // ── NgRx Effects ─────────────────────────────────────────────
    // Effects handle side effects (HTTP calls) triggered by Actions
    // PATTERN: Command — each action is a command; effect executes it
    provideEffects([AuthEffects, CartEffects, ProductEffects]),

    // ── NgRx Router Store ────────────────────────────────────────
    // Syncs Angular router state into NgRx store
    // Enables selecting route params/url from store selectors
    provideRouterStore(),

    // ── NgRx DevTools (dev only) ─────────────────────────────────
    // Chrome DevTools extension: time-travel debugging, action replay
    provideStoreDevtools({
      maxAge: 25,
      logOnly: !isDevMode(),
      connectInZone: true,
    }),
  ],
};
