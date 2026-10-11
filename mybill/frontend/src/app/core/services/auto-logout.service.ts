import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { fromEvent, merge, Subscription } from 'rxjs';
import { throttleTime } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { ApiService } from './api.service';
import { AutoLogoutDialogComponent } from '../../shared/components/auto-logout-dialog/auto-logout-dialog.component';

@Injectable({
  providedIn: 'root'
})
export class AutoLogoutService implements OnDestroy {
  private autoLogoutMinutes = 15;
  private readonly warningDurationSeconds = 60;
  private lastActivityTime = Date.now();
  private timerId: any = null;
  private activitySub?: Subscription;
  private authSub?: Subscription;
  private dialogRef: MatDialogRef<AutoLogoutDialogComponent> | null = null;
  private isInitialized = false;

  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private router: Router,
    private dialog: MatDialog,
    private ngZone: NgZone
  ) {}

  public init(): void {
    if (this.isInitialized) {
      return;
    }
    this.isInitialized = true;

    // Fetch initial timeout from Company Settings
    this.fetchCompanySettings();

    // Listen to authentication state changes
    this.authSub = this.authService.currentUser$.subscribe((user) => {
      if (user) {
        this.startTimer();
      } else {
        this.stopTimer();
      }
    });

    if (this.authService.isAuthenticated()) {
      this.startTimer();
    }
  }

  public updateTimeoutMinutes(minutes: number): void {
    if (minutes && minutes > 0) {
      this.autoLogoutMinutes = minutes;
      this.resetTimer();
    }
  }

  public fetchCompanySettings(): void {
    if (!this.authService.isAuthenticated()) return;
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        if (settings && settings.autoLogoutMinutes && settings.autoLogoutMinutes > 0) {
          this.autoLogoutMinutes = settings.autoLogoutMinutes;
        }
      },
      error: () => {
        // Fallback to default 15 minutes
      }
    });
  }

  public resetTimer(): void {
    this.lastActivityTime = Date.now();
    if (this.dialogRef) {
      this.dialogRef.close(true);
      this.dialogRef = null;
    }
  }

  private startTimer(): void {
    this.stopTimer();
    this.lastActivityTime = Date.now();

    // Register activity listeners outside Angular zone for maximum performance
    this.ngZone.runOutsideAngular(() => {
      const events = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'wheel', 'click'];
      const streams = events.map((ev) => fromEvent(window, ev));

      this.activitySub = merge(...streams)
        .pipe(throttleTime(1000))
        .subscribe(() => {
          this.lastActivityTime = Date.now();
          if (this.dialogRef) {
            this.ngZone.run(() => {
              this.dialogRef?.close(true);
              this.dialogRef = null;
            });
          }
        });

      // 1-second interval ticker for inactivity checking
      this.timerId = setInterval(() => {
        this.checkInactivity();
      }, 1000);
    });
  }

  private checkInactivity(): void {
    if (!this.authService.isAuthenticated()) {
      this.stopTimer();
      return;
    }

    const totalAllowedSeconds = Math.max(60, this.autoLogoutMinutes * 60);
    const elapsedSeconds = Math.floor((Date.now() - this.lastActivityTime) / 1000);
    const remainingSeconds = totalAllowedSeconds - elapsedSeconds;

    if (remainingSeconds <= 0) {
      // Inactivity timeout reached -> Logout
      this.ngZone.run(() => {
        this.performLogout();
      });
    } else if (remainingSeconds <= this.warningDurationSeconds && !this.dialogRef) {
      // Show warning dialog within warning window
      this.ngZone.run(() => {
        this.showWarningDialog(remainingSeconds);
      });
    } else if (this.dialogRef) {
      // Update countdown in open warning dialog
      this.ngZone.run(() => {
        if (this.dialogRef?.componentInstance) {
          this.dialogRef.componentInstance.data.remainingSeconds = Math.max(0, remainingSeconds);
        }
      });
    }
  }

  private showWarningDialog(initialRemainingSeconds: number): void {
    if (this.dialogRef) return;

    this.dialogRef = this.dialog.open(AutoLogoutDialogComponent, {
      width: '400px',
      disableClose: true,
      data: { remainingSeconds: initialRemainingSeconds }
    });

    this.dialogRef.afterClosed().subscribe((res) => {
      this.dialogRef = null;
      if (res === true) {
        this.resetTimer();
      }
    });
  }

  private performLogout(): void {
    this.stopTimer();
    if (this.dialogRef) {
      this.dialogRef.close(false);
      this.dialogRef = null;
    }
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  private stopTimer(): void {
    if (this.timerId) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
    if (this.activitySub) {
      this.activitySub.unsubscribe();
      this.activitySub = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
    if (this.authSub) {
      this.authSub.unsubscribe();
    }
  }
}
