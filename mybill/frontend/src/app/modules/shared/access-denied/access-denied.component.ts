import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-access-denied',
  template: `
    <div class="access-denied">
      <div class="access-denied-card">
        <mat-icon class="lock-icon">lock</mat-icon>
        <h1>403</h1>
        <h2>Access Denied</h2>
        <p>You do not have permission to view this page. If you believe this is a
          mistake, please contact your administrator.</p>
        <div class="actions">
          <button mat-raised-button class="btn-primary" (click)="goToDashboard()">
            <mat-icon>dashboard</mat-icon>
            <span>Go to Dashboard</span>
          </button>
          <button mat-button (click)="goBack()">
            <mat-icon>arrow_back</mat-icon>
            <span>Go Back</span>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .access-denied {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 70vh;
      padding: 24px;
    }
    .access-denied-card {
      text-align: center;
      max-width: 480px;
      background: #fff;
      border: 1px solid #E5E7EB;
      border-radius: 16px;
      padding: 48px 32px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
    }
    .lock-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      color: #EF4444;
      margin-bottom: 8px;
    }
    h1 {
      font-size: 48px;
      font-weight: 700;
      color: #1A1D2E;
      margin: 0;
    }
    h2 {
      font-size: 22px;
      font-weight: 600;
      color: #374151;
      margin: 4px 0 12px;
    }
    p {
      color: #6B7280;
      font-size: 14px;
      line-height: 1.6;
      margin-bottom: 24px;
    }
    .actions {
      display: flex;
      gap: 12px;
      justify-content: center;
      flex-wrap: wrap;
    }
    .btn-primary {
      background: #5B6FE8 !important;
      color: #fff !important;
    }
    button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
  `]
})
export class AccessDeniedComponent {
  constructor(private router: Router) {}

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  goBack(): void {
    window.history.back();
  }
}
