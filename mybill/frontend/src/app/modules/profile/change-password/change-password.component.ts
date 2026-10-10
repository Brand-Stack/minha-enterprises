import { Component } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-change-password',
  template: `
    <div class="change-password">
      <div class="card">
        <h1>Change Password</h1>

        <div *ngIf="submittedSuccessfully" class="success-state">
          <mat-icon class="success-icon">check_circle</mat-icon>
          <p class="success-title">Password changed successfully!</p>
          <p class="success-subtitle">You can update your password again anytime from the sidebar.</p>
          <button mat-raised-button class="btn-primary" type="button" (click)="goToDashboard()">
            Go to Dashboard
          </button>
        </div>

        <ng-container *ngIf="!submittedSuccessfully">
          <p class="subtitle">Update the password for your account.</p>

          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current Password</mat-label>
              <input matInput type="password" formControlName="currentPassword" autocomplete="current-password" />
              <mat-error *ngIf="form.get('currentPassword')?.invalid && form.get('currentPassword')?.touched">
                Current password is required
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password</mat-label>
              <input matInput type="password" formControlName="newPassword" autocomplete="new-password" />
              <mat-error *ngIf="form.get('newPassword')?.hasError('required') && form.get('newPassword')?.touched">
                New password is required
              </mat-error>
              <mat-error *ngIf="form.get('newPassword')?.hasError('minlength') && form.get('newPassword')?.touched">
                Must be at least 6 characters
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm New Password</mat-label>
              <input matInput type="password" formControlName="confirmPassword" autocomplete="new-password" />
              <mat-error *ngIf="form.get('confirmPassword')?.hasError('required') && form.get('confirmPassword')?.touched">
                Please confirm your new password
              </mat-error>
            </mat-form-field>

            <div class="mismatch" *ngIf="form.hasError('mismatch') && form.get('confirmPassword')?.touched">
              New password and confirmation do not match.
            </div>

            <div class="actions">
              <button mat-raised-button class="btn-primary" type="submit" [disabled]="form.invalid || saving">
                {{ saving ? 'Updating...' : 'Update Password' }}
              </button>
            </div>
          </form>
        </ng-container>
      </div>
    </div>
  `,
  styles: [`
    .change-password { max-width: 480px; margin: 0 auto; }
    .card {
      background: #fff; border: 1px solid #E5E7EB; border-radius: 12px; padding: 32px;
    }
    h1 { font-size: 22px; font-weight: 600; color: #1A1D2E; margin: 0; }
    .subtitle { color: #6B7280; font-size: 13px; margin: 4px 0 20px; }
    .full-width { width: 100%; }
    .actions { display: flex; justify-content: flex-end; margin-top: 8px; }
    .btn-primary { background: #5B6FE8 !important; color: #fff !important; }
    .mismatch { color: #EF4444; font-size: 13px; margin: -8px 0 12px; }
    .success-state {
      text-align: center;
      padding: 24px 0 8px;
    }
    .success-icon {
      font-size: 56px;
      width: 56px;
      height: 56px;
      color: #10B981;
      margin-bottom: 16px;
    }
    .success-title {
      font-size: 18px;
      font-weight: 600;
      color: #1A1D2E;
      margin: 0 0 8px;
    }
    .success-subtitle {
      color: #6B7280;
      font-size: 14px;
      margin: 0 0 24px;
    }
  `]
})
export class ChangePasswordComponent {
  form: FormGroup;
  saving = false;
  submittedSuccessfully = false;

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private toast: ToastService,
    private router: Router
  ) {
    this.form = this.fb.group(
      {
        currentPassword: ['', Validators.required],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: ['', Validators.required]
      },
      { validators: [this.passwordsMatch] }
    );
  }

  private passwordsMatch(group: AbstractControl): ValidationErrors | null {
    const newPwd = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return newPwd && confirm && newPwd !== confirm ? { mismatch: true } : null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.api.post('/auth/change-password', this.form.value).subscribe({
      next: () => {
        this.saving = false;
        this.toast.success('Success', 'Password changed successfully');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.toast.error('Error', err?.error?.message || 'Failed to change password');
        this.saving = false;
      }
    });
  }

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }
}
