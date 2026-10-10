import { Component, OnInit, Input, Output, EventEmitter, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormControl } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { AuthService } from '../../../core/services/auth.service';
import { PermissionService } from '../../../core/services/permission.service';

export interface SimpleEmployee {
  id: string;
  employeeCode?: string;
  employeeName: string;
  category?: string;
  designation?: string;
}

@Component({
  selector: 'app-employee-selector',
  template: `
    <mat-form-field appearance="outline" class="employee-selector-field">
      <mat-label>{{ label }}</mat-label>
      <mat-select [formControl]="selectControl" (selectionChange)="onSelectChange($event.value)" [disabled]="isDisabled">
        <mat-option *ngIf="allowAll && isAdmin" [value]="''">All Employees</mat-option>
        <div class="search-box-container" (click)="$event.stopPropagation()">
          <mat-icon class="search-icon">search</mat-icon>
          <input 
            type="text" 
            class="search-input" 
            placeholder="Type to filter..." 
            [formControl]="filterControl"
            (keydown)="$event.stopPropagation()">
        </div>
        <mat-option *ngFor="let emp of filteredEmployees" [value]="emp.id">
          <div class="emp-option-row">
            <span class="emp-name">{{ emp.employeeName }}</span>
            <span class="emp-code" *ngIf="emp.employeeCode">({{ emp.employeeCode }})</span>
          </div>
        </mat-option>
        <div *ngIf="filteredEmployees.length === 0" class="no-results">
          No matching employees
        </div>
      </mat-select>
    </mat-form-field>
  `,
  styles: [`
    .employee-selector-field {
      width: 100%;
      min-width: 200px;
    }
    .search-box-container {
      display: flex;
      align-items: center;
      padding: 8px 12px;
      position: sticky;
      top: 0;
      background: white;
      z-index: 10;
      border-bottom: 1px solid #E2E8F0;
    }
    .search-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #64748B;
      margin-right: 8px;
    }
    .search-input {
      width: 100%;
      border: none;
      outline: none;
      font-size: 13px;
      color: #0F172A;
      background: transparent;
    }
    .emp-option-row {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .emp-name {
      font-weight: 500;
    }
    .emp-code {
      font-size: 12px;
      color: #64748B;
    }
    .no-results {
      padding: 12px 16px;
      font-size: 13px;
      color: #94A3B8;
      text-align: center;
    }
  `],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EmployeeSelectorComponent),
      multi: true
    }
  ]
})
export class EmployeeSelectorComponent implements OnInit, ControlValueAccessor {
  @Input() label: string = 'Select Employee';
  @Input() allowAll: boolean = true;
  @Input() showLabel: boolean = true;
  @Output() employeeChange = new EventEmitter<string>();

  employees: SimpleEmployee[] = [];
  filteredEmployees: SimpleEmployee[] = [];

  selectControl = new FormControl<string>('');
  filterControl = new FormControl<string>('');

  isAdmin: boolean = false;
  isDisabled: boolean = false;
  currentEmpId: string | null = null;

  private onChange: (val: any) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(
    private apiService: ApiService,
    private authService: AuthService,
    private permissionService: PermissionService
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    const currentUser = this.authService.getCurrentUser();
    this.currentEmpId = currentUser?.employeeId || (currentUser as any)?.id || null;

    this.filterControl.valueChanges.subscribe(query => {
      this.filterEmployees(query || '');
    });

    this.loadEmployees();
  }

  loadEmployees(): void {
    this.apiService.get<any>('/employees/list').subscribe({
      next: (data) => {
        const rawList = Array.isArray(data) ? data : (data?.content || []);
        this.employees = rawList.map((e: any) => ({
          id: e.id,
          employeeCode: e.employeeCode,
          employeeName: e.employeeName || e.name || 'Employee',
          category: e.category,
          designation: e.designation
        }));
        this.filterEmployees(this.filterControl.value || '');

        if (!this.isAdmin) {
          // Employee role -> automatically lock to current logged-in employee
          const match = this.employees.find(e => e.id === this.currentEmpId || e.employeeCode === this.currentEmpId);
          const targetId = match ? match.id : (this.currentEmpId || '');
          this.selectControl.setValue(targetId);
          this.selectControl.disable();
          this.isDisabled = true;
          this.emitValue(targetId);
        }
      },
      error: (err) => console.error('Failed to load employees for selector', err)
    });
  }

  filterEmployees(query: string): void {
    const q = query.toLowerCase().trim();
    if (!q) {
      this.filteredEmployees = [...this.employees];
    } else {
      this.filteredEmployees = this.employees.filter(e =>
        (e.employeeName && e.employeeName.toLowerCase().includes(q)) ||
        (e.employeeCode && e.employeeCode.toLowerCase().includes(q)) ||
        (e.designation && e.designation.toLowerCase().includes(q))
      );
    }
  }

  onSelectChange(val: string): void {
    this.onChange(val);
    this.onTouched();
    this.employeeChange.emit(val);
  }

  emitValue(val: string): void {
    this.onChange(val);
    this.employeeChange.emit(val);
  }

  writeValue(value: any): void {
    if (value !== undefined) {
      this.selectControl.setValue(value, { emitEvent: false });
    }
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState?(isDisabled: boolean): void {
    if (!this.isAdmin) {
      this.selectControl.disable();
      this.isDisabled = true;
    } else {
      this.isDisabled = isDisabled;
      if (isDisabled) this.selectControl.disable();
      else this.selectControl.enable();
    }
  }
}
