import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, from } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../../shared/components/toast/toast.service';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  constructor(
    private router: Router,
    private authService: AuthService,
    private toastService: ToastService
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<any> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          console.log('ErrorInterceptor - 401 Unauthorized, clearing auth and redirecting to login');
          this.authService.logout();
          if (!this.router.url.includes('/login')) {
            this.router.navigate(['/login'], { queryParams: { expired: 'true' } });
          }
        }

        if (error.status === 403) {
          if (this.shouldSuppress403Toast()) {
            return throwError(() => error);
          }
          return from(this.extract403Message(error)).pipe(
            switchMap((msg) => {
              this.toastService.error('Access Denied', msg);
              return throwError(() => error);
            })
          );
        }

        return throwError(() => error);
      })
    );
  }

  private async extract403Message(error: HttpErrorResponse): Promise<string> {
    const fallback = 'You do not have permission to perform this action.';
    try {
      if (error.error instanceof Blob) {
        const text = await error.error.text();
        const json = JSON.parse(text);
        return json?.message || fallback;
      }
      return error.error?.message || fallback;
    } catch {
      return fallback;
    }
  }

  /** Skip 403 toasts during logout/login when stale or unauthenticated requests fail. */
  private shouldSuppress403Toast(): boolean {
    const url = this.router.url || '';
    if (url.includes('/login')) {
      return true;
    }
    return !this.authService.isAuthenticated();
  }
}

