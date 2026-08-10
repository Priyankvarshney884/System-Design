// ╔══════════════════════════════════════════════════════════════╗
// ║          Application Routes — Lazy-Loaded Feature Modules    ║
// ╚══════════════════════════════════════════════════════════════╝
//
// SYSTEM DESIGN: Lazy Loading = Code Splitting
//   Each feature is a separate JS chunk loaded on demand.
//   User visiting /products does NOT download the auth or admin bundle.
//   Initial bundle size stays small → faster first contentful paint.
//
// PATTERN: Guard Chain (Chain of Responsibility)
//   canActivate runs guards left-to-right. First false = blocks navigation.
//
// Route naming mirrors backend module structure:
//   /auth     → AuthModule   (login, register, forgot-password)
//   /products → ProductModule (catalog, search, detail)
//   /cart     → CartModule
//   /orders   → OrderModule
//   /profile  → ProfileModule
//   /admin    → AdminModule  (ADMIN role only)

import { Routes } from '@angular/router';
import { authGuard }   from './core/guards/auth.guard';
import { roleGuard }   from './core/guards/role.guard';
import { noAuthGuard } from './core/guards/no-auth.guard';

export const routes: Routes = [

  // Default redirect
  { path: '', redirectTo: '/products', pathMatch: 'full' },

  // ── Auth (public — redirect away if already logged in) ────────
  {
    path: 'auth',
    canActivate: [noAuthGuard],
    loadChildren: () =>
      import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES),
  },

  // ── Product Catalog (public) ──────────────────────────────────
  {
    path: 'products',
    loadChildren: () =>
      import('./features/product/product.routes').then(m => m.PRODUCT_ROUTES),
  },

  // ── Shopping Cart (requires login) ───────────────────────────
  {
    path: 'cart',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/cart/cart.routes').then(m => m.CART_ROUTES),
  },

  // ── Orders (requires login) ──────────────────────────────────
  {
    path: 'orders',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/order/order.routes').then(m => m.ORDER_ROUTES),
  },

  // ── User Profile (requires login) ────────────────────────────
  {
    path: 'profile',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/profile/profile.routes').then(m => m.PROFILE_ROUTES),
  },

  // ── Admin Dashboard (requires ADMIN role) ────────────────────
  // SYSTEM DESIGN: RBAC enforced at route level
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadChildren: () =>
      import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES),
  },

  // ── 404 Fallback ─────────────────────────────────────────────
  {
    path: '**',
    loadComponent: () =>
      import('./shared/components/not-found/not-found.component')
        .then(m => m.NotFoundComponent),
  },
];
