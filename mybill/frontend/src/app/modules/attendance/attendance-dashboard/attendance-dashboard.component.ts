import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { PermissionService } from '../../../core/services/permission.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AttendanceRecord } from '../../../core/models/attendance.model';

@Component({
  selector: 'app-attendance-dashboard',
  template: `
    <div class="attendance-dashboard-container">
      <!-- Header Banner -->
      <div class="dashboard-header">
        <div>
          <h2>Attendance Dashboard</h2>
          <p class="subtitle">{{ isAdmin ? 'Organization-wide Attendance & Payroll Overview' : 'Personal Attendance & Leave Summary' }}</p>
        </div>
        <div class="header-actions">
          <button mat-raised-button color="primary" class="btn-punch-in" (click)="punch('IN')">
            <mat-icon>login</mat-icon> Punch In
          </button>
          <button mat-raised-button color="warn" class="btn-punch-out" (click)="punch('OUT')">
            <mat-icon>logout</mat-icon> Punch Out
          </button>
        </div>
      </div>

      <!-- Filter Controls Bar -->
      <div class="dashboard-filter-bar">
        <div class="filter-group">
          <div class="preset-toggle-container">
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'today'" (click)="selectPreset('today')">Today</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'yesterday'" (click)="selectPreset('yesterday')">Yesterday</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_week'" (click)="selectPreset('this_week')">This Week</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_month'" (click)="selectPreset('this_month')">This Month</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'month_year'" (click)="selectPreset('month_year')">Month / Year</button>
          </div>

          <div class="period-filter-wrapper" *ngIf="filterPreset === 'month_year'">
            <mat-icon class="calendar-icon">calendar_month</mat-icon>
            <select class="period-select" [(ngModel)]="selectedFilterMonth" (change)="onMonthYearChange()">
              <option *ngFor="let m of monthOptions" [ngValue]="m.value">{{ m.label }}</option>
            </select>
            <select class="period-select" [(ngModel)]="selectedFilterYear" (change)="onMonthYearChange()">
              <option *ngFor="let y of yearOptions" [ngValue]="y">{{ y }}</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Admin KPIs -->
      <div class="kpi-grid" *ngIf="isAdmin">
        <div class="kpi-card blue">
          <div class="kpi-icon"><mat-icon>groups</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.totalEmployees || 0 }}</span>
            <span class="kpi-label">Total Employees</span>
          </div>
        </div>

        <div class="kpi-card green clickable" (click)="openKpiModal('PRESENT', 'Present Employees')">
          <div class="kpi-icon"><mat-icon>check_circle</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.presentToday || 0 }}</span>
            <span class="kpi-label">Present</span>
          </div>
        </div>

        <div class="kpi-card red clickable" (click)="openKpiModal('ABSENT', 'Absent Employees')">
          <div class="kpi-icon"><mat-icon>cancel</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.absentToday || 0 }}</span>
            <span class="kpi-label">Absent</span>
          </div>
        </div>

        <div class="kpi-card orange clickable" (click)="openKpiModal('LEAVE', 'On Leave Employees')">
          <div class="kpi-icon"><mat-icon>flight_takeoff</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.onLeave || 0 }}</span>
            <span class="kpi-label">On Leave</span>
          </div>
        </div>

        <div class="kpi-card yellow clickable" (click)="openKpiModal('LATE', 'Late Arrival Employees')">
          <div class="kpi-icon"><mat-icon>access_time</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.lateEmployees || 0 }}</span>
            <span class="kpi-label">Late Arrival</span>
          </div>
        </div>

        <div class="kpi-card purple clickable" (click)="openKpiModal('OVERTIME', 'Total Overtime Hours')">
          <div class="kpi-icon"><mat-icon>timer</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ adminKpis?.totalOvertimeHours || 0 }} hrs</span>
            <span class="kpi-label">Total Overtime</span>
          </div>
        </div>
      </div>

      <!-- Employee Personal KPIs -->
      <div class="kpi-grid" *ngIf="!isAdmin">
        <div class="kpi-card blue">
          <div class="kpi-icon"><mat-icon>today</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ empKpis?.todayStatus || 'NOT CHECKED IN' }}</span>
            <span class="kpi-label">Today Status</span>
          </div>
        </div>

        <div class="kpi-card green">
          <div class="kpi-icon"><mat-icon>login</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ formatTime(empKpis?.checkInTime) }}</span>
            <span class="kpi-label">Check-In Time</span>
          </div>
        </div>

        <div class="kpi-card orange">
          <div class="kpi-icon"><mat-icon>logout</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ formatTime(empKpis?.checkOutTime) }}</span>
            <span class="kpi-label">Check-Out Time</span>
          </div>
        </div>

        <div class="kpi-card purple">
          <div class="kpi-icon"><mat-icon>schedule</mat-icon></div>
          <div class="kpi-content">
            <span class="kpi-value">{{ empKpis?.todayWorkingHours || 0 }} hrs</span>
            <span class="kpi-label">Working Hours</span>
          </div>
        </div>
      </div>

      <!-- Quick Links Section -->
      <div class="quick-links-section">
        <h3>Quick Actions</h3>
        <div class="quick-links-grid">
          <a routerLink="/attendance/my-attendance" class="action-card">
            <mat-icon>calendar_month</mat-icon>
            <span>My Attendance</span>
          </a>
          <a routerLink="/attendance/leaves" class="action-card">
            <mat-icon>event_note</mat-icon>
            <span>Leave Requests</span>
          </a>
          <a routerLink="/attendance/permissions" class="action-card">
            <mat-icon>more_time</mat-icon>
            <span>Permissions</span>
          </a>
          <a routerLink="/attendance/payslips" class="action-card">
            <mat-icon>receipt_long</mat-icon>
            <span>My Payslips</span>
          </a>
          <a routerLink="/attendance/salary-advances" class="action-card">
            <mat-icon>payments</mat-icon>
            <span>Salary Advances</span>
          </a>
          <a *ngIf="isAdmin" routerLink="/attendance/payroll" class="action-card admin">
            <mat-icon>account_balance</mat-icon>
            <span>Payroll Management</span>
          </a>
          <a *ngIf="isAdmin" routerLink="/attendance/devices" class="action-card admin">
            <mat-icon>fingerprint</mat-icon>
            <span>Biometric Devices</span>
          </a>
          <a *ngIf="isAdmin" routerLink="/attendance/settings" class="action-card admin">
            <mat-icon>settings</mat-icon>
            <span>Working Hours Settings</span>
          </a>
        </div>
      </div>

      <!-- KPI Details Modal Popup -->
      <div class="modal-overlay" *ngIf="openKpiModalFlag">
        <div class="modal-card wide">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">list_alt</mat-icon>
              <h3>{{ kpiModalTitle }} — Detailed Records</h3>
            </div>
            <button mat-icon-button (click)="closeKpiModal()"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body">
            <div *ngIf="loadingKpiDetails" class="loading-spinner">
              <p>Loading details...</p>
            </div>

            <div *ngIf="!loadingKpiDetails">
              <!-- Overtime Sum Highlight Header -->
              <div *ngIf="kpiCategory === 'OVERTIME'" class="ot-summary-banner">
                <mat-icon>timer</mat-icon>
                <span>Total Accumulated Overtime: <strong>{{ totalOvertimeSum | number:'1.1-2' }} hours</strong></span>
              </div>

              <table class="modal-table" *ngIf="kpiRecords.length > 0">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Date</th>
                    <th>Status</th>
                    <th>Check In</th>
                    <th>Check Out</th>
                    <th *ngIf="kpiCategory === 'OVERTIME'">OT Hours</th>
                    <th *ngIf="kpiCategory !== 'OVERTIME'">Working Hours</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let r of kpiRecords">
                    <td>
                      <div class="emp-cell">
                        <div class="emp-avatar">{{ r.employeeName?.charAt(0) || 'E' }}</div>
                        <div>
                          <div class="emp-name">{{ r.employeeName }}</div>
                          <div class="emp-code">{{ r.employeeCode }}</div>
                        </div>
                      </div>
                    </td>
                    <td>{{ r.date }}</td>
                    <td><span class="status-badge" [class]="r.status ? r.status.toLowerCase() : ''">{{ r.status }}</span></td>
                    <td>{{ formatTime(r.checkInTime) }}</td>
                    <td>{{ formatTime(r.checkOutTime) }}</td>
                    <td *ngIf="kpiCategory === 'OVERTIME'"><strong class="text-purple">{{ r.overtimeHours || 0 }} hrs</strong></td>
                    <td *ngIf="kpiCategory !== 'OVERTIME'">{{ r.totalWorkingHours || 0 }} hrs</td>
                  </tr>
                </tbody>
              </table>

              <div *ngIf="kpiRecords.length === 0" class="empty-state">
                <mat-icon class="empty-icon">check_circle_outline</mat-icon>
                <p>No employee records found for this category in the selected filter period.</p>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-flat-button color="primary" (click)="closeKpiModal()">Close</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .attendance-dashboard-container {
      padding: 24px;
      width: 100%;
      box-sizing: border-box;
    }
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }
    .dashboard-header h2 {
      font-size: 24px;
      font-weight: 700;
      color: #1A1D2E;
      margin: 0;
    }
    .subtitle {
      color: #6B7280;
      font-size: 14px;
      margin-top: 4px;
    }
    .header-actions {
      display: flex;
      gap: 12px;
    }
    .btn-punch-in {
      background: #10B981 !important;
      color: white !important;
      font-weight: 700;
      border-radius: 8px;
    }
    .btn-punch-out {
      background: #EF4444 !important;
      color: white !important;
      font-weight: 700;
      border-radius: 8px;
    }

    .dashboard-filter-bar {
      margin-bottom: 24px;
      padding: 12px 16px;
      background: #FFFFFF;
      border-radius: 12px;
      border: 1px solid #E2E8F0;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }
    .filter-group { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .preset-toggle-container {
      display: flex;
      background: #F1F5F9;
      border: 1px solid #CBD5E1;
      border-radius: 8px;
      padding: 3px;
      gap: 2px;
      flex-wrap: wrap;
    }
    .preset-btn {
      padding: 6px 12px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #64748B;
      font-weight: 700;
      font-size: 12.5px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .preset-btn.active {
      background: #2563EB;
      color: white;
      box-shadow: 0 1px 3px rgba(37, 99, 235, 0.3);
    }

    .period-filter-wrapper { 
      display: flex; 
      align-items: center; 
      background: #F8FAFC; 
      border: 1px solid #CBD5E1; 
      border-radius: 8px; 
      padding: 4px 10px; 
      gap: 6px; 
    }
    .calendar-icon { color: #2563EB; font-size: 18px; width: 18px; height: 18px; }
    .period-select { 
      border: none; 
      background: transparent; 
      outline: none; 
      font-size: 13px; 
      font-weight: 700; 
      color: #0F172A; 
      cursor: pointer; 
    }

    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }
    .kpi-card {
      background: white;
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      border-left: 4px solid #5B6FE8;
      transition: all 0.2s ease;
    }
    .kpi-card.clickable {
      cursor: pointer;
    }
    .kpi-card.clickable:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 8px rgba(0,0,0,0.1);
    }
    .kpi-card.blue { border-left-color: #5B6FE8; }
    .kpi-card.green { border-left-color: #10B981; }
    .kpi-card.red { border-left-color: #EF4444; }
    .kpi-card.orange { border-left-color: #F59E0B; }
    .kpi-card.yellow { border-left-color: #EAB308; }
    .kpi-card.purple { border-left-color: #8B5CF6; }
    .kpi-icon mat-icon {
      font-size: 32px;
      width: 32px;
      height: 32px;
      color: #5B6FE8;
    }
    .kpi-value {
      font-size: 20px;
      font-weight: 700;
      color: #1A1D2E;
      display: block;
    }
    .kpi-label {
      font-size: 12px;
      color: #6B7280;
      text-transform: uppercase;
      font-weight: 600;
    }
    .quick-links-section h3 {
      font-size: 18px;
      font-weight: 600;
      color: #1A1D2E;
      margin-bottom: 16px;
    }
    .quick-links-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
    }
    .action-card {
      background: white;
      border-radius: 10px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
      text-decoration: none;
      color: #1A1D2E;
      font-weight: 600;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
      border: 1px solid #E5E7EB;
      transition: all 0.2s ease;
    }
    .action-card:hover {
      border-color: #5B6FE8;
      transform: translateY(-2px);
      box-shadow: 0 4px 6px rgba(0,0,0,0.08);
    }
    .action-card mat-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      color: #5B6FE8;
    }
    .action-card.admin mat-icon {
      color: #8B5CF6;
    }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); }
    .modal-card { background: white; border-radius: 16px; width: 500px; max-width: 92%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
    .modal-card.wide { width: 700px; max-width: 95%; }
    .modal-header { padding: 18px 24px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center; }
    .modal-title { display: flex; align-items: center; gap: 10px; }
    .modal-title h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0; }
    .modal-body { padding: 24px; max-height: 480px; overflow-y: auto; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }

    .loading-spinner { text-align: center; padding: 24px; color: #64748B; font-weight: 600; }
    .ot-summary-banner { background: #F3E8FF; border: 1px solid #E9D5FF; color: #6B21A8; padding: 12px 16px; border-radius: 8px; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }

    .modal-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .modal-table th { background: #F8FAFC; text-align: left; padding: 10px 12px; border-bottom: 1px solid #E2E8F0; color: #475569; font-weight: 700; }
    .modal-table td { padding: 10px 12px; border-bottom: 1px solid #F1F5F9; color: #0F172A; }

    .emp-cell { display: flex; align-items: center; gap: 10px; }
    .emp-avatar { width: 32px; height: 32px; border-radius: 50%; background: #EFF6FF; color: #1D4ED8; font-weight: 700; font-size: 13px; display: flex; align-items: center; justify-content: center; }
    .emp-name { font-weight: 600; color: #0F172A; }
    .emp-code { font-size: 11px; color: #64748B; }

    .status-badge { padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 800; text-transform: uppercase; }
    .status-badge.present { background: #DCFCE7; color: #15803D; }
    .status-badge.absent { background: #FEE2E2; color: #B91C1C; }
    .status-badge.late { background: #FEF3C7; color: #B45309; }
    .status-badge.leave { background: #DBEAFE; color: #1D4ED8; }
    .status-badge.overtime { background: #F3E8FF; color: #7E22CE; }

    .empty-state { text-align: center; padding: 32px 16px; color: #64748B; }
    .empty-icon { font-size: 40px; width: 40px; height: 40px; color: #CBD5E1; margin-bottom: 6px; }
    .text-purple { color: #7C3AED; }
  `]
})
export class AttendanceDashboardComponent implements OnInit {
  isAdmin = false;
  adminKpis: any;
  empKpis: any;

  filterPreset: 'today' | 'yesterday' | 'this_week' | 'this_month' | 'month_year' = 'today';
  selectedFilterMonth: number = new Date().getMonth();
  selectedFilterYear: number = new Date().getFullYear();

  startDate: Date = new Date();
  endDate: Date = new Date();

  openKpiModalFlag = false;
  kpiModalTitle = '';
  kpiCategory = '';
  kpiRecords: AttendanceRecord[] = [];
  loadingKpiDetails = false;

  monthOptions = [
    { value: 0, label: 'January' },
    { value: 1, label: 'February' },
    { value: 2, label: 'March' },
    { value: 3, label: 'April' },
    { value: 4, label: 'May' },
    { value: 5, label: 'June' },
    { value: 6, label: 'July' },
    { value: 7, label: 'August' },
    { value: 8, label: 'September' },
    { value: 9, label: 'October' },
    { value: 10, label: 'November' },
    { value: 11, label: 'December' }
  ];
  yearOptions: number[] = [2021, 2022, 2023, 2024, 2025, 2026, 2027, 2028, 2029, 2030, 2031];

  constructor(
    private attendanceService: AttendanceService,
    private permissionService: PermissionService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    this.selectPreset('today');
  }

  selectPreset(preset: 'today' | 'yesterday' | 'this_week' | 'this_month' | 'month_year'): void {
    this.filterPreset = preset;
    const now = new Date();

    if (preset === 'today') {
      this.startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      this.endDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    } else if (preset === 'yesterday') {
      const y = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
      this.startDate = y;
      this.endDate = y;
    } else if (preset === 'this_week') {
      const day = now.getDay();
      const diffToMon = now.getDate() - day + (day === 0 ? -6 : 1);
      const monday = new Date(now.getFullYear(), now.getMonth(), diffToMon);
      const sunday = new Date(now.getFullYear(), now.getMonth(), diffToMon + 6);
      this.startDate = monday;
      this.endDate = sunday;
    } else if (preset === 'this_month') {
      this.startDate = new Date(now.getFullYear(), now.getMonth(), 1);
      this.endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    } else if (preset === 'month_year') {
      this.startDate = new Date(this.selectedFilterYear, this.selectedFilterMonth, 1);
      this.endDate = new Date(this.selectedFilterYear, this.selectedFilterMonth + 1, 0);
    }
    this.loadKpis();
  }

  onMonthYearChange(): void {
    if (this.filterPreset === 'month_year') {
      this.startDate = new Date(this.selectedFilterYear, this.selectedFilterMonth, 1);
      this.endDate = new Date(this.selectedFilterYear, this.selectedFilterMonth + 1, 0);
      this.loadKpis();
    }
  }

  loadKpis(): void {
    const startStr = this.formatIsoDate(this.startDate);
    const endStr = this.formatIsoDate(this.endDate);

    if (this.isAdmin) {
      this.attendanceService.getDashboardKpis(undefined, startStr, endStr).subscribe({
        next: (data: any) => this.adminKpis = data,
        error: (err: any) => console.error(err)
      });
    } else {
      this.attendanceService.getMyDashboardKpis().subscribe({
        next: (data: any) => this.empKpis = data,
        error: (err: any) => console.error(err)
      });
    }
  }

  openKpiModal(category: string, title: string): void {
    this.kpiCategory = category;
    this.kpiModalTitle = title;
    this.openKpiModalFlag = true;
    this.loadingKpiDetails = true;
    this.kpiRecords = [];

    const startStr = this.formatIsoDate(this.startDate);
    const endStr = this.formatIsoDate(this.endDate);

    this.attendanceService.getKpiDetails(category, startStr, endStr).subscribe({
      next: (data) => {
        this.kpiRecords = data || [];
        this.loadingKpiDetails = false;
      },
      error: (err) => {
        console.error(err);
        this.loadingKpiDetails = false;
        this.toastService.error('Error', 'Failed to load KPI details');
      }
    });
  }

  closeKpiModal(): void {
    this.openKpiModalFlag = false;
  }

  get totalOvertimeSum(): number {
    return this.kpiRecords.reduce((sum, r) => sum + (r.overtimeHours || 0), 0);
  }

  punch(type: 'IN' | 'OUT'): void {
    this.attendanceService.punch(type).subscribe({
      next: () => {
        this.toastService.success('Check-in / Check-out', `Punched ${type} successfully!`);
        this.loadKpis();
      },
      error: (err) => this.toastService.error('Punch Failed', err?.error?.message || 'Unable to record attendance')
    });
  }

  formatTime(dateTimeStr?: string): string {
    if (!dateTimeStr) return '—';
    try {
      const dt = new Date(dateTimeStr);
      return dt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });
    } catch {
      return dateTimeStr;
    }
  }

  formatIsoDate(d: Date): string {
    if (!(d instanceof Date) || isNaN(d.getTime())) return '';
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
