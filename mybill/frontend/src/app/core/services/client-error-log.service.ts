import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ErrorHandler } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Global error handler that logs uncaught errors to the backend.
 * Errors appear in the same logs folder as backend (billing.log, billing-error.log).
 */
@Injectable()
export class ClientErrorLogHandler extends ErrorHandler {
  constructor(private http: HttpClient) {
    super();
  }

  override handleError(error: unknown): void {
    // Always log to console for dev tools
    console.error('Global error:', error);

    // In production/local build, send to backend so it appears in log files
    if (environment.production && environment.apiUrl) {
      const body = {
        message: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
        url: typeof window !== 'undefined' && window.location ? window.location.href : undefined
      };
      this.http.post(`${environment.apiUrl}/log-client-error`, body).subscribe({
        next: () => {},
        error: () => {} // avoid recursive errors
      });
    }

    super.handleError(error);
  }
}
