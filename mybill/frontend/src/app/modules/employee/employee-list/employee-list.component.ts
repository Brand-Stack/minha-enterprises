import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ExcelExportService } from '../../../core/services/excel-export.service';

interface Employee {
  id: string;
  employeeCode: string;
  employeeName: string;
  category?: string;
  designation?: string;
  gender?: string;
  dateOfBirth?: string;
  dateOfJoining?: string;
  phone?: string;
  email?: string;
  address?: string;
  status?: string;
  role?: string;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-employee-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <h1>Employees</h1>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" 
                   (keyup.enter)="search()" 
                   placeholder="Search by code, name, email, phone...">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="createNew()" *appHasPermission="'EMPLOYEES:create'">
            <mat-icon>add</mat-icon>
            Add Employee
          </button>
        </div>
      </div>

      <mat-card>
        <div class="table-header-actions">
          <button mat-raised-button color="accent" (click)="exportToExcel()" [disabled]="loading" *appHasPermission="'EMPLOYEES:export'">
            <mat-icon>download</mat-icon>
            Export to Excel
          </button>
        </div>

        <table mat-table [dataSource]="dataSource" class="w-full">
          <ng-container matColumnDef="employeeCode">
            <th mat-header-cell *matHeaderCellDef>Code</th>
            <td mat-cell *matCellDef="let row">{{ row.employeeCode }}</td>
          </ng-container>

          <ng-container matColumnDef="employeeName">
            <th mat-header-cell *matHeaderCellDef>Name</th>
            <td mat-cell *matCellDef="let row">{{ row.employeeName }}</td>
          </ng-container>

          <ng-container matColumnDef="category">
            <th mat-header-cell *matHeaderCellDef>Category</th>
            <td mat-cell *matCellDef="let row">{{ row.category || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="designation">
            <th mat-header-cell *matHeaderCellDef>Designation</th>
            <td mat-cell *matCellDef="let row">{{ row.designation || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="phone">
            <th mat-header-cell *matHeaderCellDef>Phone</th>
            <td mat-cell *matCellDef="let row">{{ row.phone || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>Email</th>
            <td mat-cell *matCellDef="let row">{{ row.email || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <span [class]="row.status === 'ACTIVE' ? 'status-active' : 'status-inactive'">
                {{ row.status || 'ACTIVE' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="lastUpdatedBy">
            <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
            <td mat-cell *matCellDef="let row">{{ row.lastUpdatedBy || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="view(row.id)">
                  <mat-icon>visibility</mat-icon>
                  <span>View</span>
                </button>
                <button mat-menu-item (click)="edit(row.id)" [appDisableIfNoPermission]="'EMPLOYEES:edit'">
                  <mat-icon>edit</mat-icon>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="delete(row.id)" *appHasPermission="'EMPLOYEES:delete'">
                  <mat-icon>delete</mat-icon>
                  <span>Delete</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <mat-paginator [length]="totalElements" 
                       [pageIndex]="page"
                       [pageSize]="pageSize" 
                       [pageSizeOptions]="[10, 25, 50, 100]" 
                       (page)="onPageChange($event)"
                       showFirstLastButtons></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-actions { display: flex; gap: 16px; align-items: center; }
    .search-field { width: 300px; }
    .table-header-actions { display: flex; justify-content: flex-end; margin-bottom: 16px; }
    table { width: 100%; }
    .status-active { color: #059669; font-weight: 500; }
    .status-inactive { color: #DC2626; font-weight: 500; }
  `]
})
export class EmployeeListComponent implements OnInit {
  displayedColumns: string[] = ['employeeCode', 'employeeName', 'category', 'designation', 'phone', 'email', 'status', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<Employee>([]);
  
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  loading = false;
  searchTerm = '';
  page = 0;
  pageSize = 10;
  totalElements = 0;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toastService: ToastService,
    private excelExportService: ExcelExportService
  ) {}

  ngOnInit() {
    this.loadData();
  }

  ngAfterViewInit() {
    // Server-side pagination: do not assign dataSource.paginator
  }

  loadData() {
    this.loading = true;
    this.apiService.getPaged<Employee>('/employees', this.page, this.pageSize).subscribe({
      next: (response) => {
        this.loading = false;
        this.dataSource.data = response.content;
        this.totalElements = response.totalElements;
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load employees');
      }
    });
  }

  search() {
    this.loading = true;
    if (this.searchTerm) {
      this.apiService.get<PageResponse<Employee>>(`/employees/search?searchTerm=${this.searchTerm}&page=${this.page}&size=${this.pageSize}`)
        .subscribe({
          next: (response) => {
            this.loading = false;
            this.dataSource.data = response.content;
            this.totalElements = response.totalElements;
          },
          error: () => {
            this.loading = false;
            this.toastService.error('Error', 'Search failed');
          }
        });
    } else {
      this.loadData();
    }
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.searchTerm) {
      this.search();
    } else {
      this.loadData();
    }
  }

  createNew() {
    this.router.navigate(['/employees/create']);
  }

  view(id: string) {
    this.router.navigate(['/employees/view', id]);
  }

  edit(id: string) {
    this.router.navigate(['/employees/edit', id]);
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this employee?')) {
      this.apiService.delete('/employees', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Employee deleted');
          this.loadData();
        },
        error: () => {
          this.toastService.error('Error', 'Failed to delete employee');
        }
      });
    }
  }

  async exportToExcel() {
    if (this.dataSource.data.length === 0) {
      this.toastService.warning('Warning', 'No data to export');
      return;
    }
    
    const headers = ['Employee Code', 'Employee Name', 'Category', 'Designation', 'Gender', 'Date of Birth', 'Date of Joining', 'Phone', 'Email', 'Address', 'Status', 'Role'];
    const exportData = this.dataSource.data.map(emp => ({
      'Employee Code': emp.employeeCode || '',
      'Employee Name': emp.employeeName || '',
      'Category': emp.category || '',
      'Designation': emp.designation || '',
      'Gender': emp.gender || '',
      'Date of Birth': emp.dateOfBirth ? new Date(emp.dateOfBirth).toLocaleDateString() : '',
      'Date of Joining': emp.dateOfJoining ? new Date(emp.dateOfJoining).toLocaleDateString() : '',
      'Phone': emp.phone || '',
      'Email': emp.email || '',
      'Address': emp.address || '',
      'Status': emp.status || '',
      'Role': emp.role || ''
    }));
    
    try {
      await this.excelExportService.exportToExcel(exportData, 'Employees', headers);
      this.toastService.success('Success', 'Excel file downloaded successfully');
    } catch (error) {
      this.toastService.error('Error', 'Failed to generate Excel file');
    }
  }
}
