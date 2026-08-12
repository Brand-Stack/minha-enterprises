import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-master-data-form',
  template: `
    <div class="page-container">
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>
      <mat-card class="max-w-2xl mx-auto">
        <mat-card-header>
          <mat-card-title>{{ isEdit ? 'Edit' : 'Create' }} {{ isEmployeeCategory ? 'Employee Category' : 'Category' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Type *</mat-label>
              <mat-select formControlName="type" required [disabled]="isEdit || typeLocked">
                <mat-option value="ITEM_CATEGORY">Item Category</mat-option>
                <mat-option value="ITEM_UNIT">Item Unit</mat-option>
                <mat-option value="EMPLOYEE_CATEGORY">Employee Category</mat-option>
                <mat-option value="EXPENSE_CATEGORY">Expense Category</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full" *ngIf="!isEmployeeCategory">
              <mat-label>Name *</mat-label>
              <input matInput formControlName="name" required>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full" *ngIf="isEmployeeCategory">
              <mat-label>Role *</mat-label>
              <input matInput formControlName="role" required placeholder="e.g. Accountant, Operations">
              <mat-error *ngIf="form.get('role')?.hasError('required')">Role is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Description</mat-label>
              <textarea matInput formControlName="description" rows="3"></textarea>
            </mat-form-field>

            <mat-checkbox formControlName="active" *ngIf="!isEmployeeCategory">Active</mat-checkbox>

            <app-last-updated-by-field *ngIf="isEdit" [value]="lastUpdatedBy"></app-last-updated-by-field>

            <div class="flex gap-4 mt-4">
              <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid || loading">
                {{ isEdit ? 'Update' : 'Create' }}
              </button>
              <button mat-button type="button" (click)="cancel()">Cancel</button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .w-full { width: 100%; }
  `]
})
export class MasterDataFormComponent implements OnInit {
  form: FormGroup;
  loading = false;
  isEdit = false;
  lastUpdatedBy = '';
  typeLocked = false;
  id: string | null = null;

  get isEmployeeCategory(): boolean {
    return this.form.get('type')?.value === 'EMPLOYEE_CATEGORY';
  }

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService
  ) {
    this.form = this.fb.group({
      type: ['ITEM_CATEGORY', Validators.required],
      name: ['', Validators.required],
      role: [''], // Not required by default, will be validated conditionally
      description: [''],
      active: [true]
    });
    
    // Watch for type changes to update role/name validation
    this.form.get('type')?.valueChanges.subscribe(type => {
      this.applyTypeValidators(type);
    });
  }

  private applyTypeValidators(type: string): void {
    const roleControl = this.form.get('role');
    const nameControl = this.form.get('name');
    if (type === 'EMPLOYEE_CATEGORY') {
      roleControl?.setValidators([Validators.required]);
      nameControl?.clearValidators();
      nameControl?.setValue('');
    } else {
      roleControl?.clearValidators();
      roleControl?.setValue('');
      nameControl?.setValidators([Validators.required]);
    }
    roleControl?.updateValueAndValidity();
    nameControl?.updateValueAndValidity();
  }

  ngOnInit() {
    const type = this.route.snapshot.queryParams['type'];
    if (type) {
      this.form.patchValue({ type });
      this.typeLocked = true;
      this.applyTypeValidators(type);
    }

    this.id = this.route.snapshot.paramMap.get('id');
    if (this.id) {
      this.isEdit = true;
      this.loadData();
    }
  }

  loadData() {
    this.loading = true;
    this.apiService.get<any>(`/master-data/${this.id}`).subscribe({
      next: (data) => {
        this.form.patchValue(data);
        this.lastUpdatedBy = data.lastUpdatedBy ?? '';
        this.applyTypeValidators(data.type);
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Failed to load category');
        this.loading = false;
      }
    });
  }

  private navigateBack(): void {
    const type = this.form.get('type')?.value;
    this.router.navigate(['/master/master-data'], { queryParams: { type } });
  }

  onSubmit() {
    if (this.form.invalid) return;

    this.loading = true;
    const data = { ...this.form.value };

    if (data.type === 'EMPLOYEE_CATEGORY') {
      // Employee categories are identified by role; name is derived for storage.
      data.name = data.role;
      data.active = true;
    } else {
      delete data.role;
    }

    if (this.isEdit && this.id) {
      this.apiService.put('/master-data', this.id, data).subscribe({
        next: () => {
          this.toastService.success('Success', 'Category updated successfully');
          this.navigateBack();
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to update category');
          this.loading = false;
        }
      });
    } else {
      this.apiService.post('/master-data', data).subscribe({
        next: () => {
          this.toastService.success('Success', 'Category created successfully');
          this.navigateBack();
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to create category');
          this.loading = false;
        }
      });
    }
  }

  cancel() {
    this.navigateBack();
  }
}

