import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AnimationService } from '../../../core/services/animation.service';
import { AutoLogoutService } from '../../../core/services/auto-logout.service';

export interface BankAccountDto {
  id?: string;
  accountName: string;
  accountNumber: string;
  bankName: string;
  branch: string;
  ifscCode: string;
}

interface CompanySettings {
  id?: string;
  companyName: string;
  ownerName?: string;
  address: string;
  city: string;
  state: string;
  pincode: string;
  phone: string;
  mobile?: string;
  email: string;
  gstin: string;
  hsnSacCode?: string;
  lutArnNo?: string;
  bankAccountName?: string;
  bankAccountNumber?: string;
  bankName?: string;
  bankBranch?: string;
  bankIfscCode?: string;
  bankAccounts?: BankAccountDto[];
  footerSlogan: string;
  printFormat: string;
  logoPath?: string;
  logoBase64?: string;
  invoiceNumberMode?: string;
  lastSeriesNo?: number;
  year?: number;
  invoiceStartingSequence?: number;
  couriers?: string[];
  items?: string[];
  statuses?: string[];
  awbFrequency?: number;
  defaultGstPercentage?: number | null;
  autoLogoutMinutes?: number;
}

@Component({
  selector: 'app-company-settings',
  template: `
    <div class="cs-container" #pageContainer>
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>

      <!-- Clean Light Header -->
      <div class="cs-header">
        <div class="cs-header-left">
          <div class="cs-icon-box">
            <mat-icon>settings</mat-icon>
          </div>
          <div>
            <h1 class="cs-page-title">Company Settings</h1>
            <p class="cs-page-subtitle">Configure organization profile, session timeout, billing defaults, and document templates.</p>
          </div>
        </div>
        <div class="cs-header-actions">
          <button mat-stroked-button type="button" (click)="loadSettings()" [disabled]="loading" class="cs-btn-reset-top">
            <mat-icon>refresh</mat-icon> Reset
          </button>
          <button mat-raised-button color="primary" type="button" (click)="onSubmit()" [disabled]="settingsForm.invalid || loading" class="cs-btn-save-top">
            <mat-icon *ngIf="!loading">save</mat-icon>
            <mat-spinner *ngIf="loading" diameter="18" style="margin-right: 8px;"></mat-spinner>
            <span>{{ loading ? 'Saving...' : 'Save Settings' }}</span>
          </button>
        </div>
      </div>

      <!-- Settings Card with Tabs -->
      <mat-card class="cs-card" #card>
        <form [formGroup]="settingsForm" (ngSubmit)="onSubmit()">
          <mat-tab-group animationDuration="200ms" class="cs-tabs">

            <!-- Tab 1: Company Profile -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="tab-icon">business</mat-icon>
                <span>Company Profile</span>
              </ng-template>
              <div class="cs-tab-content">
                <div class="cs-section-lead">
                  <h3>Organization Details</h3>
                  <p>Primary business identity printed on customer invoices, delivery receipts, and email communications.</p>
                </div>

                <div class="cs-form-grid">
                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Company Name *</mat-label>
                    <input matInput formControlName="companyName" required placeholder="e.g. MINHA ENTERPRISES">
                    <mat-error *ngIf="settingsForm.get('companyName')?.hasError('required')">Company name is required</mat-error>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Owner / Contact Person</mat-label>
                    <input matInput formControlName="ownerName" placeholder="Shown in email signatures (e.g. Thanks & Regards)">
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Full Office Address</mat-label>
                    <textarea matInput formControlName="address" rows="2" placeholder="Street address, building name, landmark"></textarea>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>City</mat-label>
                    <input matInput formControlName="city" placeholder="CHENNAI">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>State</mat-label>
                    <input matInput formControlName="state" placeholder="TAMILNADU">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Pincode</mat-label>
                    <input matInput formControlName="pincode" placeholder="603103">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Landline Phone</mat-label>
                    <input matInput formControlName="phone" type="tel" placeholder="044-12345678">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Mobile (for Invoices)</mat-label>
                    <input matInput formControlName="mobile" type="tel" placeholder="9884822786">
                    <mat-hint>Primary contact number printed on invoices</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Email Address</mat-label>
                    <input matInput formControlName="email" type="email" placeholder="billing@company.com">
                    <mat-error *ngIf="settingsForm.get('email')?.hasError('email')">Invalid email address</mat-error>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>GSTIN</mat-label>
                    <input matInput formControlName="gstin" placeholder="33DZFPS1076D1ZH">
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>HSN / SAC Code</mat-label>
                    <input matInput formControlName="hsnSacCode" placeholder="996812">
                    <mat-hint>Shown on invoice PDF header</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>LUT ARN Number</mat-label>
                    <input matInput formControlName="lutArnNo" placeholder="Letter of Undertaking ARN">
                    <mat-hint>Shown on Monthly Courier Invoice PDF</mat-hint>
                  </mat-form-field>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 2: Security & Auto Logout -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="tab-icon icon-purple">security</mat-icon>
                <span>Security Policy</span>
              </ng-template>
              <div class="cs-tab-content">
                <div class="cs-section-lead">
                  <h3>Security & Session Management</h3>
                  <p>Protect account access with automatic session termination when inactive.</p>
                </div>

                <div class="cs-security-banner">
                  <div class="cs-sec-icon-circle">
                    <mat-icon>timer</mat-icon>
                  </div>
                  <div class="cs-sec-details">
                    <h4>Automatic Inactivity Logout</h4>
                    <p>To protect sensitive billing and client records, user sessions are automatically logged out after a period of inactivity. A 60-second warning countdown is displayed before session expiration.</p>
                  </div>
                </div>

                <div class="cs-form-grid cs-grid-single" style="margin-top: 24px;">
                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Auto Logout Time (Minutes) *</mat-label>
                    <input matInput type="number" formControlName="autoLogoutMinutes" min="1" max="1440" placeholder="15">
                    <mat-icon matSuffix class="suffix-icon">schedule</mat-icon>
                    <mat-hint>Default is 15 minutes (Allowed range: 1 to 1440 minutes / 24 hours)</mat-hint>
                    <mat-error *ngIf="settingsForm.get('autoLogoutMinutes')?.hasError('required')">Auto logout time is required</mat-error>
                    <mat-error *ngIf="settingsForm.get('autoLogoutMinutes')?.hasError('min')">Must be at least 1 minute</mat-error>
                    <mat-error *ngIf="settingsForm.get('autoLogoutMinutes')?.hasError('max')">Maximum allowed is 1440 minutes</mat-error>
                  </mat-form-field>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 3: Bank Accounts -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="tab-icon">account_balance</mat-icon>
                <span>Bank Accounts</span>
              </ng-template>
              <div class="cs-tab-content">
                <div class="cs-section-lead-flex">
                  <div>
                    <h3>Bank Accounts (RTGS / NEFT)</h3>
                    <p>Configure bank accounts available for selection on quotations and invoices.</p>
                  </div>
                  <button type="button" mat-flat-button color="primary" (click)="addBankAccount()" class="cs-btn-add-bank">
                    <mat-icon>add</mat-icon> Add Bank Account
                  </button>
                </div>

                <div formArrayName="bankAccounts" class="cs-bank-stack">
                  <div *ngFor="let bank of bankAccounts.controls; let i = index" [formGroupName]="i" class="cs-bank-card">
                    <div class="cs-bank-card-head">
                      <span class="cs-badge">Account {{ i + 1 }}</span>
                      <button type="button" mat-icon-button color="warn" (click)="removeBankAccount(i)" matTooltip="Remove Bank Account">
                        <mat-icon>delete_outline</mat-icon>
                      </button>
                    </div>
                    <div class="cs-form-grid cs-grid-3">
                      <mat-form-field appearance="outline">
                        <mat-label>Account Name</mat-label>
                        <input matInput formControlName="accountName" placeholder="e.g. MINHA ENTERPRISES">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Account Number</mat-label>
                        <input matInput formControlName="accountNumber" placeholder="e.g. 270005001219">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Bank Name</mat-label>
                        <input matInput formControlName="bankName" placeholder="e.g. STATE BANK OF INDIA">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>Branch</mat-label>
                        <input matInput formControlName="branch" placeholder="e.g. KELAMBAKKAM">
                      </mat-form-field>
                      <mat-form-field appearance="outline">
                        <mat-label>IFSC Code</mat-label>
                        <input matInput formControlName="ifscCode" placeholder="e.g. SBIN0001234">
                      </mat-form-field>
                    </div>
                  </div>

                  <div *ngIf="bankAccounts.controls.length === 0" class="cs-empty-state">
                    <mat-icon>account_balance_wallet</mat-icon>
                    <p>No bank accounts added yet. Click "Add Bank Account" to configure payment details.</p>
                  </div>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 4: Invoicing & Printing -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="tab-icon">receipt_long</mat-icon>
                <span>Invoicing & Printing</span>
              </ng-template>
              <div class="cs-tab-content">
                <div class="cs-section-lead">
                  <h3>Invoice Sequence & Print Layout</h3>
                  <p>Set automated sequence numbers, active financial period, and PDF print formatting.</p>
                </div>

                <div class="cs-form-grid">
                  <mat-form-field appearance="outline">
                    <mat-label>Invoice Number Mode</mat-label>
                    <mat-select formControlName="invoiceNumberMode">
                      <mat-option value="AUTO">Auto Generated</mat-option>
                      <mat-option value="MANUAL">Manual Entry</mat-option>
                    </mat-select>
                    <mat-hint>Choose auto sequence or manual input</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Last Series Number</mat-label>
                    <input matInput type="number" formControlName="lastSeriesNo" placeholder="123">
                    <mat-hint>Last generated invoice number</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Financial Year Start</mat-label>
                    <input matInput type="number" formControlName="year" placeholder="2026">
                    <mat-hint>FY starting year (e.g. 2026 for FY 2026-27)</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Invoice Starting Sequence</mat-label>
                    <input matInput type="number" min="1" formControlName="invoiceStartingSequence" placeholder="1">
                    <mat-hint>Starting sequence for Client invoices (e.g. 1)</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Print Format *</mat-label>
                    <mat-select formControlName="printFormat" required>
                      <mat-option value="A4">A4 Standard Sheet</mat-option>
                      <mat-option value="THERMAL">Thermal Receipt</mat-option>
                      <mat-option value="COMPACT">Compact Page</mat-option>
                    </mat-select>
                    <mat-error *ngIf="settingsForm.get('printFormat')?.hasError('required')">Print format is required</mat-error>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Footer Slogan / Closing Quote</mat-label>
                    <textarea matInput formControlName="footerSlogan" rows="2" placeholder="e.g. Thank you for your business!"></textarea>
                    <mat-hint>Printed at the bottom of customer invoices and receipts</mat-hint>
                  </mat-form-field>
                </div>
              </div>
            </mat-tab>

            <!-- Tab 5: Masters & Branding -->
            <mat-tab>
              <ng-template mat-tab-label>
                <mat-icon class="tab-icon">tune</mat-icon>
                <span>Masters & Branding</span>
              </ng-template>
              <div class="cs-tab-content">
                <div class="cs-section-lead">
                  <h3>AWB Step & Master Dropdowns</h3>
                  <p>Configure bulk AWB number generation step, default tax rates, and dropdown values.</p>
                </div>

                <div class="cs-form-grid" style="margin-bottom: 24px;">
                  <mat-form-field appearance="outline">
                    <mat-label>AWB Step Frequency *</mat-label>
                    <input matInput type="number" formControlName="awbFrequency" min="1" step="1" placeholder="1">
                    <mat-hint>Step interval between bulk-generated AWB numbers</mat-hint>
                    <mat-error *ngIf="settingsForm.get('awbFrequency')?.hasError('required')">Required for bulk generation</mat-error>
                    <mat-error *ngIf="settingsForm.get('awbFrequency')?.hasError('min')">Must be at least 1</mat-error>
                  </mat-form-field>

                  <mat-form-field appearance="outline">
                    <mat-label>Default GST Percentage (%)</mat-label>
                    <input matInput type="number" formControlName="defaultGstPercentage" min="0" max="100" step="0.01" placeholder="Leave blank for 0%">
                    <mat-hint>Fallback GST % when Client Entry GST is empty</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Courier Partners (Comma-separated)</mat-label>
                    <textarea matInput formControlName="couriersInput" rows="2" placeholder="DHL, FedEx, Blue Dart, Professional"></textarea>
                    <mat-hint>Separate partner names with commas</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Shipment Items (Comma-separated)</mat-label>
                    <textarea matInput formControlName="itemsInput" rows="2" placeholder="Document, Parcel, Sample, Box"></textarea>
                    <mat-hint>Separate item categories with commas</mat-hint>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="cs-span-2">
                    <mat-label>Delivery Statuses (Comma-separated)</mat-label>
                    <textarea matInput formControlName="statusesInput" rows="2" placeholder="Picked Up, In Transit, Delivered, RTO"></textarea>
                    <mat-hint>Separate status labels with commas</mat-hint>
                  </mat-form-field>
                </div>

                <div class="cs-section-lead" style="margin-top: 16px;">
                  <h3>Official Brand Logo</h3>
                  <p>Upload brand logo displayed on top of printed invoices and quotations.</p>
                </div>

                <div class="cs-logo-panel">
                  <div class="cs-logo-btn-row">
                    <input type="file" #fileInput accept="image/*" (change)="onFileSelected($event)" class="cs-file-input" id="logoUpload">
                    <button type="button" mat-stroked-button color="primary" (click)="fileInput.click()" class="cs-btn-upload">
                      <mat-icon>upload</mat-icon> Choose Logo File
                    </button>
                    <button *ngIf="logoPreview" type="button" mat-button color="warn" (click)="removeLogo()">
                      <mat-icon>delete</mat-icon> Remove Logo
                    </button>
                  </div>
                  <div *ngIf="logoPreview" class="cs-preview-box">
                    <img [src]="logoPreview" alt="Brand Logo Preview" class="cs-logo-image">
                    <span class="cs-preview-caption">Document Header Logo Preview</span>
                  </div>
                  <p *ngIf="!logoPreview" class="cs-upload-hint">
                    PNG, JPG, or WEBP formats supported (Maximum file size: 2MB).
                  </p>
                </div>
              </div>
            </mat-tab>

          </mat-tab-group>

          <!-- Clean In-Card Footer -->
          <div class="cs-card-footer">
            <button mat-stroked-button type="button" (click)="loadSettings()" [disabled]="loading" class="cs-btn-reset-bottom">
              <mat-icon>refresh</mat-icon> Reset Changes
            </button>
            <button mat-raised-button color="primary" type="submit" [disabled]="settingsForm.invalid || loading" class="cs-btn-save-bottom">
              <mat-icon *ngIf="!loading">save</mat-icon>
              <mat-spinner *ngIf="loading" diameter="18" style="margin-right: 8px;"></mat-spinner>
              <span>{{ loading ? 'Saving Settings...' : 'Save Settings' }}</span>
            </button>
          </div>
        </form>
      </mat-card>
    </div>
  `,
  styles: [`
    /* ── Base Container ── */
    .cs-container {
      width: 100%;
      padding: 24px 16px 48px;
      font-family: 'Inter', system-ui, -apple-system, sans-serif;
    }

    /* ── Header ── */
    .cs-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
      flex-wrap: wrap;
      gap: 16px;
    }
    .cs-header-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .cs-icon-box {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(2, 132, 199, 0.2);
    }
    .cs-icon-box mat-icon {
      font-size: 26px;
      width: 26px;
      height: 26px;
    }
    .cs-page-title {
      margin: 0;
      font-size: 1.625rem;
      font-weight: 800;
      color: #0f172a;
      letter-spacing: -0.02em;
    }
    .cs-page-subtitle {
      margin: 3px 0 0;
      font-size: 0.875rem;
      color: #64748b;
    }
    .cs-header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .cs-btn-save-top, .cs-btn-reset-top {
      height: 40px;
      font-weight: 600;
      border-radius: 8px !important;
    }

    /* ── Main Card ── */
    .cs-card {
      background: #ffffff !important;
      border-radius: 16px !important;
      border: 1px solid #e2e8f0 !important;
      box-shadow: 0 4px 20px rgba(15, 23, 42, 0.05) !important;
      overflow: hidden;
      padding: 0 !important;
    }

    /* ── Tabs ── */
    .cs-tabs ::ng-deep .mat-mdc-tab-header {
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      padding: 0 12px;
    }
    .cs-tabs ::ng-deep .mat-mdc-tab .mdc-tab__text-label {
      font-weight: 600;
      font-size: 0.875rem;
      color: #475569;
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }
    .cs-tabs ::ng-deep .mat-mdc-tab.mdc-tab--active .mdc-tab__text-label {
      color: #0284c7;
    }
    .tab-icon {
      font-size: 20px !important;
      width: 20px !important;
      height: 20px !important;
      color: #64748b;
    }
    .cs-tabs ::ng-deep .mat-mdc-tab.mdc-tab--active .tab-icon {
      color: #0284c7;
    }
    .icon-purple {
      color: #7c3aed !important;
    }

    /* ── Tab Content ── */
    .cs-tab-content {
      padding: 28px 32px 36px;
    }

    /* ── Section Leads ── */
    .cs-section-lead {
      margin-bottom: 24px;
      padding-bottom: 12px;
      border-bottom: 1px solid #f1f5f9;
    }
    .cs-section-lead-flex {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      margin-bottom: 24px;
      padding-bottom: 12px;
      border-bottom: 1px solid #f1f5f9;
      flex-wrap: wrap;
      gap: 12px;
    }
    .cs-section-lead h3, .cs-section-lead-flex h3 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
      color: #0f172a;
    }
    .cs-section-lead p, .cs-section-lead-flex p {
      margin: 3px 0 0;
      font-size: 0.8125rem;
      color: #64748b;
    }

    /* ── Grid System (2 columns standard) ── */
    .cs-form-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px 24px;
    }
    .cs-grid-3 {
      grid-template-columns: repeat(3, 1fr);
    }
    .cs-grid-single {
      grid-template-columns: 1fr;
    }
    .cs-span-2 {
      grid-column: span 2;
    }
    mat-form-field {
      width: 100%;
    }
    .suffix-icon {
      color: #64748b;
    }

    /* ── Security Policy Banner ── */
    .cs-security-banner {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      background: linear-gradient(135deg, #f5f3ff 0%, #faf5ff 100%);
      border: 1px solid #ddd6fe;
      border-radius: 12px;
      padding: 20px;
    }
    .cs-sec-icon-circle {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: #ede9fe;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .cs-sec-icon-circle mat-icon {
      color: #7c3aed;
      font-size: 24px;
      width: 24px;
      height: 24px;
    }
    .cs-sec-details h4 {
      margin: 0;
      font-size: 0.975rem;
      font-weight: 700;
      color: #4c1d95;
    }
    .cs-sec-details p {
      margin: 4px 0 0;
      font-size: 0.825rem;
      color: #6d28d9;
      line-height: 1.45;
    }

    /* ── Bank Accounts ── */
    .cs-bank-stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .cs-bank-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
    }
    .cs-bank-card-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .cs-badge {
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #0284c7;
      background: #e0f2fe;
      padding: 4px 10px;
      border-radius: 16px;
    }
    .cs-btn-add-bank {
      border-radius: 8px !important;
      font-weight: 600;
    }
    .cs-empty-state {
      text-align: center;
      padding: 36px 16px;
      color: #94a3b8;
    }
    .cs-empty-state mat-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: #cbd5e1;
      margin-bottom: 8px;
    }

    /* ── Logo Panel ── */
    .cs-logo-panel {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .cs-logo-btn-row {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .cs-file-input {
      display: none;
    }
    .cs-btn-upload {
      border-radius: 8px !important;
      font-weight: 600;
    }
    .cs-preview-box {
      display: inline-flex;
      flex-direction: column;
      align-items: flex-start;
      padding: 14px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      gap: 8px;
      width: fit-content;
    }
    .cs-logo-image {
      max-width: 220px;
      max-height: 90px;
      object-fit: contain;
    }
    .cs-preview-caption {
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
    }
    .cs-upload-hint {
      margin: 0;
      font-size: 0.8125rem;
      color: #64748b;
    }

    /* ── Card Footer ── */
    .cs-card-footer {
      background: #f8fafc;
      border-top: 1px solid #e2e8f0;
      padding: 16px 32px;
      display: flex;
      justify-content: flex-end;
      gap: 16px;
    }
    .cs-btn-save-bottom {
      min-width: 160px;
      height: 42px;
      font-weight: 600;
      border-radius: 8px !important;
    }
    .cs-btn-reset-bottom {
      height: 42px;
      font-weight: 500;
      border-radius: 8px !important;
      color: #64748b !important;
    }

    /* ── Responsive ── */
    @media (max-width: 768px) {
      .cs-container {
        padding: 16px 8px 32px;
      }
      .cs-tab-content {
        padding: 20px 16px;
      }
      .cs-form-grid, .cs-grid-3 {
        grid-template-columns: 1fr;
      }
      .cs-span-2 {
        grid-column: span 1;
      }
      .cs-header {
        flex-direction: column;
        align-items: flex-start;
      }
      .cs-header-actions {
        width: 100%;
        justify-content: flex-end;
      }
      .cs-card-footer {
        padding: 16px;
      }
    }
  `]
})
export class CompanySettingsComponent implements OnInit, AfterViewInit {
  settingsForm: FormGroup;
  loading = false;
  logoPreview: string | null = null;
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('pageContainer') pageContainer!: ElementRef;
  @ViewChild('card') card!: ElementRef;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private http: HttpClient,
    private toastService: ToastService,
    private animationService: AnimationService,
    private autoLogoutService: AutoLogoutService
  ) {
    this.settingsForm = this.fb.group({
      companyName: ['', Validators.required],
      ownerName: [''],
      address: [''],
      city: [''],
      state: [''],
      pincode: [''],
      phone: [''],
      mobile: [''],
      email: ['', Validators.email],
      gstin: [''],
      hsnSacCode: [''],
      lutArnNo: [''],
      bankAccounts: this.fb.array([]),
      footerSlogan: ['Thank you for your business!'],
      printFormat: ['A4', Validators.required],
      invoiceNumberMode: ['AUTO'],
      lastSeriesNo: [null as number | null, [Validators.min(0)]],
      year: [null as number | null, [Validators.min(1000), Validators.max(9999)]],
      invoiceStartingSequence: [1, [Validators.min(1)]],
      couriersInput: [''],
      itemsInput: [''],
      statusesInput: [''],
      awbFrequency: [1, [Validators.required, Validators.min(1), Validators.pattern(/^\d+$/)]],
      defaultGstPercentage: [null as number | null],
      autoLogoutMinutes: [15, [Validators.required, Validators.min(1), Validators.max(1440)]]
    });
  }

  ngOnInit() {
    this.loadSettings();
  }

  get bankAccounts(): FormArray {
    return this.settingsForm.get('bankAccounts') as FormArray;
  }

  addBankAccount(account?: BankAccountDto) {
    const id = account?.id ?? ('bank-' + Date.now() + '-' + Math.random().toString(36).slice(2, 9));
    this.bankAccounts.push(this.fb.group({
      id: [id],
      accountName: [account?.accountName ?? ''],
      accountNumber: [account?.accountNumber ?? ''],
      bankName: [account?.bankName ?? ''],
      branch: [account?.branch ?? ''],
      ifscCode: [account?.ifscCode ?? '']
    }));
  }

  removeBankAccount(index: number) {
    this.bankAccounts.removeAt(index);
  }

  ngAfterViewInit() {
    setTimeout(() => {
      if (this.pageContainer?.nativeElement) {
        this.animationService.fadeIn(this.pageContainer.nativeElement);
      }
      if (this.card?.nativeElement) {
        this.animationService.scaleIn(this.card.nativeElement, 0.95);
      }
    }, 100);
  }

  loadSettings() {
    this.loading = true;
    this.apiService.get<CompanySettings>('/company-settings').subscribe({
      next: (settings: CompanySettings) => {
        this.settingsForm.patchValue({
          companyName: settings.companyName || '',
          ownerName: settings.ownerName || '',
          address: settings.address || '',
          city: settings.city || '',
          state: settings.state || '',
          pincode: settings.pincode || '',
          phone: settings.phone || '',
          mobile: settings.mobile || '',
          email: settings.email || '',
          gstin: settings.gstin || '',
          hsnSacCode: settings.hsnSacCode || '',
          lutArnNo: settings.lutArnNo || '',
          footerSlogan: settings.footerSlogan || 'Thank you for your business!',
          printFormat: settings.printFormat || 'A4',
          invoiceNumberMode: settings.invoiceNumberMode || 'AUTO',
          lastSeriesNo: settings.lastSeriesNo ?? null,
          year: settings.year ?? null,
          invoiceStartingSequence: settings.invoiceStartingSequence != null && settings.invoiceStartingSequence > 0
            ? settings.invoiceStartingSequence : 1,
          couriersInput: settings.couriers ? settings.couriers.join(', ') : '',
          itemsInput: settings.items ? settings.items.join(', ') : '',
          statusesInput: settings.statuses ? settings.statuses.join(', ') : '',
          awbFrequency: settings.awbFrequency != null && settings.awbFrequency > 0 ? settings.awbFrequency : 1,
          defaultGstPercentage: settings.defaultGstPercentage ?? null,
          autoLogoutMinutes: settings.autoLogoutMinutes != null && settings.autoLogoutMinutes > 0 ? settings.autoLogoutMinutes : 15
        });
        this.bankAccounts.clear();
        (settings.bankAccounts || []).forEach((b: BankAccountDto) => this.addBankAccount(b));
        if (settings.logoBase64) {
          if (settings.logoBase64.startsWith('data:')) {
            this.logoPreview = settings.logoBase64;
          } else {
            this.logoPreview = 'data:image/png;base64,' + settings.logoBase64;
          }
        }
        
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load settings');
        this.loading = false;
      }
    });
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      
      if (!file.type.startsWith('image/')) {
        this.toastService.error('Error', 'Please select an image file');
        return;
      }
      
      if (file.size > 2 * 1024 * 1024) {
        this.toastService.error('Error', 'Image size should be less than 2MB');
        return;
      }
      
      const reader = new FileReader();
      reader.onload = (e: any) => {
        const base64String = e.target.result.split(',')[1];
        this.logoPreview = e.target.result;
        this.settingsForm.patchValue({ logoBase64: base64String });
      };
      reader.readAsDataURL(file);
    }
  }

  removeLogo() {
    this.logoPreview = null;
    this.settingsForm.patchValue({ logoBase64: null });
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  onSubmit() {
    if (this.settingsForm.invalid) {
      return;
    }

    this.loading = true;
    const formValue = this.settingsForm.value;
    
    const bankAccounts = (formValue.bankAccounts || []).map((row: any) => ({
      id: row.id || undefined,
      accountName: row.accountName || '',
      accountNumber: row.accountNumber || '',
      bankName: row.bankName || '',
      branch: row.branch || '',
      ifscCode: row.ifscCode || ''
    }));
    const autoLogout = Math.max(1, Math.floor(Number(formValue.autoLogoutMinutes) || 15));
    const settings: CompanySettings = {
      companyName: formValue.companyName,
      ownerName: formValue.ownerName || '',
      address: formValue.address || '',
      city: formValue.city || '',
      state: formValue.state || '',
      pincode: formValue.pincode || '',
      phone: formValue.phone || '',
      mobile: formValue.mobile || '',
      email: formValue.email || '',
      gstin: formValue.gstin || '',
      hsnSacCode: formValue.hsnSacCode || '',
      lutArnNo: formValue.lutArnNo || '',
      bankAccounts,
      footerSlogan: formValue.footerSlogan || 'Thank you for your business!',
      printFormat: formValue.printFormat,
      logoBase64: formValue.logoBase64 || null,
      invoiceNumberMode: formValue.invoiceNumberMode || 'AUTO',
      lastSeriesNo: formValue.lastSeriesNo != null ? Number(formValue.lastSeriesNo) : undefined,
      year: formValue.year != null ? Number(formValue.year) : undefined,
      invoiceStartingSequence: Math.max(1, Math.floor(Number(formValue.invoiceStartingSequence) || 1)),
      couriers: formValue.couriersInput ? formValue.couriersInput.split(',').map((s: string) => s.trim()).filter((s: string) => s) : [],
      items: formValue.itemsInput ? formValue.itemsInput.split(',').map((s: string) => s.trim()).filter((s: string) => s) : [],
      statuses: formValue.statusesInput ? formValue.statusesInput.split(',').map((s: string) => s.trim()).filter((s: string) => s) : [],
      awbFrequency: Math.max(1, Math.floor(Number(formValue.awbFrequency) || 1)),
      defaultGstPercentage: formValue.defaultGstPercentage != null && formValue.defaultGstPercentage !== ''
        ? Number(formValue.defaultGstPercentage) : null,
      autoLogoutMinutes: autoLogout
    };

    this.http.put<CompanySettings>(`${this.apiService.getBaseUrl()}/company-settings`, settings).subscribe({
      next: () => {
        this.loading = false;
        this.autoLogoutService.updateTimeoutMinutes(autoLogout);
        this.toastService.success('Success', 'Company settings saved successfully');
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to save settings');
      }
    });
  }
}
