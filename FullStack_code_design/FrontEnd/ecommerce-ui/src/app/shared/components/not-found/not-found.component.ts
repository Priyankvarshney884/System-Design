import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div style="text-align:center; padding: 80px 20px;">
      <h1 style="font-size: 96px; margin: 0; color: #e5e7eb;">404</h1>
      <h2>Page Not Found</h2>
      <p>The page you're looking for doesn't exist.</p>
      <a routerLink="/products">Go to Products</a>
    </div>
  `
})
export class NotFoundComponent {}
