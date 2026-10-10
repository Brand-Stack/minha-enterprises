import { Component, OnInit, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ApiService } from '../../../core/services/api.service';
import { PermissionService } from '../../../core/services/permission.service';
import { AttendanceSummaryModalComponent } from '../attendance-summary-modal/attendance-summary-modal.component';

export interface CalendarDay {
  date: Date;
  dateStr: string; // YYYY-MM-DD
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  records: any[];
  // Summary counts for Admin
  presentCount: number;
  absentCount: number;
  leaveCount: number;
  holidayCount: number;
  weekOffCount: number;
  lateCount: number;
  // Personal record for Employee
  personalRecord?: any;
}

@Component({
  selector: 'app-attendance-calendar',
  template: `
    <div class="calendar-card">
      <!-- Calendar Header Controls -->
      <div class="calendar-header">
        <div class="month-title-wrap">
          <h2 class="month-title">{{ monthNames[currentMonth] }} {{ currentYear }}</h2>
          <button mat-stroked-button class="btn-today" (click)="goToToday()">Today</button>
        </div>

        <div class="month-nav-actions">
          <button mat-icon-button (click)="prevMonth()" matTooltip="Previous Month">
            <mat-icon>chevron_left</mat-icon>
          </button>
          <button mat-icon-button (click)="nextMonth()" matTooltip="Next Month">
            <mat-icon>chevron_right</mat-icon>
          </button>
        </div>
      </div>

      <!-- Legend -->
      <div class="calendar-legend">
        <span class="legend-item"><span class="dot dot-present"></span> Present (P)</span>
        <span class="legend-item"><span class="dot dot-absent"></span> Absent (A)</span>
        <span class="legend-item"><span class="dot dot-leave"></span> Leave (L)</span>
        <span class="legend-item"><span class="dot dot-holiday"></span> Holiday (H)</span>
        <span class="legend-item"><span class="dot dot-weekoff"></span> Week Off (WO)</span>
        <span class="legend-item"><span class="dot dot-late"></span> Late (LC)</span>
        <span class="legend-item"><span class="dot dot-ot"></span> Overtime (OT)</span>
      </div>

      <!-- Calendar Days Header -->
      <div class="weekdays-grid">
        <div class="weekday" *ngFor="let day of weekDays">{{ day }}</div>
      </div>

      <!-- Calendar Grid -->
      <div class="days-grid" *ngIf="!loading">
        <div 
          *ngFor="let cell of calendarGrid" 
          class="day-cell"
          [class.outside-month]="!cell.isCurrentMonth"
          [class.today]="cell.isToday"
          (click)="onDateClick(cell)">
          
          <div class="day-header">
            <span class="day-number">{{ cell.dayNumber }}</span>
          </div>

          <!-- Admin View Badges -->
          <div class="day-body" *ngIf="isAdmin && cell.isCurrentMonth">
            <div class="badge-stack" *ngIf="cell.records.length > 0">
              <span *ngIf="cell.presentCount > 0" class="mini-badge bg-green">P: {{ cell.presentCount }}</span>
              <span *ngIf="cell.absentCount > 0" class="mini-badge bg-red">A: {{ cell.absentCount }}</span>
              <span *ngIf="cell.leaveCount > 0" class="mini-badge bg-yellow">L: {{ cell.leaveCount }}</span>
              <span *ngIf="cell.holidayCount > 0" class="mini-badge bg-blue">H: {{ cell.holidayCount }}</span>
              <span *ngIf="cell.weekOffCount > 0" class="mini-badge bg-slate">WO: {{ cell.weekOffCount }}</span>
            </div>
            <div *ngIf="cell.records.length === 0" class="no-data-dots"></div>
          </div>

          <!-- Employee View Personal Status -->
          <div class="day-body" *ngIf="!isAdmin && cell.isCurrentMonth && cell.personalRecord">
            <div class="emp-status-box" [ngClass]="getStatusClass(cell.personalRecord.status)">
              <span class="status-name">{{ formatShortStatus(cell.personalRecord.status) }}</span>
              <span class="status-time" *ngIf="cell.personalRecord.checkInTime">
                {{ formatTime(cell.personalRecord.checkInTime) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div *ngIf="loading" class="calendar-loading">
        <mat-spinner diameter="36"></mat-spinner>
        <p>Loading attendance calendar...</p>
      </div>
    </div>
  `,
  styles: [`
    .calendar-card {
      background: white;
      border-radius: 12px;
      border: 1px solid #E2E8F0;
      padding: 20px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }
    .calendar-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .month-title-wrap {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .month-title {
      font-size: 20px;
      font-weight: 700;
      color: #0F172A;
      margin: 0;
    }
    .btn-today {
      font-size: 12px;
      padding: 0 10px;
      height: 32px;
    }
    .calendar-legend {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
      font-size: 12px;
      color: #64748B;
      flex-wrap: wrap;
    }
    .legend-item { display: flex; align-items: center; gap: 6px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
    .dot-present { background: #10B981; }
    .dot-absent { background: #EF4444; }
    .dot-leave { background: #F59E0B; }
    .dot-holiday { background: #0284C7; }
    .dot-weekoff { background: #64748B; }
    .dot-late { background: #F97316; }
    .dot-ot { background: #8B5CF6; }

    .weekdays-grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      text-align: center;
      font-weight: 700;
      font-size: 12px;
      color: #64748B;
      margin-bottom: 8px;
      border-bottom: 1px solid #F1F5F9;
      padding-bottom: 8px;
    }
    .days-grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 4px;
    }
    .day-cell {
      min-height: 85px;
      border: 1px solid #F1F5F9;
      border-radius: 8px;
      padding: 6px;
      background: white;
      cursor: pointer;
      transition: all 0.15s ease;
      display: flex;
      flex-direction: column;
    }
    .day-cell:hover {
      border-color: #2563EB;
      box-shadow: 0 2px 6px rgba(37,99,235,0.12);
    }
    .day-cell.outside-month {
      background: #F8FAFC;
      opacity: 0.4;
      cursor: default;
    }
    .day-cell.today {
      border: 2px solid #2563EB;
      background: #EFF6FF;
    }
    .day-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 4px;
    }
    .day-number {
      font-size: 13px;
      font-weight: 700;
      color: #1E293B;
    }
    .badge-stack {
      display: flex;
      flex-wrap: wrap;
      gap: 3px;
    }
    .mini-badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 5px;
      border-radius: 4px;
      color: white;
    }
    .bg-green { background: #10B981; }
    .bg-red { background: #EF4444; }
    .bg-yellow { background: #F59E0B; }
    .bg-blue { background: #0284C7; }
    .bg-slate { background: #64748B; }

    .emp-status-box {
      border-radius: 4px;
      padding: 4px 6px;
      font-size: 11px;
      font-weight: 700;
      display: flex;
      flex-direction: column;
    }
    .status-present { background: #DCFCE7; color: #15803D; }
    .status-absent { background: #FEE2E2; color: #B91C1C; }
    .status-leave { background: #FEF3C7; color: #B45309; }
    .status-holiday { background: #E0F2FE; color: #0369A1; }
    .status-weekoff { background: #F1F5F9; color: #475569; }
    .status-late { background: #FFEDD5; color: #C2410C; }

    .calendar-loading {
      padding: 40px;
      text-align: center;
      color: #64748B;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }
  `]
})
export class AttendanceCalendarComponent implements OnInit, OnChanges {
  @Input() selectedEmployeeId?: string;
  @Input() selectedDepartment?: string;
  @Input() month?: number;
  @Input() year?: number;
  @Output() dateSelected = new EventEmitter<string>();
  @Output() monthYearChange = new EventEmitter<{ month: number; year: number }>();

  weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

  currentDate = new Date();
  currentMonth = this.currentDate.getMonth();
  currentYear = this.currentDate.getFullYear();

  calendarGrid: CalendarDay[] = [];
  recordsMap: Map<string, any[]> = new Map();
  loading = false;
  isAdmin = false;

  constructor(
    private apiService: ApiService,
    private permissionService: PermissionService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    if (this.month !== undefined && this.month !== null) {
      this.currentMonth = this.month;
    }
    if (this.year !== undefined && this.year !== null) {
      this.currentYear = this.year;
    }
    this.loadCalendarData();
  }

  ngOnChanges(changes: SimpleChanges): void {
    let reloadNeeded = false;
    if (changes['month'] && this.month !== undefined && this.month !== null && this.month !== this.currentMonth) {
      this.currentMonth = this.month;
      reloadNeeded = true;
    }
    if (changes['year'] && this.year !== undefined && this.year !== null && this.year !== this.currentYear) {
      this.currentYear = this.year;
      reloadNeeded = true;
    }
    if (changes['selectedEmployeeId'] || changes['selectedDepartment']) {
      reloadNeeded = true;
    }
    if (reloadNeeded) {
      this.loadCalendarData();
    }
  }

  loadCalendarData(): void {
    this.loading = true;
    const firstDay = new Date(this.currentYear, this.currentMonth, 1);
    const lastDay = new Date(this.currentYear, this.currentMonth + 1, 0);

    const startDateStr = this.formatIsoDate(firstDay);
    const endDateStr = this.formatIsoDate(lastDay);

    const params: Record<string, string> = {
      startDate: startDateStr,
      endDate: endDateStr
    };

    if (this.selectedEmployeeId) params['employeeId'] = this.selectedEmployeeId;
    if (this.selectedDepartment) params['department'] = this.selectedDepartment;

    const endpoint = this.isAdmin ? '/attendance/daily' : '/attendance/my-attendance';

    this.apiService.get<any[]>(endpoint, params).subscribe({
      next: (records) => {
        this.recordsMap.clear();
        (records || []).forEach(r => {
          const dStr = r.date;
          if (!this.recordsMap.has(dStr)) {
            this.recordsMap.set(dStr, []);
          }
          this.recordsMap.get(dStr)!.push(r);
        });

        this.buildGrid();
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load calendar data', err);
        this.buildGrid();
        this.loading = false;
      }
    });
  }

  buildGrid(): void {
    const grid: CalendarDay[] = [];
    const todayStr = this.formatIsoDate(new Date());

    const firstOfMonth = new Date(this.currentYear, this.currentMonth, 1);
    const dayOfWeek = firstOfMonth.getDay();

    // Fill previous month trailing days
    const prevMonthLastDay = new Date(this.currentYear, this.currentMonth, 0).getDate();
    for (let i = dayOfWeek - 1; i >= 0; i--) {
      const d = new Date(this.currentYear, this.currentMonth - 1, prevMonthLastDay - i);
      const dateStr = this.formatIsoDate(d);
      grid.push({
        date: d,
        dateStr,
        dayNumber: d.getDate(),
        isCurrentMonth: false,
        isToday: dateStr === todayStr,
        records: [],
        presentCount: 0,
        absentCount: 0,
        leaveCount: 0,
        holidayCount: 0,
        weekOffCount: 0,
        lateCount: 0
      });
    }

    // Days in current month
    const totalDaysInMonth = new Date(this.currentYear, this.currentMonth + 1, 0).getDate();
    for (let day = 1; day <= totalDaysInMonth; day++) {
      const d = new Date(this.currentYear, this.currentMonth, day);
      const dateStr = this.formatIsoDate(d);
      const dayRecords = this.recordsMap.get(dateStr) || [];

      const presentCount = dayRecords.filter(r =>
        r.status === 'PRESENT' || r.status === 'LATE' || r.status === 'EARLY_CHECKOUT' ||
        r.status === 'PERMISSION' || r.status === 'WORK_FROM_HOME' || r.status === 'OVERTIME'
      ).length;
      const absentCount = dayRecords.filter(r => r.status === 'ABSENT').length;
      const leaveCount = dayRecords.filter(r => r.status === 'LEAVE' || r.status === 'HALF_DAY').length;
      const holidayCount = dayRecords.filter(r => r.status === 'HOLIDAY').length;
      const weekOffCount = dayRecords.filter(r => r.status === 'WEEK_OFF').length;
      const lateCount = dayRecords.filter(r => Boolean(r.lateArrival)).length;

      const personalRecord = dayRecords.length > 0 ? dayRecords[0] : undefined;

      grid.push({
        date: d,
        dateStr,
        dayNumber: day,
        isCurrentMonth: true,
        isToday: dateStr === todayStr,
        records: dayRecords,
        presentCount,
        absentCount,
        leaveCount,
        holidayCount,
        weekOffCount,
        lateCount,
        personalRecord
      });
    }

    // Next month leading days to complete week row
    const remaining = 7 - (grid.length % 7);
    if (remaining < 7) {
      for (let day = 1; day <= remaining; day++) {
        const d = new Date(this.currentYear, this.currentMonth + 1, day);
        const dateStr = this.formatIsoDate(d);
        grid.push({
          date: d,
          dateStr,
          dayNumber: day,
          isCurrentMonth: false,
          isToday: dateStr === todayStr,
          records: [],
          presentCount: 0,
          absentCount: 0,
          leaveCount: 0,
          holidayCount: 0,
          weekOffCount: 0,
          lateCount: 0
        });
      }
    }

    this.calendarGrid = grid;
  }

  prevMonth(): void {
    if (this.currentMonth === 0) {
      this.currentMonth = 11;
      this.currentYear--;
    } else {
      this.currentMonth--;
    }
    this.monthYearChange.emit({ month: this.currentMonth, year: this.currentYear });
    this.loadCalendarData();
  }

  nextMonth(): void {
    if (this.currentMonth === 11) {
      this.currentMonth = 0;
      this.currentYear++;
    } else {
      this.currentMonth++;
    }
    this.monthYearChange.emit({ month: this.currentMonth, year: this.currentYear });
    this.loadCalendarData();
  }

  goToToday(): void {
    const today = new Date();
    this.currentMonth = today.getMonth();
    this.currentYear = today.getFullYear();
    this.monthYearChange.emit({ month: this.currentMonth, year: this.currentYear });
    this.loadCalendarData();
  }

  onDateClick(cell: CalendarDay): void {
    if (!cell.isCurrentMonth) return;
    this.dateSelected.emit(cell.dateStr);

    this.dialog.open(AttendanceSummaryModalComponent, {
      width: '750px',
      data: {
        title: `Attendance — ${cell.dateStr}`,
        startDate: cell.dateStr,
        endDate: cell.dateStr,
        department: this.selectedDepartment
      }
    });
  }

  getStatusClass(status?: string): string {
    if (!status) return '';
    const s = status.toUpperCase();
    if (s.includes('HOLIDAY')) return 'status-holiday';
    if (s.includes('WEEK_OFF')) return 'status-weekoff';
    if (s.includes('PRESENT') || s.includes('WORK_FROM_HOME')) return 'status-present';
    if (s.includes('ABSENT')) return 'status-absent';
    if (s.includes('LEAVE') || s.includes('HALF_DAY')) return 'status-leave';
    if (s.includes('LATE')) return 'status-late';
    return '';
  }

  formatShortStatus(status?: string): string {
    if (!status) return '—';
    if (status === 'HOLIDAY') return 'Holiday (H)';
    if (status === 'WEEK_OFF') return 'Week Off (WO)';
    if (status === 'PRESENT') return 'Present (P)';
    if (status === 'ABSENT') return 'Absent (A)';
    if (status === 'LEAVE') return 'Leave (L)';
    if (status === 'LATE') return 'Late (LC)';
    if (status === 'EARLY_CHECKOUT') return 'Early Out (EC)';
    if (status === 'OVERTIME') return 'Overtime (OT)';
    return status;
  }

  formatTime(timeStr?: string): string {
    if (!timeStr) return '';
    try {
      const dt = new Date(timeStr);
      return dt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  }

  formatIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
