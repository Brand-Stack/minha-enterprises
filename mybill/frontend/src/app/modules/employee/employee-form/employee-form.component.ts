import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

interface Employee {
  id?: string;
  employeeCode?: string;
  employeeName: string;
  category?: string;
  designation?: string;
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  dateOfBirth?: string;
  dateOfJoining?: string;
  phone?: string;
  email?: string;
  address?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  role?: 'ADMIN' | 'BILLER' | 'CASHIER';
  categoryId?: string;
  categoryName?: string;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-employee-form',
  template: `
    <div class="page-container">
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="w-full">
        <div class="card-header">
          <h2>{{ isEdit ? 'Edit' : 'Create' }} Employee</h2>
          <button mat-icon-button (click)="cancel()" class="close-btn">
            <mat-icon>close</mat-icon>
          </button>
        </div>

        <mat-card-content>
          <form [formGroup]="employeeForm" (ngSubmit)="onSubmit()" class="space-y-4">
            <!-- Basic Information -->
            <div class="form-section">
              <h3 class="section-title">Basic Information</h3>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Employee Code</mat-label>
                  <input matInput formControlName="employeeCode" [readonly]="isEdit" [disabled]="isEdit">
                  <mat-hint *ngIf="!isEdit">Auto-generated if left empty</mat-hint>
                  <mat-hint *ngIf="isEdit">Employee code cannot be changed</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Employee Name *</mat-label>
                  <input matInput formControlName="employeeName" required>
                  <mat-error *ngIf="employeeForm.get('employeeName')?.hasError('required')">Employee name is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Category</mat-label>
                  <mat-select formControlName="category">
                    <mat-option *ngFor="let cat of categories" [value]="cat.name">{{ cat.name }}</mat-option>
                  </mat-select>
                  <mat-hint>From Master Data (Employee Category). Drives entitlement permissions.</mat-hint>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Designation</mat-label>
                  <input matInput formControlName="designation">
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Gender</mat-label>
                  <mat-select formControlName="gender">
                    <mat-option value="MALE">Male</mat-option>
                    <mat-option value="FEMALE">Female</mat-option>
                    <mat-option value="OTHER">Other</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>
            </div>

            <!-- Dates -->
            <div class="form-section">
              <h3 class="section-title">Dates</h3>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Date of Birth</mat-label>
                  <input matInput [matDatepicker]="dobPicker" formControlName="dateOfBirth">
                  <mat-datepicker-toggle matSuffix [for]="dobPicker"></mat-datepicker-toggle>
                  <mat-datepicker #dobPicker></mat-datepicker>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Date of Joining *</mat-label>
                  <input matInput [matDatepicker]="dojPicker" formControlName="dateOfJoining" required>
                  <mat-datepicker-toggle matSuffix [for]="dojPicker"></mat-datepicker-toggle>
                  <mat-datepicker #dojPicker></mat-datepicker>
                  <mat-error *ngIf="employeeForm.get('dateOfJoining')?.hasError('required')">Date of joining is required</mat-error>
                </mat-form-field>
              </div>
            </div>

            <!-- Contact Information -->
            <div class="form-section">
              <h3 class="section-title">Contact Information</h3>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Phone</mat-label>
                  <input matInput formControlName="phone" type="tel" pattern="[0-9]*">
                  <mat-error *ngIf="employeeForm.get('phone')?.hasError('pattern')">Phone number should contain only digits</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Email</mat-label>
                  <input matInput formControlName="email" type="email">
                  <mat-error *ngIf="employeeForm.get('email')?.hasError('email')">Invalid email format</mat-error>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Address</mat-label>
                <textarea matInput formControlName="address" rows="2"></textarea>
              </mat-form-field>
            </div>

            <!-- Status & Role -->
            <div class="form-section">
              <h3 class="section-title">Status & Role</h3>
              <div class="form-grid">
                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Status *</mat-label>
                  <mat-select formControlName="status" required>
                    <mat-option value="ACTIVE">Active</mat-option>
                    <mat-option value="INACTIVE">Inactive</mat-option>
                  </mat-select>
                  <mat-error *ngIf="employeeForm.get('status')?.hasError('required')">Status is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="w-full">
                  <mat-label>Role</mat-label>
                  <mat-select formControlName="role">
                    <mat-option value="ADMIN">Admin</mat-option>
                    <mat-option value="EMPLOYEE">Employee</mat-option>
                    <mat-option value="BILLING_USER">Billing User</mat-option>
                    <mat-option value="PARTY_USER">Party User</mat-option>
                    <mat-option value="BILLER">Biller</mat-option>
                    <mat-option value="CASHIER">Cashier</mat-option>
                  </mat-select>
                  <mat-hint>ADMIN bypasses all permission checks</mat-hint>
                </mat-form-field>
              </div>
            </div>

            <!-- Bank Accounts (Multiple Accounts Support) -->
            <div class="form-section" *ngIf="isEdit && employeeId">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                <h3 class="section-title" style="margin: 0;">Bank Account Details</h3>
                <button *ngIf="!showBankForm" type="button" mat-stroked-button color="primary" (click)="toggleBankForm(true)">
                  <mat-icon>add</mat-icon> Add Bank Account
                </button>
              </div>

              <!-- Bank Account Form -->
              <mat-card *ngIf="showBankForm" class="bank-form-card" style="margin-bottom: 20px; background: white; border: 1px solid #CBD5E1;">
                <mat-card-header style="margin-bottom: 16px;">
                  <mat-card-title style="font-size: 15px; font-weight: 700;">
                    Bank Account Details — {{ employeeForm.get('employeeName')?.value || 'Employee' }}
                  </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                  <form [formGroup]="bankForm" (ngSubmit)="submitBankForm()" class="space-y-3">
                    <div class="form-grid">
                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Account Holder Name *</mat-label>
                        <input matInput formControlName="accountHolderName" required placeholder="John Doe">
                        <mat-error *ngIf="bankForm.get('accountHolderName')?.hasError('required')">Account holder name is required</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Bank Name *</mat-label>
                        <input matInput formControlName="bankName" required placeholder="HDFC Bank, SBI, ICICI...">
                        <mat-error *ngIf="bankForm.get('bankName')?.hasError('required')">Bank name is required</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Account Number *</mat-label>
                        <input matInput [type]="hideAccountNumber ? 'password' : 'text'" formControlName="accountNumber" required>
                        <button type="button" mat-icon-button matSuffix (click)="hideAccountNumber = !hideAccountNumber" [attr.aria-label]="'Hide account number'" [attr.aria-pressed]="hideAccountNumber">
                          <mat-icon>{{ hideAccountNumber ? 'visibility_off' : 'visibility' }}</mat-icon>
                        </button>
                        <mat-error *ngIf="bankForm.get('accountNumber')?.hasError('required')">Account number is required</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Confirm Account Number *</mat-label>
                        <input matInput type="text" formControlName="confirmAccountNumber" required>
                        <mat-error *ngIf="bankForm.get('confirmAccountNumber')?.hasError('required')">Confirm account number is required</mat-error>
                        <mat-error *ngIf="bankForm.hasError('accountMismatch') && bankForm.get('confirmAccountNumber')?.touched">Account numbers do not match</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>IFSC Code *</mat-label>
                        <input matInput formControlName="ifscCode" required placeholder="HDFC0001234" style="text-transform: uppercase;">
                        <mat-error *ngIf="bankForm.get('ifscCode')?.hasError('required')">IFSC code is required</mat-error>
                        <mat-error *ngIf="bankForm.get('ifscCode')?.hasError('pattern')">Invalid IFSC format (e.g. HDFC0001234)</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Branch *</mat-label>
                        <input matInput formControlName="bankBranch" required placeholder="Main Branch">
                        <mat-error *ngIf="bankForm.get('bankBranch')?.hasError('required')">Branch name is required</mat-error>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="w-full">
                        <mat-label>Account Type *</mat-label>
                        <mat-select formControlName="accountType" required>
                          <mat-option value="SAVINGS">Savings Account</mat-option>
                          <mat-option value="CURRENT">Current Account</mat-option>
                          <mat-option value="SALARY">Salary Account</mat-option>
                        </mat-select>
                        <mat-error *ngIf="bankForm.get('accountType')?.hasError('required')">Account type is required</mat-error>
                      </mat-form-field>
                    </div>

                    <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px;">
                      <button type="button" mat-button (click)="toggleBankForm(false)">Cancel</button>
                      <button type="submit" mat-raised-button color="primary" [disabled]="bankForm.invalid || savingBank">
                        <span *ngIf="!savingBank">Save</span>
                        <span *ngIf="savingBank">Saving...</span>
                      </button>
                    </div>
                  </form>
                </mat-card-content>
              </mat-card>

              <div class="table-container" *ngIf="bankAccounts && bankAccounts.length > 0">
                <table class="bank-table">
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Bank Name</th>
                      <th>Account Holder</th>
                      <th>Account Number</th>
                      <th>IFSC Code</th>
                      <th>Type</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let acc of bankAccounts">
                      <td>
                        <span *ngIf="acc.isPrimary" class="primary-badge"><mat-icon>check_circle</mat-icon> PRIMARY</span>
                        <button *ngIf="!acc.isPrimary" type="button" mat-button class="btn-set-primary" (click)="setPrimaryAccount(acc.id!)">Set Primary</button>
                      </td>
                      <td><strong>{{ acc.bankName }}</strong></td>
                      <td>{{ acc.accountHolderName || '—' }}</td>
                      <td>
                        <code>{{ maskAccountNumber(acc.accountNumber) }}</code>
                      </td>
                      <td>{{ acc.ifscCode || '—' }}</td>
                      <td><span class="type-chip">{{ acc.accountType || 'SAVINGS' }}</span></td>
                      <td>
                        <button type="button" mat-icon-button color="warn" (click)="deleteBankAccount(acc.id!)" matTooltip="Delete Account">
                          <mat-icon>delete</mat-icon>
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p *ngIf="!bankAccounts || bankAccounts.length === 0" class="text-muted" style="font-size: 13px; margin: 0;">No bank accounts added yet.</p>
            </div>

            <!-- Password (only for new employees or if updating) -->
            <div class="form-section" *ngIf="!isEdit">
              <h3 class="section-title">Password</h3>
              <mat-form-field appearance="outline" class="w-full">
                <mat-label>Password *</mat-label>
                <input matInput type="password" formControlName="password" required>
                <mat-error *ngIf="employeeForm.get('password')?.hasError('required')">Password is required</mat-error>
              </mat-form-field>
            </div>

            <!-- Actions -->
            <app-last-updated-by-field *ngIf="isEdit" [value]="lastUpdatedBy"></app-last-updated-by-field>

            <div class="form-actions">
              <button mat-button type="button" (click)="cancel()">Cancel</button>
              <button mat-raised-button color="primary" type="submit" [disabled]="employeeForm.invalid || loading">
                <span *ngIf="!loading">{{ isEdit ? 'Update' : 'Create' }}</span>
                <span *ngIf="loading">Processing...</span>
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; animation: fadeSlideUp 0.3s ease-out; }
    .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .close-btn { color: #666; }
    .form-section { margin-bottom: 32px; padding: 24px; background: #F9FAFB; border-radius: 8px; }
    .section-title { font-size: 16px; font-weight: 600; color: #1A1D2E; margin-bottom: 16px; }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 16px; }
    .form-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; }
    .w-full { width: 100%; }

    .table-container { width: 100%; overflow-x: auto; margin-top: 8px; }
    .bank-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
    .bank-table th { background: #E2E8F0; color: #334155; font-weight: 700; padding: 10px 12px; border-bottom: 2px solid #CBD5E1; }
    .bank-table td { padding: 10px 12px; border-bottom: 1px solid #E2E8F0; }
    .primary-badge { background: #DCFCE7; color: #15803D; font-weight: 800; padding: 4px 8px; border-radius: 12px; font-size: 11px; display: inline-flex; align-items: center; gap: 4px; }
    .primary-badge mat-icon { font-size: 14px; width: 14px; height: 14px; }
    .btn-set-primary { color: #2563EB !important; font-weight: 600 !important; font-size: 12px !important; }
    .type-chip { background: #F1F5F9; color: #475569; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; }
  `]
})
export class EmployeeFormComponent implements OnInit {
  employeeForm!: FormGroup;
  loading = false;
  employeeId: string | null = null;
  isEdit = false;
  lastUpdatedBy = '';
  categories: any[] = [];
  showNewCategoryInput = false;

  bankForm!: FormGroup;
  bankAccounts: any[] = [];
  showBankForm = false;
  savingBank = false;
  hideAccountNumber = true;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private toastService: ToastService
  ) {
    this.employeeForm = this.fb.group({
      employeeCode: [''],
      employeeName: ['', Validators.required],
      category: [''],
      newCategory: [''],
      designation: [''],
      gender: [''],
      dateOfBirth: [null],
      dateOfJoining: [new Date(), Validators.required],
      phone: ['', [Validators.pattern(/^[0-9]*$/)]],
      email: ['', Validators.email],
      address: [''],
      status: ['ACTIVE', Validators.required],
      role: ['EMPLOYEE'],
      password: ['', this.isEdit ? [] : [Validators.required]]
    });

    this.initBankForm();
  }

  initBankForm(): void {
    this.bankForm = this.fb.group({
      accountHolderName: ['', Validators.required],
      bankName: ['', Validators.required],
      accountNumber: ['', Validators.required],
      confirmAccountNumber: ['', Validators.required],
      ifscCode: ['', [Validators.required, Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/i)]],
      bankBranch: ['', Validators.required],
      accountType: ['SAVINGS', Validators.required]
    }, { validators: this.accountMatchValidator });
  }

  accountMatchValidator(g: FormGroup) {
    const acc = g.get('accountNumber')?.value;
    const confirmAcc = g.get('confirmAccountNumber')?.value;
    return acc === confirmAcc ? null : { accountMismatch: true };
  }

  toggleBankForm(show: boolean): void {
    this.showBankForm = show;
    if (show) {
      this.initBankForm();
      const empName = this.employeeForm.get('employeeName')?.value || '';
      if (empName) {
        this.bankForm.patchValue({ accountHolderName: empName });
      }
    }
  }

  submitBankForm(): void {
    if (this.bankForm.invalid || !this.employeeId) {
      this.bankForm.markAllAsTouched();
      return;
    }

    this.savingBank = true;
    const val = this.bankForm.value;
    const payload = {
      bankName: val.bankName.trim(),
      accountHolderName: val.accountHolderName.trim(),
      accountNumber: val.accountNumber.trim(),
      ifscCode: val.ifscCode.trim().toUpperCase(),
      bankBranch: val.bankBranch.trim(),
      accountType: val.accountType,
      isPrimary: this.bankAccounts.length === 0
    };

    this.apiService.post<any[]>(`/employees/${this.employeeId}/bank-accounts`, payload).subscribe({
      next: (updatedList) => {
        this.savingBank = false;
        this.showBankForm = false;
        this.bankAccounts = updatedList || [];
        this.toastService.success('Success', 'Bank details saved successfully');
      },
      error: (err) => {
        this.savingBank = false;
        this.toastService.error('Error', err?.error?.message || 'Unable to save bank details');
      }
    });
  }

  setPrimaryAccount(accountId: string): void {
    if (!this.employeeId) return;
    this.apiService.put<any[]>(`/employees/${this.employeeId}/bank-accounts/${accountId}/set-primary`, {}).subscribe({
      next: (updatedList) => {
        this.bankAccounts = updatedList || [];
        this.toastService.success('Success', 'Primary bank account updated successfully');
      },
      error: (err) => this.toastService.error('Error', err?.error?.message || 'Unable to update primary bank account')
    });
  }

  deleteBankAccount(accountId: string): void {
    if (!this.employeeId) return;
    if (confirm('Are you sure you want to delete this bank account?')) {
      this.apiService.delete<any[]>(`/employees/${this.employeeId}/bank-accounts`, accountId).subscribe({
        next: (updatedList) => {
          this.bankAccounts = updatedList || [];
          this.toastService.success('Success', 'Bank account deleted successfully');
        },
        error: (err) => this.toastService.error('Error', err?.error?.message || 'Unable to delete bank account')
      });
    }
  }

  maskAccountNumber(accNum?: string): string {
    if (!accNum) return '—';
    if (accNum.length <= 4) return accNum;
    const visible = accNum.slice(-4);
    const masked = '•'.repeat(Math.min(accNum.length - 4, 8));
    return masked + visible;
  }

  ngOnInit() {
    this.loadCategories();
    
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.employeeId = id;
      this.employeeForm.get('password')?.clearValidators();
      this.employeeForm.get('password')?.updateValueAndValidity();
      this.loadEmployee(id);
      this.loadBankAccounts(id);
    }
  }

  loadCategories() {
    this.apiService.get<any[]>('/master-data/type/EMPLOYEE_CATEGORY').subscribe({
      next: (categories) => {
        this.categories = (categories || [])
          .filter((c) => c.active !== false)
          .map((c) => ({ name: c.role || c.name }));
      },
      error: () => {
        this.apiService.get<string[]>('/employees/categories').subscribe({
          next: (cats) => {
            this.categories = cats.map(cat => ({ name: cat }));
          },
          error: () => {
            this.categories = [];
          }
        });
      }
    });
  }

  loadEmployee(id: string) {
    this.loading = true;
    this.apiService.get<Employee>(`/employees/${id}`).subscribe({
      next: (employee) => {
        this.loading = false;
        this.lastUpdatedBy = employee.lastUpdatedBy ?? '';
        this.employeeForm.patchValue({
          employeeCode: employee.employeeCode || '',
          employeeName: employee.employeeName || '',
          category: employee.category || '',
          designation: employee.designation || '',
          gender: employee.gender || '',
          dateOfBirth: employee.dateOfBirth ? new Date(employee.dateOfBirth) : null,
          dateOfJoining: employee.dateOfJoining ? new Date(employee.dateOfJoining) : new Date(),
          phone: employee.phone || '',
          email: employee.email || '',
          address: employee.address || '',
          status: employee.status || 'ACTIVE',
          role: employee.role || 'EMPLOYEE'
        });
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load employee');
        this.router.navigate(['/employees']);
      }
    });
  }

  onCategoryChange(event: any) {
    if (event.value === '__NEW__') {
      this.showNewCategoryInput = true;
      this.employeeForm.patchValue({ category: '' });
    } else {
      this.showNewCategoryInput = false;
    }
  }

  // Categories are now managed in Master Data module - removed addNewCategory

  onSubmit() {
    if (this.employeeForm.valid) {
      this.loading = true;
      const formValue = { ...this.employeeForm.value };
      
      // Remove newCategory from submission
      delete formValue.newCategory;
      
      // Convert dates to ISO strings
      if (formValue.dateOfBirth) {
        formValue.dateOfBirth = formValue.dateOfBirth.toISOString().split('T')[0];
      }
      if (formValue.dateOfJoining) {
        formValue.dateOfJoining = formValue.dateOfJoining.toISOString().split('T')[0];
      }
      
      // For new employees, omit employeeCode if empty (will be auto-generated)
      if (!this.isEdit && (!formValue.employeeCode || formValue.employeeCode.trim() === '')) {
        delete formValue.employeeCode;
      }

      if (this.isEdit && this.employeeId) {
        // Don't send password if not provided during update
        if (!formValue.password || formValue.password.trim() === '') {
          delete formValue.password;
        }
        this.apiService.put('/employees', this.employeeId, formValue).subscribe({
          next: () => {
            this.loading = false;
            this.toastService.success('Success', 'Employee updated successfully');
            this.router.navigate(['/employees']);
          },
          error: (err) => {
            this.loading = false;
            this.toastService.error('Error', err.error?.message || 'Failed to update employee');
          }
        });
      } else {
        this.apiService.post('/employees', formValue).subscribe({
          next: () => {
            this.loading = false;
            this.toastService.success('Success', 'Employee created successfully');
            this.router.navigate(['/employees']);
          },
          error: (err) => {
            this.loading = false;
            this.toastService.error('Error', err.error?.message || 'Failed to create employee');
          }
        });
      }
    }
  }

  cancel() {
    this.router.navigate(['/employees']);
  }

  // --- BANK ACCOUNTS MANAGEMENT ---

  loadBankAccounts(empId: string): void {
    this.apiService.get<any[]>(`/employees/${empId}/bank-accounts`).subscribe({
      next: (list) => this.bankAccounts = list || [],
      error: (err) => console.error('Failed to load bank accounts', err)
    });
  }
}

