// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth Guard — Protect authenticated routes          ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Interceptor / Guard (Chain of Responsibility)
//   canActivate runs before any route component is loaded.
//   Returns true → proceed. Returns UrlTree → redirect.
//
// Angular 21: Functional guards (no class, no Injectable decorator).
//   inject() works inside functional guards — DI fully supported.
//
// SYSTEM DESIGN: Route-level access control
//   Never rely on UI guards alone — backend MUST enforce auth too.
//   Guard = UX convenience. Backend JWT validation = real security.

import { inject }       from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store }        from '@ngrx/store';
import { map, take }    from 'rxjs';
import { selectIsLoggedIn } from '../../store/auth/auth.selectors';

export const authGuard: CanActivateFn = () => {
  const store  = inject(Store);
  const router = inject(Router);

  return store.select(selectIsLoggedIn).pipe(
    take(1),   // take 1 = unsubscribe immediately — no memory leaks
    map(isLoggedIn => {
      if (isLoggedIn) return true;
      // Redirect to login with returnUrl — user lands back after login
      return router.createUrlTree(['/auth/login'], {
        queryParams: { returnUrl: router.url }
      });
    })
  );
};
