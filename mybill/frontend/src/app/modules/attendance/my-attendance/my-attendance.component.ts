import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { AttendanceRecord } from '../../../core/models/attendance.model';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { PermissionService } from '../../../core/services/permission.service';

@Component({
  selector: 'app-my-attendance',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header indigo-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>person_pin</mat-icon>
          </div>
          <div>
            <h2>My Attendance Log</h2>
            <p class="subtitle">Track your daily check-in times, working hours, overtime, and attendance status</p>
          </div>
        </div>

        <div class="filter-group">
          <!-- Preset Filter Buttons -->
          <div class="preset-toggle-container">
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'today'" (click)="selectPreset('today')">Today</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'yesterday'" (click)="selectPreset('yesterday')">Yesterday</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_week'" (click)="selectPreset('this_week')">This Week</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_month'" (click)="selectPreset('this_month')">This Month</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'month_year'" (click)="selectPreset('month_year')">Month / Year</button>
          </div>

          <!-- Month/Year Pickers -->
          <div class="period-filter-wrapper" *ngIf="filterPreset === 'month_year'">
            <mat-icon class="calendar-icon">calendar_month</mat-icon>
            <select class="period-select" [(ngModel)]="selectedFilterMonth" (change)="onMonthYearChange()">
              <option *ngFor="let m of monthOptions" [ngValue]="m.value">{{ m.label }}</option>
            </select>
            <select class="period-select" [(ngModel)]="selectedFilterYear" (change)="onMonthYearChange()">
              <option *ngFor="let y of yearOptions" [ngValue]="y">{{ y }}</option>
            </select>
          </div>

          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
        </div>
      </div>

      <!-- Web Punch Card -->
      <div class="web-punch-card">
        <div class="punch-status-info">
          <div class="punch-badge" 
               [class.in]="isCheckedIn" 
               [class.completed]="isDayCompleted"
               [class.out]="!isCheckedIn && !isDayCompleted">
            <mat-icon>{{ isDayCompleted ? 'verified' : (isCheckedIn ? 'sensors' : 'sensor_door') }}</mat-icon>
            <span>{{ isDayCompleted ? 'ATTENDANCE COMPLETED FOR TODAY' : (isCheckedIn ? 'CURRENTLY CHECKED IN' : 'NOT CHECKED IN TODAY') }}</span>
          </div>
          <p class="punch-subtext" *ngIf="currentTodayRecord?.checkInTime">
            Check-In Time: <strong>{{ formatTime(currentTodayRecord?.checkInTime) }}</strong>
            <span *ngIf="currentTodayRecord?.checkOutTime"> | Check-Out Time: <strong>{{ formatTime(currentTodayRecord?.checkOutTime) }}</strong></span>
          </p>
        </div>
        <div class="punch-actions">
          <button mat-raised-button color="primary" class="btn-punch-in" (click)="doPunch('IN')" [disabled]="!canPunchIn">
            <mat-icon *ngIf="!isPunching">login</mat-icon>
            <span>{{ isPunching ? 'Processing...' : 'Punch IN' }}</span>
          </button>
          <button mat-raised-button color="warn" class="btn-punch-out" (click)="doPunch('OUT')" [disabled]="!canPunchOut">
            <mat-icon *ngIf="!isPunching">logout</mat-icon>
            <span>{{ isPunching ? 'Processing...' : 'Punch OUT' }}</span>
          </button>
        </div>
      </div>
      <div *ngIf="punchError" class="punch-error-alert">
        <mat-icon>error_outline</mat-icon> {{ punchError }}
      </div>

      <!-- KPI Summary Cards -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-icon indigo"><mat-icon>calendar_today</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-label">Logged Days</span>
            <span class="kpi-value">{{ records.length }}</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon green"><mat-icon>check_circle</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-label">Present Days</span>
            <span class="kpi-value">{{ presentCount }}</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon amber"><mat-icon>schedule</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-label">Late Arrivals</span>
            <span class="kpi-value">{{ lateCount }}</span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon purple"><mat-icon>more_time</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-label">Total Overtime</span>
            <span class="kpi-value">{{ totalOvertime | number:'1.1-1' }} hrs</span>
          </div>
        </div>
      </div>

      <!-- Data Table Card -->
      <div class="table-card">
        <table mat-table [dataSource]="records" class="full-width-table">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let element" class="font-medium text-dark">{{ element.date }}</td>
          </ng-container>

          <ng-container matColumnDef="checkIn">
            <th mat-header-cell *matHeaderCellDef>Check-In</th>
            <td mat-cell *matCellDef="let element">
              <span class="time-pill check-in" *ngIf="element.checkInTime">
                <mat-icon>login</mat-icon> {{ formatTime(element.checkInTime) }}
              </span>
              <span *ngIf="!element.checkInTime" class="text-muted">—</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="checkOut">
            <th mat-header-cell *matHeaderCellDef>Check-Out</th>
            <td mat-cell *matCellDef="let element">
              <span class="time-pill check-out" [class.early-checkout-red]="isEarlyCheckout(element)" *ngIf="element.checkOutTime">
                <mat-icon>logout</mat-icon> {{ formatTime(element.checkOutTime) }}
              </span>
              <span *ngIf="!element.checkOutTime" class="text-muted">—</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="workingHours">
            <th mat-header-cell *matHeaderCellDef>Working Hours</th>
            <td mat-cell *matCellDef="let element">
              <span class="hours-chip">{{ element.totalWorkingHours || 0 }} hrs</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let element">
              <span class="status-badge" [class]="element.status?.toLowerCase()">{{ element.status }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="lateMinutes">
            <th mat-header-cell *matHeaderCellDef>Late / Early</th>
            <td mat-cell *matCellDef="let element">
              <span *ngIf="element.lateArrival" class="badge-tag red">Late ({{ element.lateMinutes }}m)</span>
              <span *ngIf="element.earlyDeparture" class="badge-tag orange">Early ({{ element.earlyMinutes }}m)</span>
              <span *ngIf="!element.lateArrival && !element.earlyDeparture" class="text-muted">—</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="overtime">
            <th mat-header-cell *matHeaderCellDef>Overtime</th>
            <td mat-cell *matCellDef="let element">
              <span *ngIf="element.overtimeHours" class="badge-tag purple">+{{ element.overtimeHours }} hrs</span>
              <span *ngIf="!element.overtimeHours" class="text-muted">—</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let element">
              <button mat-icon-button color="warn" (click)="promptDeleteRecord(element)" matTooltip="Delete Attendance Log">
                <mat-icon>delete</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;" class="table-row"></tr>
        </table>

        <div *ngIf="records.length === 0" class="empty-state">
          <mat-icon class="empty-icon">event_busy</mat-icon>
          <p class="empty-title">No Attendance Records Found</p>
          <p class="empty-desc">No attendance logs found for the selected filter period.</p>
        </div>
      </div>

      <!-- Delete Confirmation Modal -->
      <div class="modal-overlay" *ngIf="showDeleteModal" (click)="showDeleteModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <div class="modal-title">
              <mat-icon style="color: #EF4444;">warning</mat-icon>
              <h3>Confirm Delete Attendance Record</h3>
            </div>
            <button mat-icon-button (click)="showDeleteModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete your attendance record for <strong>{{ selectedRecordToDelete?.date }}</strong>?</p>
            <p *ngIf="selectedRecordToDelete?.checkInTime" class="text-sub">
              Check-In: {{ formatTime(selectedRecordToDelete?.checkInTime) }}
              <span *ngIf="selectedRecordToDelete?.checkOutTime"> | Check-Out: {{ formatTime(selectedRecordToDelete?.checkOutTime) }}</span>
            </p>
          </div>
          <div class="modal-footer">
            <button mat-button (click)="showDeleteModal = false">Cancel</button>
            <button mat-flat-button color="warn" (click)="confirmDeleteRecord()">Delete</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; width: 100%; box-sizing: border-box; }
    
    .page-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 24px;
      padding: 20px 24px;
      background: #FFFFFF;
      border-radius: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border-left: 6px solid #2563EB;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #EFF6FF; 
      color: #2563EB; 
      display: flex; 
      align-items: center; 
      justify-content: center;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }
    
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

    .btn-export-excel { background: #10B981 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-pdf { background: #EF4444 !important; color: white !important; font-weight: 600; border-radius: 8px; }

    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .kpi-card { 
      background: white; 
      padding: 18px 20px; 
      border-radius: 14px; 
      display: flex; 
      align-items: center; 
      gap: 16px;
      border: 1px solid #F1F5F9;
      box-shadow: 0 2px 8px rgba(0,0,0,0.03);
    }
    .kpi-icon { width: 44px; height: 44px; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
    .kpi-icon.indigo { background: #EFF6FF; color: #2563EB; }
    .kpi-icon.green { background: #ECFDF5; color: #10B981; }
    .kpi-icon.amber { background: #FFFBEB; color: #F59E0B; }
    .kpi-icon.purple { background: #F5F3FF; color: #8B5CF6; }
    .kpi-info { display: flex; flex-direction: column; }
    .kpi-label { font-size: 12px; font-weight: 600; color: #64748B; text-transform: uppercase; letter-spacing: 0.5px; }
    .kpi-value { font-size: 22px; font-weight: 800; color: #0F172A; margin-top: 2px; }

    .table-card { background: white; border-radius: 14px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); overflow: hidden; border: 1px solid #E2E8F0; }
    .full-width-table { width: 100%; border-collapse: collapse; }
    th.mat-header-cell { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; padding: 14px 16px; border-bottom: 1px solid #E2E8F0; }
    td.mat-cell { padding: 14px 16px; font-size: 13.5px; border-bottom: 1px solid #F1F5F9; }
    .table-row:hover { background-color: #F8FAFC; }

    .font-medium { font-weight: 600; }
    .text-dark { color: #0F172A; }
    .text-muted { color: #94A3B8; }
    .text-sub { font-size: 13px; color: #64748B; margin-top: 4px; }

    .time-pill { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 600; }
    .time-pill.check-in { background: #EFF6FF; color: #1D4ED8; }
    .time-pill.check-out { background: #EFF6FF; color: #1D4ED8; }
    .time-pill.check-out.early-checkout-red { background: #FEF2F2 !important; color: #B91C1C !important; }
    .time-pill mat-icon { font-size: 14px; width: 14px; height: 14px; }

    .hours-chip { background: #F1F5F9; color: #334155; font-weight: 700; padding: 4px 10px; border-radius: 6px; font-size: 12px; }

    .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-badge.present { background: #DCFCE7; color: #15803D; }
    .status-badge.absent { background: #FEE2E2; color: #B91C1C; }
    .status-badge.late { background: #FEF3C7; color: #B45309; }
    .status-badge.leave { background: #DBEAFE; color: #1D4ED8; }
    .status-badge.overtime { background: #F3E8FF; color: #7E22CE; }

    .badge-tag { padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; margin-right: 4px; display: inline-block; }
    .badge-tag.red { background: #FEE2E2; color: #991B1B; }
    .badge-tag.orange { background: #FFEDD5; color: #C2410C; }
    .badge-tag.purple { background: #F3E8FF; color: #6B21A8; }

    .empty-state { text-align: center; padding: 48px 24px; color: #64748B; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #CBD5E1; margin-bottom: 8px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #334155; margin: 0 0 4px 0; }
    .empty-desc { font-size: 13px; color: #94A3B8; margin: 0; }

    .web-punch-card {
      background: white;
      border-radius: 14px;
      padding: 20px 24px;
      margin-bottom: 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border: 1px solid #E2E8F0;
      flex-wrap: wrap;
      gap: 16px;
    }
    .punch-status-info { display: flex; flex-direction: column; gap: 6px; }
    .punch-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 800;
      letter-spacing: 0.5px;
    }
    .punch-badge.in { background: #DCFCE7; color: #15803D; }
    .punch-badge.completed { background: #DBEAFE; color: #1D4ED8; }
    .punch-badge.out { background: #F3F4F6; color: #4B5563; }
    .punch-subtext { font-size: 13px; color: #475569; margin: 0; }
    .punch-actions { display: flex; gap: 12px; }
    .btn-punch-in { background: #16A34A !important; color: white !important; font-weight: 700; border-radius: 8px; }
    .btn-punch-out { background: #DC2626 !important; color: white !important; font-weight: 700; border-radius: 8px; }
    .btn-punch-in:disabled, .btn-punch-out:disabled { opacity: 0.5; cursor: not-allowed; }
    .punch-error-alert {
      background: #FEF2F2;
      color: #991B1B;
      border: 1px solid #FCA5A5;
      padding: 12px 16px;
      border-radius: 10px;
      margin-bottom: 24px;
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 13.5px;
      font-weight: 600;
    }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); }
    .modal-card { background: white; border-radius: 16px; width: 480px; max-width: 92%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
    .modal-header { padding: 18px 24px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center; }
    .modal-header.text-red { color: #DC2626; }
    .modal-title { display: flex; align-items: center; gap: 10px; }
    .modal-title h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0; }
    .modal-body { padding: 24px; display: flex; flex-direction: column; gap: 8px; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }
  `]
})
export class MyAttendanceComponent implements OnInit {
  records: AttendanceRecord[] = [];
  todayRecordData: AttendanceRecord | null = null;

  filterPreset: 'today' | 'yesterday' | 'this_week' | 'this_month' | 'month_year' = 'today';
  selectedFilterMonth: number = new Date().getMonth();
  selectedFilterYear: number = new Date().getFullYear();

  startDate: Date = new Date();
  endDate: Date = new Date();

  isPunching: boolean = false;
  punchError: string | null = null;

  showDeleteModal: boolean = false;
  selectedRecordToDelete: AttendanceRecord | null = null;

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
    private apiService: ApiService,
    private toastService: ToastService,
    private permissionService: PermissionService
  ) {}

  get canDelete(): boolean {
    return this.permissionService.hasPermission('MY_ATTENDANCE', 'delete');
  }

  get displayedColumns(): string[] {
    const cols = ['date', 'checkIn', 'checkOut', 'workingHours', 'status', 'lateMinutes', 'overtime'];
    if (this.canDelete) {
      cols.push('actions');
    }
    return cols;
  }

  get presentCount(): number {
    return this.records.filter(r => r.status === 'PRESENT' || r.status === 'OVERTIME').length;
  }

  get lateCount(): number {
    return this.records.filter(r => r.lateArrival).length;
  }

  get totalOvertime(): number {
    return this.records.reduce((acc, r) => acc + (r.overtimeHours || 0), 0);
  }

  get currentTodayRecord(): AttendanceRecord | undefined {
    const todayStr = this.formatIsoDate(new Date());
    const fromRecords = this.records.find(r => r.date === todayStr);
    return fromRecords || this.todayRecordData || undefined;
  }

  get isCheckedIn(): boolean {
    const rec = this.currentTodayRecord;
    return !!(rec && rec.checkInTime && !rec.checkOutTime);
  }

  get isDayCompleted(): boolean {
    const rec = this.currentTodayRecord;
    return !!(rec && rec.checkInTime && rec.checkOutTime);
  }

  get canPunchIn(): boolean {
    return !this.isCheckedIn && !this.isDayCompleted && !this.isPunching;
  }

  get canPunchOut(): boolean {
    return this.isCheckedIn && !this.isDayCompleted && !this.isPunching;
  }

  isEarlyCheckout(row: any): boolean {
    if (!row) return false;
    return row.earlyDeparture === true || row.status === 'EARLY_CHECKOUT' || row.status === 'LATE_AND_EARLY_CHECKOUT';
  }

  ngOnInit(): void {
    this.selectPreset('today');
    this.loadTodayStatus();
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
    this.loadData();
  }

  onMonthYearChange(): void {
    if (this.filterPreset === 'month_year') {
      this.startDate = new Date(this.selectedFilterYear, this.selectedFilterMonth, 1);
      this.endDate = new Date(this.selectedFilterYear, this.selectedFilterMonth + 1, 0);
      this.loadData();
    }
  }

  loadTodayStatus(): void {
    const todayStr = this.formatIsoDate(new Date());
    this.attendanceService.getMyAttendance(todayStr, todayStr).subscribe({
      next: (list) => {
        if (list && list.length > 0) {
          this.todayRecordData = list[0];
        } else {
          this.todayRecordData = null;
        }
      },
      error: () => {}
    });
  }

  loadData(): void {
    const startStr = this.startDate ? this.formatIsoDate(this.startDate) : '';
    const endStr = this.endDate ? this.formatIsoDate(this.endDate) : '';

    this.attendanceService.getMyAttendance(startStr, endStr).subscribe({
      next: (data) => this.records = data || [],
      error: (err) => console.error(err)
    });
  }

  doPunch(type: 'IN' | 'OUT'): void {
    if (this.isPunching) return;
    this.isPunching = true;
    this.punchError = null;

    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.executePunch(type, position.coords.latitude, position.coords.longitude);
        },
        (error) => {
          console.warn('Geolocation unavailable or denied:', error.message);
          this.executePunch(type);
        },
        { enableHighAccuracy: true, timeout: 3000, maximumAge: 0 }
      );
    } else {
      this.executePunch(type);
    }
  }

  private executePunch(type: 'IN' | 'OUT', lat?: number, lng?: number): void {
    const actionText = type === 'IN' ? 'Punch In' : 'Punch Out';
    this.attendanceService.punch(type, lat, lng).subscribe({
      next: (savedRecord) => {
        this.isPunching = false;
        this.punchError = null;
        this.todayRecordData = savedRecord;

        let timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: true });
        const rawTime = type === 'IN' ? savedRecord?.checkInTime : savedRecord?.checkOutTime;
        if (rawTime) {
          timeStr = this.formatTime(rawTime);
        }

        this.toastService.success('Success', `${actionText} successful at ${timeStr}.`);

        if (savedRecord) {
          const idx = this.records.findIndex(r => r.date === savedRecord.date);
          if (idx >= 0) {
            this.records[idx] = savedRecord;
            this.records = [...this.records];
          } else {
            this.records = [savedRecord, ...this.records];
          }
        }
        this.loadData();
      },
      error: (err) => {
        this.isPunching = false;
        let msg = err.error?.message || err.message;
        if (typeof err.error === 'string' && err.error.trim().length > 0) {
          msg = err.error;
        }
        if (!msg || msg.includes('Http failure')) {
          msg = `Unable to ${actionText}. Please try again.`;
        }
        this.punchError = msg;
        this.toastService.error('Punch Failed', msg);
      }
    });
  }

  promptDeleteRecord(record: AttendanceRecord): void {
    this.selectedRecordToDelete = record;
    this.showDeleteModal = true;
  }

  confirmDeleteRecord(): void {
    if (!this.selectedRecordToDelete || !this.selectedRecordToDelete.id) return;
    const recordId = this.selectedRecordToDelete.id;
    this.attendanceService.deleteMyAttendance(recordId).subscribe({
      next: () => {
        this.toastService.success('Success', 'Attendance record deleted successfully.');
        this.showDeleteModal = false;
        this.selectedRecordToDelete = null;
        this.loadData();
        this.loadTodayStatus();
      },
      error: (err) => {
        const msg = err.error?.message || err.message || 'Unable to delete attendance record. Please try again.';
        this.toastService.error('Delete Failed', msg);
      }
    });
  }

  exportExcel(): void {
    const startStr = this.startDate ? this.formatIsoDate(this.startDate) : '';
    const endStr = this.endDate ? this.formatIsoDate(this.endDate) : '';
    this.apiService.getBlob('/attendance/my-attendance/export/excel', { startDate: startStr, endDate: endStr }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'My_Attendance.xlsx';
        a.click();
      }
    });
  }

  exportPdf(): void {
    const startStr = this.startDate ? this.formatIsoDate(this.startDate) : '';
    const endStr = this.endDate ? this.formatIsoDate(this.endDate) : '';
    this.apiService.getBlob('/attendance/my-attendance/export/pdf', { startDate: startStr, endDate: endStr }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'My_Attendance.pdf';
        a.click();
      }
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
