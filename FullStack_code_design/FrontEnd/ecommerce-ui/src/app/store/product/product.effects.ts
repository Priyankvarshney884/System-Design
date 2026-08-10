// Product Effects — placeholder
import { Injectable } from '@angular/core';
import { Actions }    from '@ngrx/effects';
import { inject }     from '@angular/core';

@Injectable()
export class ProductEffects {
  private readonly actions$ = inject(Actions);
  // Product HTTP effects will be implemented in the Product module
}
