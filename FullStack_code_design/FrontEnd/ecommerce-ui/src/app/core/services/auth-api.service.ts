// ╔══════════════════════════════════════════════════════════════╗
// ║           Auth API Service — HTTP calls to backend           ║
// ╚══════════════════════════════════════════════════════════════╝
//
// DESIGN PATTERN: Repository / Service Layer
//   All auth HTTP calls go through this single service.
//   Components and Effects never call HttpClient directly.
//   Swapping the backend URL or mocking for tests = change ONE file.
//
// SYSTEM DESIGN: API contract
//   Responses match the backend's ApiResponse<T> wrapper:
//   { success: true, data: T, timestamp: string }

import { inject, Injectable } from '@angular/core';
import { HttpClient }         from '@angular/common/http';
import { Observable, map }    from 'rxjs';
import { environment }        from '../../../environments/environment';
import { AuthUser }           from '../../store/auth/auth.actions';

interface AuthResponse {
  user:         AuthUser;
  accessToken:  string;
  refreshToken: string;
}

interface ApiResponse<T> {
  success: boolean;
  data:    T;
  message: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthApiService {

  private readonly http    = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.baseUrl}/login`, { email, password })
      .pipe(map(r => r.data));
  }

  register(name: string, email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${this.baseUrl}/register`, { name, email, password })
      .pipe(map(r => r.data));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/logout`, {});
  }

  /** Called on app load to validate stored token and get current user */
  getMe(): Observable<AuthUser> {
    return this.http
      .get<ApiResponse<AuthUser>>(`${this.baseUrl}/me`)
      .pipe(map(r => r.data));
  }

  refreshToken(refreshToken: string): Observable<{ accessToken: string }> {
    return this.http
      .post<ApiResponse<{ accessToken: string }>>(`${this.baseUrl}/refresh`, { refreshToken })
      .pipe(map(r => r.data));
  }
}
