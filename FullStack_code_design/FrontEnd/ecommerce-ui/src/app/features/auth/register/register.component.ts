import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Store }              from '@ngrx/store';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule }     from '@angular/material/input';
import { MatButtonModule }    from '@angular/material/button';
import { MatCardModule }      from '@angular/material/card';
import { CommonModule }       from '@angular/common';
import { RouterLink }         from '@angular/router';
import { registerRequest }    from '../../../store/auth/auth.actions';
import { selectAuthLoading, selectAuthError } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, ReactiveFormsModule, RouterLink,
            MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  template: `
    <div class="register-container">
      <mat-card class="register-card">
        <mat-card-header>
          <mat-card-title>Create Account</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (error$ | async; as error) {
            <div class="error-banner">{{ error }}</div>
          }
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Full Name</mat-label>
              <input matInput formControlName="name" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Email</mat-label>
              <input matInput formControlName="email" type="email" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Password</mat-label>
              <input matInput formControlName="password" type="password" />
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit"
                    class="full-width" [disabled]="form.invalid || (loading$ | async)">
              @if (loading$ | async) { Creating... } @else { Create Account }
            </button>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <a routerLink="/auth/login">Already have an account? Sign in</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .register-container { display: flex; justify-content: center; align-items: center; min-height: 100vh; }
    .register-card { width: 100%; max-width: 400px; padding: 16px; }
    .full-width { width: 100%; margin-bottom: 12px; }
    .error-banner { background: #fde8e8; color: #c53030; padding: 8px 12px; border-radius: 4px; margin-bottom: 12px; font-size: 14px; }
  `]
})
export class RegisterComponent {
  private readonly store = inject(Store);
  private readonly fb    = inject(FormBuilder);
  protected readonly loading$ = this.store.select(selectAuthLoading);
  protected readonly error$   = this.store.select(selectAuthError);
  protected readonly form = this.fb.group({
    name:     ['', Validators.required],
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  onSubmit(): void {
    if (this.form.valid) {
      const { name, email, password } = this.form.getRawValue();
      this.store.dispatch(registerRequest({ name: name!, email: email!, password: password! }));
    }
  }
}
