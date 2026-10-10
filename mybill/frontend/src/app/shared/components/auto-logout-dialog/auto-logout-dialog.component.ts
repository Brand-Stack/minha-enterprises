import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface AutoLogoutDialogData {
  remainingSeconds: number;
}

@Component({
  selector: 'app-auto-logout-dialog',
  template: `
    <div class="ald-container">
      <div class="ald-icon-wrapper">
        <mat-icon class="ald-icon">timer</mat-icon>
      </div>
      <h2 class="ald-title">Session Expiring Soon</h2>
      <p class="ald-message">
        You have been inactive. For security reasons, your session will automatically expire in:
      </p>
      <div class="ald-countdown-badge">
        <span class="ald-time">{{ data.remainingSeconds }}</span>
        <span class="ald-unit">seconds</span>
      </div>
      <p class="ald-subtext">Click "Stay Logged In" to continue your session.</p>

      <div class="ald-actions">
        <button mat-flat-button color="primary" class="ald-btn-stay" (click)="stayLoggedIn()">
          <mat-icon>lock_open</mat-icon> Stay Logged In
        </button>
      </div>
    </div>
  `,
  styles: [`
    .ald-container {
      padding: 24px 16px 12px;
      text-align: center;
      font-family: 'Inter', system-ui, -apple-system, sans-serif;
      max-width: 380px;
    }
    .ald-icon-wrapper {
      width: 56px; height: 56px;
      margin: 0 auto 16px;
      background: #fef3c7;
      border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
    }
    .ald-icon {
      font-size: 32px; width: 32px; height: 32px;
      color: #d97706;
    }
    .ald-title {
      margin: 0 0 8px;
      font-size: 1.25rem; font-weight: 700;
      color: #0f172a;
    }
    .ald-message {
      margin: 0 0 16px;
      font-size: 0.875rem; color: #475569;
      line-height: 1.4;
    }
    .ald-countdown-badge {
      display: inline-flex; flex-direction: column; align-items: center;
      background: #f1f5f9; border: 1px solid #cbd5e1;
      padding: 12px 24px; border-radius: 12px;
      margin-bottom: 16px;
    }
    .ald-time {
      font-size: 2.25rem; font-weight: 800; color: #dc2626;
      line-height: 1; font-variant-numeric: tabular-nums;
    }
    .ald-unit {
      font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #64748b; margin-top: 4px;
    }
    .ald-subtext {
      margin: 0 0 20px;
      font-size: 0.8125rem; color: #64748b;
    }
    .ald-actions {
      display: flex; justify-content: center; gap: 12px;
    }
    .ald-btn-stay {
      min-width: 180px; padding: 8px 24px;
      font-weight: 600; border-radius: 8px;
    }
  `]
})
export class AutoLogoutDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<AutoLogoutDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AutoLogoutDialogData
  ) {}

  stayLoggedIn(): void {
    this.dialogRef.close(true);
  }
}
