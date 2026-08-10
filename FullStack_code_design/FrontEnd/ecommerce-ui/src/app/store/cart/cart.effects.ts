// Cart Effects — placeholder (cart is synced to Redis via backend)
import { Injectable } from '@angular/core';
import { Actions }    from '@ngrx/effects';
import { inject }     from '@angular/core';

@Injectable()
export class CartEffects {
  private readonly actions$ = inject(Actions);
  // Cart HTTP effects will be implemented in the Cart module
}
