// ╔══════════════════════════════════════════════════════════════╗
// ║           Root Application Component — Angular 21            ║
// ╚══════════════════════════════════════════════════════════════╝
//
// Angular 21 Modern Features demonstrated here:
//  ✅ Signals — reactive state without RxJS boilerplate
//  ✅ ChangeDetectionStrategy.OnPush — fine-grained CD
//  ✅ inject() function — no constructor DI
//  ✅ Standalone component (no NgModule)
//
// DESIGN PATTERN: Shell / Layout Component
//   AppComponent is a thin shell — only <router-outlet>.
//   All real UI lives in lazy-loaded feature components.
//   Keeps the initial bundle as small as possible.

import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { RouterOutlet, Router, NavigationStart, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map, merge } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Global navigation loading indicator
         Shows a progress bar at the top during route transitions.
         SYSTEM DESIGN: perceived performance — user sees instant feedback -->
    @if (isNavigating()) {
      <mat-progress-bar mode="indeterminate" class="nav-loader" />
    }

    <!-- All routed feature components render here -->
    <router-outlet />
  `,
  styles: [`
    .nav-loader {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 9999;
    }
  `]
})
export class App {
  // ── inject() — Angular 21's preferred DI style (no constructor needed) ──
  private readonly router = inject(Router);

  /**
   * ANGULAR 21 SIGNAL: isNavigating
   * Derived from router events using toSignal() — bridges RxJS → Signals.
   *
   * toSignal() subscribes automatically and unsubscribes when component
   * is destroyed (no manual takeUntil/unsubscribe needed).
   *
   * SYSTEM DESIGN: fine-grained reactivity — only this signal triggers
   * re-render of the progress bar, not the entire component tree.
   */
  protected readonly isNavigating = toSignal(
    this.router.events.pipe(
      map(event => event instanceof NavigationStart),
    ),
    { initialValue: false }
  );
}
