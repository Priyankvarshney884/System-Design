// Redirect already-logged-in users away from /auth/login
import { inject }    from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store }     from '@ngrx/store';
import { map, take } from 'rxjs';
import { selectIsLoggedIn } from '../../store/auth/auth.selectors';

export const noAuthGuard: CanActivateFn = () => {
  const store  = inject(Store);
  const router = inject(Router);

  return store.select(selectIsLoggedIn).pipe(
    take(1),
    map(isLoggedIn =>
      isLoggedIn ? router.createUrlTree(['/products']) : true
    )
  );
};
