import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ModulePermission } from '../models/permission.model';

export interface AuthRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: string;
  employeeId: string;
  employeeName?: string;
  categoryId?: string;
  categoryName?: string;
  /** True for ADMIN users who implicitly have unrestricted access. */
  admin?: boolean;
  /** Full effective permission matrix keyed by module key. */
  permissions?: { [moduleKey: string]: ModulePermission };
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private currentUserSubject = new BehaviorSubject<AuthResponse | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(credentials: AuthRequest): Observable<AuthResponse> {
    // Dynamically get API URL based on current hostname
    const apiUrl = this.getApiUrl();
    console.log('AuthService - Attempting login to:', `${apiUrl}/auth/login`);
    return this.http.post<AuthResponse>(`${apiUrl}/auth/login`, credentials)
      .pipe(
        tap(response => {
          console.log('AuthService - Login response received:', response);
          
          if (!response || !response.token) {
            console.error('AuthService - Invalid response: missing token');
            throw new Error('Invalid response from server: missing token');
          }
          
          console.log('AuthService - Storing token and user:', response);
          localStorage.setItem('token', response.token);
          localStorage.setItem('user', JSON.stringify(response));
          this.currentUserSubject.next(response);
          
          // Verify token was stored
          const storedToken = localStorage.getItem('token');
          console.log('AuthService - Token stored successfully:', !!storedToken);
          console.log('AuthService - isAuthenticated:', this.isAuthenticated());
        })
      );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getCurrentUser(): AuthResponse | null {
    return this.currentUserSubject.value;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  private getStoredUser(): AuthResponse | null {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  }

  private getApiUrl(): string {
    // If in browser, use window.location.hostname
    if (typeof window !== 'undefined' && window.location) {
      const hostname = window.location.hostname;
      // Always use HTTP (not HTTPS) for API calls
      return `http://${hostname}:1104/api`;
    }
    // Fallback to environment config
    return environment.apiUrl;
  }
}

