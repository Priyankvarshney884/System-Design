// ╔══════════════════════════════════════════════════════════════╗
// ║     Role Guard — RBAC at Route Level                         ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Factory Function returning a Guard
//   roleGuard('ADMIN') returns a CanActivateFn configured for that role.
//   Same guard logic, different role — no code duplication.
//
// SYSTEM DESIGN: Role-Based Access Control (RBAC)
//   Roles come from the JWT token (decoded server-side, stored in NgRx).
//   Guard checks: does current user's roles array include the required role?
//   On mismatch → redirect to /403 (not /login — user IS authenticated).

import { inject }       from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store }        from '@ngrx/store';
import { map, take }    from 'rxjs';
import { selectHasRole } from '../../store/auth/auth.selectors';

export const roleGuard = (requiredRole: string): CanActivateFn => () => {
  const store  = inject(Store);
  const router = inject(Router);

  return store.select(selectHasRole(requiredRole)).pipe(
    take(1),
    map(hasRole => hasRole || router.createUrlTree(['/403']))
  );
};
