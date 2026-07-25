import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="login-container">
      <div class="login-content">
        <div class="login-branding">
          <div class="brand-content">
            <h1 class="brand-title">{{ companyName || 'mybuddy' }}</h1>
            <p class="brand-subtitle">Your Complete Billing Solution</p>
            <div class="brand-features">
              <div class="feature-item">
                <mat-icon>receipt_long</mat-icon>
                <span>Easy Invoice Creation</span>
              </div>
              <div class="feature-item">
                <mat-icon>inventory_2</mat-icon>
                <span>Inventory Management</span>
              </div>
              <div class="feature-item">
                <mat-icon>assessment</mat-icon>
                <span>Detailed Reports</span>
              </div>
            </div>
          </div>
        </div>

        <div class="login-form-section">
          <mat-card class="login-card">
            <mat-card-header>
              <mat-card-title class="login-title">Welcome Back</mat-card-title>
              <mat-card-subtitle>Sign in to your account</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>User ID</mat-label>
                  <input matInput formControlName="email" type="text" required autocomplete="username">
                  <mat-icon matPrefix>person</mat-icon>
                  <mat-error *ngIf="loginForm.get('email')?.hasError('required')">User ID is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Password</mat-label>
                  <input matInput formControlName="password" [type]="hidePassword ? 'password' : 'text'" required autocomplete="current-password">
                  <mat-icon matPrefix>lock</mat-icon>
                  <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword" type="button">
                    <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                  </button>
                  <mat-error *ngIf="loginForm.get('password')?.hasError('required')">Password is required</mat-error>
                </mat-form-field>

                <div *ngIf="expiredToken" class="alert alert-warning">
                  <mat-icon>warning</mat-icon>
                  <div>
                    <p class="alert-title">Session Expired</p>
                    <p class="alert-message">Your session has expired. Please login again.</p>
                  </div>
                </div>

                <div *ngIf="error && !expiredToken" class="alert alert-error">
                  <mat-icon>error</mat-icon>
                  <p>{{ error }}</p>
                </div>

                <button mat-raised-button color="primary" type="submit" class="login-button" [disabled]="loginForm.invalid || loading">
                  <mat-spinner *ngIf="loading" diameter="20"></mat-spinner>
                  <span *ngIf="!loading">Sign In</span>
                </button>
              </form>
            </mat-card-content>
          </mat-card>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #1A1D2E 0%, #2A3A9E 100%);
      padding: 16px;
    }

    .login-content {
      display: flex;
      width: 100%;
      max-width: 1000px;
      min-height: 600px;
      background: white;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
    }

    .login-branding {
      flex: 1;
      background: linear-gradient(135deg, #5B6FE8 0%, #3B4FC8 100%);
      padding: 48px;
      display: flex;
      flex-direction: column;
      justify-content: center;
      color: white;
    }

    .brand-content {
      max-width: 320px;
    }

    .brand-title {
      font-size: 48px;
      font-weight: 700;
      margin: 0 0 8px 0;
      letter-spacing: -1px;
    }

    .brand-subtitle {
      font-size: 18px;
      opacity: 0.9;
      margin: 0 0 48px 0;
    }

    .brand-features {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .feature-item {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 14px;
      opacity: 0.9;
    }

    .feature-item mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .login-form-section {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 48px;
      background: #F9FAFB;
    }

    .login-card {
      width: 100%;
      max-width: 400px;
      padding: 32px;
      border-radius: 12px !important;
      box-shadow: none !important;
      border: 1px solid #E5E7EB !important;
    }

    .login-title {
      font-size: 24px !important;
      font-weight: 600 !important;
      color: #1A1D2E;
      margin-bottom: 4px !important;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin-top: 24px;
    }

    .full-width {
      width: 100%;
    }

    .alert {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 8px;
      font-size: 14px;
    }

    .alert-warning {
      background: #FEF3C7;
      color: #92400E;
      border: 1px solid #FCD34D;
    }

    .alert-error {
      background: #FEE2E2;
      color: #991B1B;
      border: 1px solid #FCA5A5;
    }

    .alert mat-icon {
      flex-shrink: 0;
    }

    .alert-title {
      font-weight: 600;
      margin: 0 0 4px 0;
    }

    .alert-message {
      margin: 0;
    }

    .login-button {
      height: 48px;
      font-size: 16px;
      font-weight: 600;
      border-radius: 8px !important;
      margin-top: 8px;
    }

    .login-button mat-spinner {
      margin-right: 8px;
    }

    /* Responsive - Tablet */
    @media (max-width: 900px) {
      .login-content {
        flex-direction: column;
        max-width: 480px;
        min-height: auto;
      }

      .login-branding {
        padding: 32px;
        text-align: center;
      }

      .brand-content {
        max-width: 100%;
      }

      .brand-title {
        font-size: 36px;
      }

      .brand-subtitle {
        margin-bottom: 24px;
      }

      .brand-features {
        display: none;
      }

      .login-form-section {
        padding: 32px;
      }

      .login-card {
        padding: 24px;
      }
    }

    /* Responsive - Mobile */
    @media (max-width: 480px) {
      .login-container {
        padding: 0;
        align-items: stretch;
      }

      .login-content {
        border-radius: 0;
        min-height: 100vh;
      }

      .login-branding {
        padding: 24px;
      }

      .brand-title {
        font-size: 28px;
      }

      .login-form-section {
        padding: 24px 16px;
      }

      .login-card {
        padding: 16px;
        border: none !important;
        background: transparent;
      }
    }
  `]
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  loading = false;
  error = '';
  expiredToken = false;
  hidePassword = true;
  companyName: string = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private apiService: ApiService
  ) {
    this.authService.logout();
    
    this.loginForm = this.fb.group({
      email: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['expired'] === 'true') {
        this.expiredToken = true;
        this.error = 'Your session has expired. Please login again.';
      }
    });
    this.loadCompanyName();
  }
  
  loadCompanyName() {
    // Try to load company name, but don't fail if not authenticated
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        if (settings?.companyName) {
          this.companyName = settings.companyName;
          document.title = this.companyName;
        }
      },
      error: () => {
        // Keep default name if settings can't be loaded (e.g., not authenticated)
        this.companyName = '';
      }
    });
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.loading = true;
      this.error = '';
      
      this.authService.login(this.loginForm.value).subscribe({
        next: (response) => {
          const checkToken = setInterval(() => {
            const token = this.authService.getToken();
            if (token) {
              clearInterval(checkToken);
              this.loading = false;
              this.router.navigate(['/dashboard']).then(
                (success) => {
                  if (!success) {
                    this.error = 'Navigation failed. Please try again.';
                    this.loading = false;
                  }
                }
              ).catch((err) => {
                this.error = `Navigation error: ${err.message || 'Unknown error'}`;
                this.loading = false;
              });
            } else if (Date.now() - startTime > 2000) {
              clearInterval(checkToken);
              this.error = 'Token storage failed. Please try again.';
              this.loading = false;
            }
          }, 50);
          
          const startTime = Date.now();
        },
        error: (err) => {
          this.error = err.error?.message || err.message || 'Login failed. Please check your credentials.';
          this.loading = false;
        }
      });
    }
  }
}
