import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../../../core/services/api.service';

export interface KpiModalData {
  title: string;
  category: 'PRESENT' | 'ABSENT' | 'LEAVE' | 'LATE' | 'OVERTIME';
  startDate?: string;
  endDate?: string;
  department?: string;
}

@Component({
  selector: 'app-attendance-summary-modal',
  template: `
    <div class="kpi-modal-container">
      <div class="modal-header">
        <div>
          <h2 class="modal-title">{{ data.title }}</h2>
          <p class="modal-sub">{{ records.length }} employee record(s) found</p>
        </div>
        <button mat-icon-button (click)="close()" class="close-btn">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <!-- Filter / Search bar -->
      <div class="filter-bar">
        <mat-form-field appearance="outline" class="search-field">
          <mat-label>Search Employee</mat-label>
          <input matInput [(ngModel)]="searchQuery" (ngModelChange)="applySearch()" placeholder="Name or code...">
          <mat-icon matSuffix>search</mat-icon>
        </mat-form-field>
      </div>

      <!-- Content Table -->
      <div class="table-container">
        <div *ngIf="loading" class="loading-state">
          <mat-spinner diameter="40"></mat-spinner>
          <p>Loading attendance details...</p>
        </div>

        <table *ngIf="!loading && filteredRecords.length > 0" class="kpi-table">
          <thead>
            <tr>
              <th>Employee</th>
              <th>Status</th>
              <th>Check-In</th>
              <th>Check-Out</th>
              <th>Working Hours</th>
              <th>Overtime</th>
              <th>Remarks</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of pagedRecords">
              <td>
                <div class="emp-cell">
                  <span class="emp-name">{{ r.employeeName || '—' }}</span>
                  <span class="emp-code" *ngIf="r.employeeCode">({{ r.employeeCode }})</span>
                </div>
              </td>
              <td>
                <span class="status-badge" [ngClass]="getStatusClass(r.status)">
                  {{ formatStatus(r.status) }}
                </span>
              </td>
              <td>{{ formatTime(r.checkInTime) }}</td>
              <td>{{ formatTime(r.checkOutTime) }}</td>
              <td><strong>{{ formatHours(r.totalWorkingHours) }}</strong></td>
              <td>
                <span *ngIf="r.overtimeHours && r.overtimeHours > 0" class="ot-chip">
                  +{{ formatHours(r.overtimeHours) }}
                </span>
                <span *ngIf="!r.overtimeHours || r.overtimeHours === 0">—</span>
              </td>
              <td class="remarks-cell">{{ r.remarks || r.correctionReason || '—' }}</td>
            </tr>
          </tbody>
        </table>

        <div *ngIf="!loading && filteredRecords.length === 0" class="empty-state">
          <mat-icon class="empty-icon">event_busy</mat-icon>
          <p>No employee records found for this category.</p>
        </div>
      </div>

      <!-- Pagination Footer -->
      <div class="modal-footer" *ngIf="!loading && filteredRecords.length > pageSize">
        <div class="page-info">
          Page {{ currentPage }} of {{ totalPages }}
        </div>
        <div class="page-actions">
          <button mat-stroked-button [disabled]="currentPage === 1" (click)="setPage(currentPage - 1)">Previous</button>
          <button mat-stroked-button [disabled]="currentPage === totalPages" (click)="setPage(currentPage + 1)">Next</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .kpi-modal-container {
      padding: 24px;
      max-width: 900px;
      min-width: 600px;
      background: white;
      border-radius: 12px;
    }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;
    }
    .modal-title {
      font-size: 20px;
      font-weight: 700;
      color: #0F172A;
      margin: 0;
    }
    .modal-sub {
      font-size: 13px;
      color: #64748B;
      margin: 4px 0 0 0;
    }
    .close-btn { color: #64748B; }
    .filter-bar {
      margin-bottom: 16px;
    }
    .search-field {
      width: 100%;
    }
    .table-container {
      max-height: 400px;
      overflow-y: auto;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
    }
    .loading-state, .empty-state {
      padding: 40px;
      text-align: center;
      color: #64748B;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }
    .empty-icon { font-size: 40px; width: 40px; height: 40px; color: #94A3B8; }
    .kpi-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }
    .kpi-table th {
      background: #F8FAFC;
      color: #475569;
      font-weight: 700;
      padding: 12px 14px;
      border-bottom: 1px solid #E2E8F0;
      position: sticky;
      top: 0;
      z-index: 5;
    }
    .kpi-table td {
      padding: 12px 14px;
      border-bottom: 1px solid #F1F5F9;
    }
    .emp-cell { display: flex; flex-direction: column; }
    .emp-name { font-weight: 600; color: #0F172A; }
    .emp-code { font-size: 11px; color: #64748B; }
    .status-badge {
      padding: 4px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-block;
    }
    .status-present { background: #DCFCE7; color: #15803D; }
    .status-absent { background: #FEE2E2; color: #B91C1C; }
    .status-leave { background: #FEF3C7; color: #B45309; }
    .status-late { background: #FFEDD5; color: #C2410C; }
    .ot-chip { background: #F3E8FF; color: #7E22CE; font-weight: 700; padding: 2px 6px; border-radius: 4px; font-size: 11px; }
    .remarks-cell { max-width: 160px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #64748B; }
    .modal-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 16px;
      padding-top: 12px;
      border-top: 1px solid #E2E8F0;
    }
    .page-info { font-size: 13px; color: #64748B; }
    .page-actions { display: flex; gap: 8px; }
  `]
})
export class AttendanceSummaryModalComponent implements OnInit {
  records: any[] = [];
  filteredRecords: any[] = [];
  pagedRecords: any[] = [];
  loading = true;
  searchQuery = '';

  currentPage = 1;
  pageSize = 10;
  totalPages = 1;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: KpiModalData,
    private dialogRef: MatDialogRef<AttendanceSummaryModalComponent>,
    private apiService: ApiService
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    const params: Record<string, string> = {};
    if (this.data.category) params['category'] = this.data.category;
    if (this.data.startDate) params['startDate'] = this.data.startDate;
    if (this.data.endDate) params['endDate'] = this.data.endDate;
    if (this.data.department) params['department'] = this.data.department;

    this.apiService.get<any[]>('/attendance/kpi-details', params).subscribe({
      next: (list) => {
        this.records = list || [];
        this.applySearch();
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load KPI details', err);
        this.loading = false;
      }
    });
  }

  applySearch(): void {
    const q = (this.searchQuery || '').toLowerCase().trim();
    if (!q) {
      this.filteredRecords = [...this.records];
    } else {
      this.filteredRecords = this.records.filter(r =>
        (r.employeeName && r.employeeName.toLowerCase().includes(q)) ||
        (r.employeeCode && r.employeeCode.toLowerCase().includes(q)) ||
        (r.department && r.department.toLowerCase().includes(q))
      );
    }
    this.currentPage = 1;
    this.updatePagination();
  }

  updatePagination(): void {
    this.totalPages = Math.max(1, Math.ceil(this.filteredRecords.length / this.pageSize));
    const start = (this.currentPage - 1) * this.pageSize;
    this.pagedRecords = this.filteredRecords.slice(start, start + this.pageSize);
  }

  setPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.updatePagination();
    }
  }

  formatStatus(status?: string): string {
    if (!status) return '—';
    return status.replace(/_/g, ' ');
  }

  getStatusClass(status?: string): string {
    if (!status) return '';
    const s = status.toUpperCase();
    if (s.includes('PRESENT') || s.includes('WORK_FROM_HOME')) return 'status-present';
    if (s.includes('ABSENT')) return 'status-absent';
    if (s.includes('LEAVE') || s.includes('HALF_DAY')) return 'status-leave';
    if (s.includes('LATE')) return 'status-late';
    return '';
  }

  formatTime(timeStr?: string): string {
    if (!timeStr) return '—';
    try {
      const dt = new Date(timeStr);
      return dt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return timeStr;
    }
  }

  formatHours(hrs?: number): string {
    if (hrs == null || hrs === 0) return '0h';
    const totalMinutes = Math.round(hrs * 60);
    const h = Math.floor(totalMinutes / 60);
    const m = totalMinutes % 60;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  }

  close(): void {
    this.dialogRef.close();
  }
}
