// ╔══════════════════════════════════════════════════════════════╗
// ║           Token Storage Service                              ║
// ╚══════════════════════════════════════════════════════════════╝
//
// SYSTEM DESIGN: Where to store JWT tokens?
//
//   Option A: localStorage  ← we use this for access + refresh token
//     + Survives page refresh
//     - Vulnerable to XSS (mitigated by CSP headers server-side)
//
//   Option B: HttpOnly Cookie
//     + XSS-safe (JS cannot read it)
//     - CSRF attack surface (mitigated by SameSite=Strict)
//     - Works poorly for mobile/native apps
//
//   Option C: In-memory (NgRx store only)
//     + Most secure (XSS cannot steal it)
//     - Lost on page refresh → user must re-login
//
//   DECISION: localStorage for refresh token (long-lived, needed on refresh),
//   NgRx store for access token (short-lived 15min, never written to storage).
//   On page load: read refresh token → call /auth/me → restore NgRx state.

import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TokenStorageService {

  private readonly ACCESS_KEY  = 'access_token';
  private readonly REFRESH_KEY = 'refresh_token';

  saveTokens(accessToken: string, refreshToken: string): void {
    // Access token also saved for session restore on hard refresh
    localStorage.setItem(this.ACCESS_KEY,  accessToken);
    localStorage.setItem(this.REFRESH_KEY, refreshToken);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  clearTokens(): void {
    localStorage.removeItem(this.ACCESS_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
  }
}
