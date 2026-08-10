import { Routes } from '@angular/router';
export const CART_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./cart/cart.component').then(m => m.CartComponent) },
];
