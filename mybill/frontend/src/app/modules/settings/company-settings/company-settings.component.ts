import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AnimationService } from '../../../core/services/animation.service';

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
}

@Component({
  selector: 'app-company-settings',
  template: `
    <div class="page-container" #pageContainer>
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-4xl mx-auto" #card>
        <mat-card-header>
          <mat-card-title class="dark:text-gray-100">Company Settings</mat-card-title>
          <mat-card-subtitle>Configure company details and print preferences</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="settingsForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Company Information Section -->
            <div class="form-section">
              <h3 class="section-title">Company Information</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full md:col-span-2">
                  <mat-label>Company Name *</mat-label>
                  <input matInput formControlName="companyName" required>
                  <mat-error *ngIf="settingsForm.get('companyName')?.hasError('required')">Company name is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full md:col-span-2">
                  <mat-label>Owner Name</mat-label>
                  <input matInput formControlName="ownerName" placeholder="Shown in email signatures (Thanks and Regards)">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full md:col-span-2">
                  <mat-label>Address</mat-label>
                  <textarea matInput formControlName="address" rows="2"></textarea>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>City</mat-label>
                  <input matInput formControlName="city">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>State</mat-label>
                  <input matInput formControlName="state">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Pincode</mat-label>
                  <input matInput formControlName="pincode">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Phone</mat-label>
                  <input matInput formControlName="phone" type="tel">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Mobile (for invoices)</mat-label>
                  <input matInput formControlName="mobile" type="tel" placeholder="e.g., 9884822786">
                  <mat-hint>Contact number shown on invoices</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Email</mat-label>
                  <input matInput formControlName="email" type="email">
                  <mat-error *ngIf="settingsForm.get('email')?.hasError('email')">Invalid email format</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>GSTIN</mat-label>
                  <input matInput formControlName="gstin" placeholder="e.g., 29ABCDE1234F1Z5">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>HSN/SAC Code</mat-label>
                  <input matInput formControlName="hsnSacCode" placeholder="e.g., 996812">
                  <mat-hint>Shown on invoice PDF (e.g. 996812)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>LUT ARN NO</mat-label>
                  <input matInput formControlName="lutArnNo" placeholder="Letter of undertaking ARN">
                  <mat-hint>Shown below HSN/SAC on Monthly Courier Invoice PDF</mat-hint>
                </mat-form-field>
              </div>
            </div>

            <!-- Banking Details Section (multiple accounts for quotation/invoice selection) -->
            <div class="form-section">
              <h3 class="section-title">Bank Accounts (RTGS/NEFT – select one per Courier Quotation)</h3>
              <div formArrayName="bankAccounts" class="space-y-4">
                <div *ngFor="let bank of bankAccounts.controls; let i = index" [formGroupName]="i" class="bank-row grid grid-cols-1 md:grid-cols-2 gap-4 p-4 bg-white rounded border border-gray-200">
                  <span class="md:col-span-2 font-medium text-gray-700">Account {{ i + 1 }}</span>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Account Name</mat-label>
                    <input matInput formControlName="accountName" placeholder="e.g., S L ENTERPRISES">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Account Number</mat-label>
                    <input matInput formControlName="accountNumber" placeholder="e.g., 270005001219">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Bank Name</mat-label>
                    <input matInput formControlName="bankName" placeholder="e.g., STATE BANK OF INDIA">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>Branch</mat-label>
                    <input matInput formControlName="branch" placeholder="e.g., KELAMBAKKAM">
                  </mat-form-field>
                  <mat-form-field appearance="outline" class="w-full">
                    <mat-label>IFSC Code</mat-label>
                    <input matInput formControlName="ifscCode" placeholder="e.g., ICIC0002700">
                  </mat-form-field>
                  <div class="w-full flex items-end">
                    <button type="button" mat-button color="warn" (click)="removeBankAccount(i)">
                      <mat-icon>delete</mat-icon> Remove
                    </button>
                  </div>
                </div>
              </div>
              <button type="button" mat-stroked-button color="primary" (click)="addBankAccount()" class="mt-2">
                <mat-icon>add</mat-icon> Add Bank Account
              </button>
            </div>

            <!-- Invoice Number Settings Section -->
            <div class="form-section">
              <h3 class="section-title">Invoice Number Settings</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Invoice Number Mode</mat-label>
                  <mat-select formControlName="invoiceNumberMode">
                    <mat-option value="AUTO">Auto Generated</mat-option>
                    <mat-option value="MANUAL">Manual Entry</mat-option>
                  </mat-select>
                  <mat-hint>Choose whether invoice numbers are auto-generated or manually entered</mat-hint>
                </mat-form-field>

                <div class="w-full"></div>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Last Series No</mat-label>
                  <input matInput type="number" formControlName="lastSeriesNo" placeholder="e.g., 123">
                  <mat-hint>The last generated series number (e.g. 123)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Year</mat-label>
                  <input matInput type="number" formControlName="year" placeholder="e.g., 2026">
                  <mat-hint>The starting year of the Financial Year (e.g. 2026)</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Invoice Starting Number</mat-label>
                  <input matInput type="number" min="1" formControlName="invoiceStartingSequence" placeholder="e.g., 1">
                  <mat-hint>Starting sequence for Client/Small Client FY invoices (e.g. 001/2026)</mat-hint>
                </mat-form-field>
              </div>
            </div>

            <!-- Print Settings Section -->
            <div class="form-section">
              <h3 class="section-title">Print Settings</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Print Format *</mat-label>
                  <mat-select formControlName="printFormat" required>
                    <mat-option value="A4">A4</mat-option>
                    <mat-option value="THERMAL">Thermal</mat-option>
                    <mat-option value="COMPACT">Compact</mat-option>
                  </mat-select>
                  <mat-error *ngIf="settingsForm.get('printFormat')?.hasError('required')">Print format is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full md:col-span-2">
                  <mat-label>Footer Slogan / Quote</mat-label>
                  <textarea matInput formControlName="footerSlogan" rows="2" 
                            placeholder="e.g., Thank you for your business!"></textarea>
                  <mat-hint>This will appear at the bottom of printed invoices</mat-hint>
                </mat-form-field>
              </div>
            </div>

            <div class="form-section">
              <h3 class="section-title">AWB Center</h3>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <mat-form-field appearance="outline" class="w-full">
                <mat-label>AWB Frequency *</mat-label>
                <input matInput type="number" formControlName="awbFrequency" min="1" step="1" placeholder="1">
                <mat-hint>Step between bulk-generated AWB numbers (positive integer, default 1).</mat-hint>
                <mat-error *ngIf="settingsForm.get('awbFrequency')?.hasError('required')">Required for bulk generation</mat-error>
                <mat-error *ngIf="settingsForm.get('awbFrequency')?.hasError('min')">Must be at least 1</mat-error>
                <mat-error *ngIf="settingsForm.get('awbFrequency')?.hasError('pattern')">Whole numbers only</mat-error>
              </mat-form-field>
              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Default GST Percentage (%)</mat-label>
                <input matInput type="number" formControlName="defaultGstPercentage" min="0" max="100" step="0.01" placeholder="Blank = 0%">
                <mat-hint>Used when Client Entry GST % is blank. 0–100.</mat-hint>
              </mat-form-field>
              </div>
            </div>

            <!-- Configurable Dropdowns Section -->
            <div class="form-section">
              <h3 class="section-title">Shipment Dropdown Options (Comma-separated)</h3>
              <div class="grid grid-cols-1 gap-4">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Courier Options</mat-label>
                  <textarea matInput formControlName="couriersInput" rows="2" placeholder="e.g., DHL, FedEx, Blue Dart"></textarea>
                  <mat-hint>Enter courier names separated by commas.</mat-hint>
                </mat-form-field>
                
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Item Options</mat-label>
                  <textarea matInput formControlName="itemsInput" rows="2" placeholder="e.g., Document, Parcel, Box"></textarea>
                  <mat-hint>Enter item types separated by commas.</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Status Options</mat-label>
                  <textarea matInput formControlName="statusesInput" rows="2" placeholder="e.g., Picked Up, In Transit, Delivered, Returned"></textarea>
                  <mat-hint>Enter delivery statuses separated by commas.</mat-hint>
                </mat-form-field>
              </div>
            </div>

            <!-- Logo Upload Section -->
            <div class="form-section">
              <h3 class="section-title">Company Logo</h3>
              <div class="flex flex-col gap-4">
                <div class="flex items-center gap-4">
                  <input type="file" #fileInput accept="image/*" (change)="onFileSelected($event)" 
                         class="hidden" id="logoUpload">
                  <button type="button" mat-raised-button color="accent" class="btn-secondary" (click)="fileInput.click()">
                    <mat-icon>upload</mat-icon>
                    <span>Upload Logo</span>
                  </button>
                  <button *ngIf="logoPreview" type="button" mat-button color="warn" (click)="removeLogo()">
                    <mat-icon>delete</mat-icon>
                    <span>Remove Logo</span>
                  </button>
                </div>
                <div *ngIf="logoPreview" class="logo-preview">
                  <img [src]="logoPreview" alt="Company Logo" class="logo-image">
                  <p class="text-sm text-gray-600 mt-2">Logo preview (will appear on invoices)</p>
                </div>
                <p *ngIf="!logoPreview" class="text-sm text-gray-500">
                  Upload a logo image (PNG, JPG) to display on printed invoices
                </p>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex gap-4 pt-4">
              <button mat-raised-button color="primary" type="submit" 
                      [disabled]="settingsForm.invalid || loading"
                      class="btn-primary flex-1">
                <span *ngIf="!loading">Save Settings</span>
                <span *ngIf="loading">Saving...</span>
              </button>
              <button mat-button type="button" (click)="loadSettings()" [disabled]="loading">
                Reset
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 24px;
      animation: fadeSlideUp 0.3s ease-out;
    }

    .form-section {
      margin-bottom: 32px;
      padding: 24px;
      background: #F9FAFB;
      border-radius: 8px;
    }

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #1A1D2E;
      margin-bottom: 16px;
    }

    .logo-preview {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }

    .logo-image {
      max-width: 200px;
      max-height: 100px;
      object-fit: contain;
      border: 1px solid #E5E7EB;
      border-radius: 4px;
      padding: 8px;
      background: white;
    }

    .hidden {
      display: none;
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
    private animationService: AnimationService
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
      defaultGstPercentage: [null as number | null]
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
          defaultGstPercentage: settings.defaultGstPercentage ?? null
        });
        this.bankAccounts.clear();
        (settings.bankAccounts || []).forEach((b: BankAccountDto) => this.addBankAccount(b));
        if (settings.logoBase64) {
          // Check if logoBase64 already includes data URL prefix
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
      
      // Validate file type
      if (!file.type.startsWith('image/')) {
        this.toastService.error('Error', 'Please select an image file');
        return;
      }
      
      // Validate file size (max 2MB)
      if (file.size > 2 * 1024 * 1024) {
        this.toastService.error('Error', 'Image size should be less than 2MB');
        return;
      }
      
      const reader = new FileReader();
      reader.onload = (e: any) => {
        // Store base64 string (remove data:image/...;base64, prefix)
        const base64String = e.target.result.split(',')[1];
        this.logoPreview = e.target.result;
        // Store in form for submission
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
        ? Number(formValue.defaultGstPercentage) : null
    };

    // Use HttpClient directly since API service put requires ID, but company-settings doesn't use ID
    this.http.put<CompanySettings>(`${this.apiService.getBaseUrl()}/company-settings`, settings).subscribe({
      next: () => {
        this.loading = false;
        this.toastService.success('Success', 'Company settings saved successfully');
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to save settings');
      }
    });
  }
}

