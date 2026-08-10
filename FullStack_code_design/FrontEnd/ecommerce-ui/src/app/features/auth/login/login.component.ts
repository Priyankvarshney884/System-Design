// ╔══════════════════════════════════════════════════════════════╗
// ║   Login Component — Angular 21 Signals + Reactive Forms      ║
// ╚══════════════════════════════════════════════════════════════╝
import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule }     from '@angular/material/input';
import { MatButtonModule }    from '@angular/material/button';
import { MatCardModule }      from '@angular/material/card';
import { CommonModule }       from '@angular/common';
import { RouterLink }         from '@angular/router';
import { loginRequest }       from '../../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink,
            MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  template: `
    <div class="login-container">
      <mat-card class="login-card">
        <mat-card-header>
          <mat-card-title>Sign In</mat-card-title>
          <mat-card-subtitle>Welcome back to E-Commerce</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <!-- Error message from NgRx store (via async pipe + OnPush) -->
          @if (error$ | async; as error) {
            <div class="error-banner">{{ error }}</div>
          }

          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email" placeholder="you@example.com" />
              @if (form.get('email')?.hasError('required') && form.get('email')?.touched) {
                <mat-error>Email is required</mat-error>
              }
              @if (form.get('email')?.hasError('email') && form.get('email')?.touched) {
                <mat-error>Enter a valid email</mat-error>
              }
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput formControlName="password" type="password" />
              @if (form.get('password')?.hasError('required') && form.get('password')?.touched) {
                <mat-error>Password is required</mat-error>
              }
            </mat-form-field>

            <button mat-flat-button color="primary" type="submit"
                    class="full-width"
                    [disabled]="form.invalid || (loading$ | async)">
              @if (loading$ | async) { Signing in... } @else { Sign In }
            </button>
          </form>
        </mat-card-content>

        <mat-card-actions>
          <a routerLink="/auth/register">Don't have an account? Register</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .login-container { display: flex; justify-content: center; align-items: center; min-height: 100vh; }
    .login-card      { width: 100%; max-width: 400px; padding: 16px; }
    .full-width      { width: 100%; margin-bottom: 12px; }
    .error-banner    { background: #fde8e8; color: #c53030; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; font-size: 14px; }
  `]
})
export class LoginComponent {
  private readonly store = inject(Store);
  private readonly fb    = inject(FormBuilder);

  protected readonly loading$ = this.store.select(selectAuthLoading);
  protected readonly error$   = this.store.select(selectAuthError);

  protected readonly form = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  onSubmit(): void {
    if (this.form.valid) {
      const { email, password } = this.form.getRawValue();
      // PATTERN: Command — dispatch action, Effect handles HTTP call
      this.store.dispatch(loginRequest({ email: email!, password: password! }));
    }
  }
}
