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
      <mat-card class="max-w-4xl mx-auto">
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
}

