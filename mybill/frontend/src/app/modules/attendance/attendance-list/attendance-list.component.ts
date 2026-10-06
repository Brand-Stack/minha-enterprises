import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { AttendanceRecord } from '../../../core/models/attendance.model';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { PermissionService } from '../../../core/services/permission.service';

@Component({
  selector: 'app-attendance-list',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header teal-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>assessment</mat-icon>
          </div>
          <div>
            <h2>Daily Attendance Master List</h2>
            <p class="subtitle">Organization-wide daily logs, attendance tracking, calendar view and manual time adjustments</p>
          </div>
        </div>

        <div class="filter-group">
          <!-- View Toggle -->
          <div class="view-toggle-container">
            <button type="button" class="toggle-btn" [class.active]="viewMode === 'table'" (click)="viewMode = 'table'">
              <mat-icon>table_view</mat-icon> Table
            </button>
            <button type="button" class="toggle-btn" [class.active]="viewMode === 'calendar'" (click)="viewMode = 'calendar'">
              <mat-icon>calendar_month</mat-icon> Calendar
            </button>
          </div>

          <!-- Preset Date Filter Buttons -->
          <div class="preset-toggle-container">
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'today'" (click)="selectPreset('today')">Today</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'yesterday'" (click)="selectPreset('yesterday')">Yesterday</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_week'" (click)="selectPreset('this_week')">This Week</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'this_month'" (click)="selectPreset('this_month')">This Month</button>
            <button type="button" class="preset-btn" [class.active]="filterPreset === 'month_year'" (click)="selectPreset('month_year')">Month / Year</button>
          </div>

          <!-- Month/Year Dropdown Filter -->
          <div class="period-filter-wrapper" *ngIf="filterPreset === 'month_year'">
            <mat-icon class="calendar-icon">calendar_month</mat-icon>
            <select class="period-select" [(ngModel)]="selectedMonth" (change)="onPeriodChange()">
              <option *ngFor="let m of monthOptions" [ngValue]="m.value">{{ m.label }}</option>
            </select>
            <select class="period-select" [(ngModel)]="selectedYear" (change)="onPeriodChange()">
              <option *ngFor="let y of yearOptions" [ngValue]="y">{{ y }}</option>
            </select>
          </div>

          <!-- Employee Filter -->
          <div style="min-width: 180px;">
            <app-employee-selector label="Filter Employee" [allowAll]="true" (employeeChange)="onEmployeeFilterChange($event)"></app-employee-selector>
          </div>

          <!-- Status Filter -->
          <div class="period-filter-wrapper">
            <select class="period-select" [(ngModel)]="selectedStatus" (change)="onFilterParamChange()">
              <option value="">All Statuses</option>
              <option value="PRESENT">PRESENT</option>
              <option value="ABSENT">ABSENT</option>
              <option value="LATE">LATE</option>
              <option value="HALF_DAY">HALF_DAY</option>
              <option value="EARLY_CHECKOUT">EARLY_CHECKOUT</option>
              <option value="LATE_AND_EARLY_CHECKOUT">LATE_AND_EARLY_CHECKOUT</option>
              <option value="OVERTIME">OVERTIME</option>
              <option value="LEAVE">LEAVE</option>
              <option value="HOLIDAY">HOLIDAY</option>
              <option value="WEEK_OFF">WEEK_OFF</option>
            </select>
          </div>

          <!-- Search Input -->
          <div class="search-input-wrapper">
            <input type="text" class="custom-input search-input" placeholder="Search name/code..." [(ngModel)]="searchQuery" (input)="onSearchChange()" />
            <mat-icon class="search-icon">search</mat-icon>
          </div>

          <button mat-flat-button color="primary" class="btn-update-attendance" (click)="openUpdateModal()">
            <mat-icon>edit_calendar</mat-icon> Update Attendance
          </button>
          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
        </div>
      </div>

      <!-- Calendar View -->
      <div *ngIf="viewMode === 'calendar'" style="margin-top: 20px;">
        <app-attendance-calendar 
          [selectedEmployeeId]="selectedEmployeeId"
          [month]="selectedMonth"
          [year]="selectedYear"
          (dateSelected)="onCalendarDateSelected($event)"
          (monthYearChange)="onCalendarMonthYearChange($event)">
        </app-attendance-calendar>

        <!-- Attendance Table Below Calendar for Selected Date -->
        <div class="calendar-table-section" style="margin-top: 24px;">
          <div class="section-title-bar" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
            <h3 style="font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #0D9488;">event_available</mat-icon>
              <span>Attendance Records for {{ selectedCalendarDate || 'Selected Date' }}</span>
            </h3>
            <span style="font-size: 13px; color: #64748B;">Showing {{ calendarRecords.length }} record(s)</span>
          </div>

          <div class="table-card">
            <table mat-table [dataSource]="calendarRecords" class="full-width-table">
              <ng-container matColumnDef="date">
                <th mat-header-cell *matHeaderCellDef>Date</th>
                <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.date }}</td>
              </ng-container>

              <ng-container matColumnDef="employee">
                <th mat-header-cell *matHeaderCellDef>Employee</th>
                <td mat-cell *matCellDef="let r">
                  <div class="emp-cell">
                    <div class="emp-avatar">{{ r.employeeName?.charAt(0) || 'E' }}</div>
                    <div>
                      <div class="emp-name">{{ r.employeeName }}</div>
                      <div class="emp-code">{{ r.employeeCode }}</div>
                    </div>
                  </div>
                </td>
              </ng-container>

              <ng-container matColumnDef="checkIn">
                <th mat-header-cell *matHeaderCellDef>Check-In</th>
                <td mat-cell *matCellDef="let r">
                  <span class="time-pill check-in" *ngIf="r.checkInTime">
                    <mat-icon>login</mat-icon> {{ formatTime(r.checkInTime) }}
                  </span>
                  <span *ngIf="!r.checkInTime && isMissingPunch(r)" class="missing-punch-badge">
                    <mat-icon>warning</mat-icon> Missing In
                  </span>
                  <span *ngIf="!r.checkInTime && !isMissingPunch(r)" class="text-muted">—</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="checkOut">
                <th mat-header-cell *matHeaderCellDef>Check-Out</th>
                <td mat-cell *matCellDef="let r">
                  <span class="time-pill check-out" [class.early-checkout-red]="isEarlyCheckout(r)" *ngIf="r.checkOutTime">
                    <mat-icon>logout</mat-icon> {{ formatTime(r.checkOutTime) }}
                  </span>
                  <span *ngIf="!r.checkOutTime && isMissingPunch(r)" class="missing-punch-badge">
                    <mat-icon>warning</mat-icon> Missing Out
                  </span>
                  <span *ngIf="!r.checkOutTime && !isMissingPunch(r)" class="text-muted">—</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="workingHours">
                <th mat-header-cell *matHeaderCellDef>Hours</th>
                <td mat-cell *matCellDef="let r">
                  <span class="hours-chip">{{ r.totalWorkingHours || 0 }} hrs</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let r">
                  <span class="status-badge" [class]="r.status?.toLowerCase()">{{ r.status }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>Actions</th>
                <td mat-cell *matCellDef="let r">
                  <div class="action-buttons">
                    <button mat-stroked-button class="btn-correct" (click)="openUpdateModal(r)">
                      <mat-icon>edit</mat-icon> Update
                    </button>
                    <button mat-icon-button color="warn" *ngIf="canDelete" (click)="promptDeleteRecord(r)" matTooltip="Delete Attendance Record">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="['date', 'employee', 'checkIn', 'checkOut', 'workingHours', 'status', 'actions']"></tr>
              <tr mat-row *matRowDef="let row; columns: ['date', 'employee', 'checkIn', 'checkOut', 'workingHours', 'status', 'actions'];" class="table-row" [class.missing-punch-row]="isMissingPunch(row)"></tr>
            </table>

            <div *ngIf="calendarRecords.length === 0" class="empty-state">
              <mat-icon class="empty-icon">event_busy</mat-icon>
              <p class="empty-title">No Records Found</p>
              <p class="empty-desc">Click on any date in the calendar above to view attendance records for that day.</p>
            </div>

            <!-- Calendar Table Pagination Bar -->
            <div class="pagination-bar" *ngIf="calendarTotalRecords > 0">
              <div class="pagination-info">
                Showing <strong>{{ calendarTotalRecords > 0 ? (calendarPage * calendarPageSize + 1) : 0 }}</strong> to <strong>{{ calendarCurrentEndIndex }}</strong> of <strong>{{ calendarTotalRecords }}</strong> entries
              </div>
              <div class="pagination-controls">
                <div class="page-size-picker">
                  <span>Rows per page:</span>
                  <select [(ngModel)]="calendarPageSize" (change)="onCalendarPageSizeChange()">
                    <option *ngFor="let s of pageSizeOptions" [ngValue]="s">{{ s }}</option>
                  </select>
                </div>
                <div class="page-nav">
                  <button mat-icon-button [disabled]="calendarPage === 0" (click)="onCalendarPageChange(calendarPage - 1)">
                    <mat-icon>chevron_left</mat-icon>
                  </button>
                  <span class="page-indicator">
                    Page {{ calendarTotalPages > 0 ? calendarPage + 1 : 0 }} of {{ calendarTotalPages }}
                  </span>
                  <button mat-icon-button [disabled]="calendarPage >= calendarTotalPages - 1" (click)="onCalendarPageChange(calendarPage + 1)">
                    <mat-icon>chevron_right</mat-icon>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Table View -->
      <ng-container *ngIf="viewMode === 'table'">
        <!-- Active Date Range Banner -->
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; padding: 0 4px;">
          <span style="font-size: 13px; color: #475569; font-weight: 600; display: flex; align-items: center; gap: 6px;">
            <mat-icon style="font-size: 16px; width: 16px; height: 16px; color: #0D9488;">date_range</mat-icon>
            Showing logs from <strong>{{ formatIsoDate(startDate) }}</strong> to <strong>{{ formatIsoDate(endDate) }}</strong>
          </span>
          <span style="font-size: 12px; color: #9F1239; background: #FFE4E6; border: 1px solid #FECDD3; padding: 3px 10px; border-radius: 12px; font-weight: 600; display: flex; align-items: center; gap: 4px;" *ngIf="hasMissingPunchesInList">
            <mat-icon style="font-size: 14px; width: 14px; height: 14px;">warning</mat-icon> Red highlights indicate missing Punch-In / Punch-Out
          </span>
        </div>

        <!-- KPI Summary Cards -->
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-icon teal"><mat-icon>groups</mat-icon></div>
            <div class="kpi-info">
              <span class="kpi-label">Total Logged</span>
              <span class="kpi-value">{{ totalRecords }}</span>
            </div>
          </div>

          <div class="kpi-card">
            <div class="kpi-icon green"><mat-icon>check_circle</mat-icon></div>
            <div class="kpi-info">
              <span class="kpi-label">Present</span>
              <span class="kpi-value">{{ presentCount }}</span>
            </div>
          </div>

          <div class="kpi-card">
            <div class="kpi-icon red"><mat-icon>cancel</mat-icon></div>
            <div class="kpi-info">
              <span class="kpi-label">Absent</span>
              <span class="kpi-value">{{ absentCount }}</span>
            </div>
          </div>

          <div class="kpi-card">
            <div class="kpi-icon amber"><mat-icon>alarm_on</mat-icon></div>
            <div class="kpi-info">
              <span class="kpi-label">Late Arrivals</span>
              <span class="kpi-value">{{ lateCount }}</span>
            </div>
          </div>
        </div>

        <!-- Table Card -->
        <div class="table-card">
          <table mat-table [dataSource]="records" class="full-width-table">
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.date }}</td>
            </ng-container>

            <ng-container matColumnDef="employee">
              <th mat-header-cell *matHeaderCellDef>Employee</th>
              <td mat-cell *matCellDef="let r">
                <div class="emp-cell">
                  <div class="emp-avatar">{{ r.employeeName?.charAt(0) || 'E' }}</div>
                  <div>
                    <div class="emp-name">{{ r.employeeName }}</div>
                    <div class="emp-code">{{ r.employeeCode }}</div>
                  </div>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="checkIn">
              <th mat-header-cell *matHeaderCellDef>Check-In</th>
              <td mat-cell *matCellDef="let r">
                <span class="time-pill check-in" *ngIf="r.checkInTime">
                  <mat-icon>login</mat-icon> {{ formatTime(r.checkInTime) }}
                </span>
                <span *ngIf="!r.checkInTime && isMissingPunch(r)" class="missing-punch-badge">
                  <mat-icon>warning</mat-icon> Missing In
                </span>
                <span *ngIf="!r.checkInTime && !isMissingPunch(r)" class="text-muted">—</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="checkOut">
              <th mat-header-cell *matHeaderCellDef>Check-Out</th>
              <td mat-cell *matCellDef="let r">
                <span class="time-pill check-out" [class.early-checkout-red]="isEarlyCheckout(r)" *ngIf="r.checkOutTime">
                  <mat-icon>logout</mat-icon> {{ formatTime(r.checkOutTime) }}
                </span>
                <span *ngIf="!r.checkOutTime && isMissingPunch(r)" class="missing-punch-badge">
                  <mat-icon>warning</mat-icon> Missing Out
                </span>
                <span *ngIf="!r.checkOutTime && !isMissingPunch(r)" class="text-muted">—</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="workingHours">
              <th mat-header-cell *matHeaderCellDef>Hours</th>
              <td mat-cell *matCellDef="let r">
                <span class="hours-chip">{{ r.totalWorkingHours || 0 }} hrs</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r">
                <span class="status-badge" [class]="r.status?.toLowerCase()">{{ r.status }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let r">
                <div class="action-buttons">
                  <button mat-stroked-button class="btn-correct" (click)="openUpdateModal(r)">
                    <mat-icon>edit</mat-icon> Update
                  </button>
                  <button mat-icon-button color="warn" *ngIf="canDelete" (click)="promptDeleteRecord(r)" matTooltip="Delete Attendance Record">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="['date', 'employee', 'checkIn', 'checkOut', 'workingHours', 'status', 'actions']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['date', 'employee', 'checkIn', 'checkOut', 'workingHours', 'status', 'actions'];" class="table-row" [class.missing-punch-row]="isMissingPunch(row)"></tr>
          </table>

          <div *ngIf="records.length === 0" class="empty-state">
            <mat-icon class="empty-icon">search_off</mat-icon>
            <p class="empty-title">No Daily Attendance Records Found</p>
            <p class="empty-desc">No attendance logs registered for the selected date range and filter criteria.</p>
          </div>

          <!-- Pagination Bar -->
          <div class="pagination-bar">
            <div class="pagination-info">
              Showing <strong>{{ totalRecords > 0 ? (page * pageSize + 1) : 0 }}</strong> to <strong>{{ currentEndIndex }}</strong> of <strong>{{ totalRecords }}</strong> entries
            </div>
            <div class="pagination-controls">
              <div class="page-size-picker">
                <span>Rows per page:</span>
                <select [(ngModel)]="pageSize" (change)="onPageSizeChange()">
                  <option *ngFor="let s of pageSizeOptions" [ngValue]="s">{{ s }}</option>
                </select>
              </div>
              <div class="page-nav">
                <button mat-icon-button [disabled]="page === 0" (click)="onPageChange(page - 1)">
                  <mat-icon>chevron_left</mat-icon>
                </button>
                <span class="page-indicator">
                  Page {{ totalPages > 0 ? page + 1 : 0 }} of {{ totalPages }}
                </span>
                <button mat-icon-button [disabled]="page >= totalPages - 1" (click)="onPageChange(page + 1)">
                  <mat-icon>chevron_right</mat-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
      </ng-container>

      <!-- Update Attendance Modal -->
      <div class="modal-overlay" *ngIf="openUpdateModalFlag">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon style="color: #0D9488;">edit_calendar</mat-icon>
              <h3>Update Attendance Record</h3>
            </div>
            <button mat-icon-button (click)="closeUpdateModal()"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body">
            <div class="form-field-group" *ngIf="!updateForm.id">
              <app-employee-selector 
                label="Select Employee" 
                [allowAll]="false" 
                (employeeChange)="updateForm.employeeId = $event">
              </app-employee-selector>
            </div>

            <div class="form-field-group" *ngIf="updateForm.id">
              <label class="field-label">Employee</label>
              <input type="text" class="custom-input" [value]="updateForm.employeeName" disabled />
            </div>

            <div class="form-field-group">
              <label class="field-label">Attendance Date</label>
              <input type="date" class="custom-input" [(ngModel)]="updateForm.date" />
            </div>

            <div class="time-grid">
              <div class="form-field-group">
                <label class="field-label">Punch-In Date</label>
                <input type="date" class="custom-input" [(ngModel)]="updateForm.checkInDate" />
              </div>
              <div class="form-field-group">
                <label class="field-label">Punch-In Time</label>
                <input type="time" class="custom-input" [(ngModel)]="updateForm.checkInTime" />
              </div>
            </div>

            <div class="time-grid">
              <div class="form-field-group">
                <label class="field-label">Punch-Out Date</label>
                <input type="date" class="custom-input" [(ngModel)]="updateForm.checkOutDate" />
              </div>
              <div class="form-field-group">
                <label class="field-label">Punch-Out Time</label>
                <input type="time" class="custom-input" [(ngModel)]="updateForm.checkOutTime" />
              </div>
            </div>

            <div class="form-field-group">
              <label class="field-label">Reason / Remarks</label>
              <textarea class="custom-textarea" rows="3" [(ngModel)]="updateForm.remarks" placeholder="Enter reason for attendance adjustment..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button (click)="closeUpdateModal()">Cancel</button>
            <button mat-flat-button color="primary" (click)="saveAttendanceUpdate()">Save Attendance Update</button>
          </div>
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
            <p>Are you sure you want to delete the attendance record for <strong>{{ selectedRecordToDelete?.employeeName }}</strong> on <strong>{{ selectedRecordToDelete?.date }}</strong>?</p>
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
      border-left: 6px solid #0D9488;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #CCFBF1; 
      color: #0D9488; 
      display: flex; 
      align-items: center; 
      justify-content: center;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }

    .filter-group { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    
    .view-toggle-container {
      display: flex;
      background: #F1F5F9;
      border: 1px solid #CBD5E1;
      border-radius: 8px;
      padding: 3px;
      gap: 2px;
    }
    .toggle-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #64748B;
      font-weight: 700;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .toggle-btn.active {
      background: #0D9488;
      color: white;
      box-shadow: 0 1px 3px rgba(13, 148, 136, 0.3);
    }
    .toggle-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }

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
      background: #0D9488;
      color: white;
      box-shadow: 0 1px 3px rgba(13, 148, 136, 0.3);
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
    .calendar-icon { color: #0D9488; font-size: 18px; width: 18px; height: 18px; }
    .period-select { 
      border: none; 
      background: transparent; 
      outline: none; 
      font-size: 13px; 
      font-weight: 700; 
      color: #0F172A; 
      cursor: pointer; 
    }

    .search-input-wrapper {
      position: relative;
      min-width: 170px;
    }
    .search-input {
      padding-left: 32px !important;
      height: 36px;
      font-size: 13px;
    }
    .search-icon {
      position: absolute;
      left: 8px;
      top: 9px;
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #94A3B8;
    }

    .btn-update-attendance { background: #0D9488 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-excel { background: #10B981 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-pdf { background: #EF4444 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-correct { border-color: #0D9488 !important; color: #0D9488 !important; border-radius: 6px; font-weight: 600; font-size: 12px; }

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
    .kpi-icon.teal { background: #CCFBF1; color: #0D9488; }
    .kpi-icon.green { background: #ECFDF5; color: #10B981; }
    .kpi-icon.red { background: #FEF2F2; color: #EF4444; }
    .kpi-icon.amber { background: #FFFBEB; color: #F59E0B; }
    .kpi-info { display: flex; flex-direction: column; }
    .kpi-label { font-size: 12px; font-weight: 600; color: #64748B; text-transform: uppercase; letter-spacing: 0.5px; }
    .kpi-value { font-size: 22px; font-weight: 800; color: #0F172A; margin-top: 2px; }

    .table-card { background: white; border-radius: 14px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); overflow: hidden; border: 1px solid #E2E8F0; }
    .full-width-table { width: 100%; border-collapse: collapse; }
    th.mat-header-cell { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; padding: 14px 16px; border-bottom: 1px solid #E2E8F0; }
    td.mat-cell { padding: 14px 16px; font-size: 13.5px; border-bottom: 1px solid #F1F5F9; }
    .table-row:hover { background-color: #F8FAFC; }
    tr.table-row.missing-punch-row { background-color: #FFF1F2 !important; border-left: 4px solid #E11D48 !important; }
    tr.table-row.missing-punch-row:hover { background-color: #FFE4E6 !important; }

    .missing-punch-badge { background: #FFE4E6; color: #9F1239; border: 1px solid #FECDD3; padding: 3px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; gap: 4px; }
    .missing-punch-badge mat-icon { font-size: 13px; width: 13px; height: 13px; }

    .emp-cell { display: flex; align-items: center; gap: 10px; }
    .emp-avatar { width: 34px; height: 34px; border-radius: 50%; background: #CCFBF1; color: #0F766E; font-weight: 700; font-size: 14px; display: flex; align-items: center; justify-content: center; }
    .emp-name { font-weight: 600; color: #0F172A; }
    .emp-code { font-size: 11px; color: #64748B; }

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
    .status-badge.holiday { background: #E0F2FE; color: #0369A1; }
    .status-badge.week_off, .status-badge.wo { background: #F1F5F9; color: #475569; }
    .status-badge.overtime { background: #F3E8FF; color: #7E22CE; }

    .pagination-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 20px;
      background: #F8FAFC;
      border-top: 1px solid #E2E8F0;
      flex-wrap: wrap;
      gap: 12px;
    }
    .pagination-info { font-size: 13px; color: #64748B; }
    .pagination-controls { display: flex; align-items: center; gap: 16px; }
    .page-size-picker { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #64748B; }
    .page-size-picker select { border: 1px solid #CBD5E1; border-radius: 6px; padding: 4px 8px; font-size: 13px; outline: none; }
    .page-nav { display: flex; align-items: center; gap: 4px; }
    .page-indicator { font-size: 13px; font-weight: 600; color: #334155; padding: 0 8px; }

    .empty-state { text-align: center; padding: 48px 24px; color: #64748B; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #CBD5E1; margin-bottom: 8px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #334155; margin: 0 0 4px 0; }
    .empty-desc { font-size: 13px; color: #94A3B8; margin: 0; }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); }
    .modal-card { background: white; border-radius: 16px; width: 520px; max-width: 92%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
    .modal-header { padding: 18px 24px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center; }
    .modal-title { display: flex; align-items: center; gap: 10px; }
    .modal-title h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0; }
    .modal-body { padding: 24px; display: flex; flex-direction: column; gap: 16px; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }

    .form-field-group { display: flex; flex-direction: column; gap: 6px; }
    .field-label { font-size: 13px; font-weight: 600; color: #334155; }
    .custom-input, .custom-textarea { width: 100%; border: 1px solid #CBD5E1; border-radius: 8px; padding: 10px 12px; font-size: 14px; outline: none; transition: border-color 0.15s ease; box-sizing: border-box; }
    .custom-input:focus, .custom-textarea:focus { border-color: #0D9488; }
    .custom-input:disabled { background-color: #F1F5F9; color: #64748B; }
    .time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  `]
})
export class AttendanceListComponent implements OnInit {
  records: AttendanceRecord[] = [];
  viewMode: 'table' | 'calendar' = 'table';
  selectedEmployeeId: string = '';
  selectedStatus: string = '';
  searchQuery: string = '';

  // Pagination
  page: number = 0;
  pageSize: number = 15;
  totalRecords: number = 0;
  totalPages: number = 0;
  pageSizeOptions: number[] = [10, 15, 25, 50, 100];

  filterPreset: 'today' | 'yesterday' | 'this_week' | 'this_month' | 'month_year' = 'today';
  startDate: Date = new Date();
  endDate: Date = new Date();

  selectedCalendarDate: string = this.formatIsoDate(new Date());
  calendarRecords: AttendanceRecord[] = [];
  calendarPage: number = 0;
  calendarPageSize: number = 15;
  calendarTotalRecords: number = 0;
  calendarTotalPages: number = 0;

  kpiPresentCount: number = 0;
  kpiAbsentCount: number = 0;
  kpiLateCount: number = 0;

  openUpdateModalFlag: boolean = false;
  updateForm = {
    id: '',
    employeeId: '',
    employeeName: '',
    date: '',
    checkInDate: '',
    checkInTime: '',
    checkOutDate: '',
    checkOutTime: '',
    remarks: ''
  };

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

  selectedMonth: number = new Date().getMonth();
  selectedYear: number = new Date().getFullYear();
  showDeleteModal: boolean = false;
  selectedRecordToDelete: AttendanceRecord | null = null;

  constructor(
    private attendanceService: AttendanceService,
    private apiService: ApiService,
    private toastService: ToastService,
    private permissionService: PermissionService
  ) {}

  get canDelete(): boolean {
    return this.permissionService.hasPermission('MASTER_ATTENDANCE', 'delete');
  }

  get hasMissingPunchesInList(): boolean {
    return this.records.some(r => this.isMissingPunch(r));
  }

  get presentCount(): number {
    return this.kpiPresentCount;
  }

  get absentCount(): number {
    return this.kpiAbsentCount;
  }

  get lateCount(): number {
    return this.kpiLateCount;
  }

  get currentEndIndex(): number {
    return Math.min((this.page + 1) * this.pageSize, this.totalRecords);
  }

  get calendarCurrentEndIndex(): number {
    return Math.min((this.calendarPage + 1) * this.calendarPageSize, this.calendarTotalRecords);
  }

  ngOnInit(): void {
    this.selectPreset('today');
  }

  isMissingPunch(row: AttendanceRecord): boolean {
    if (!row) return false;
    if (row.status === 'LEAVE' || row.status === 'HOLIDAY' || row.status === 'WEEK_OFF') {
      return false;
    }
    return !row.checkInTime || !row.checkOutTime || row.status === 'INCOMPLETE';
  }

  isEarlyCheckout(row: AttendanceRecord): boolean {
    if (!row) return false;
    return row.earlyDeparture || row.status === 'EARLY_CHECKOUT' || row.status === 'LATE_AND_EARLY_CHECKOUT';
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
      const diffToMon = day === 0 ? -6 : 1 - day;
      const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() + diffToMon);
      const sunday = new Date(now.getFullYear(), now.getMonth(), now.getDate() + diffToMon + 6);
      this.startDate = monday;
      this.endDate = sunday;
    } else if (preset === 'this_month') {
      this.startDate = new Date(now.getFullYear(), now.getMonth(), 1);
      this.endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    } else if (preset === 'month_year') {
      const m = Number(this.selectedMonth);
      const y = Number(this.selectedYear);
      this.startDate = new Date(y, m, 1);
      this.endDate = new Date(y, m + 1, 0);
    }
    this.page = 0;
    this.loadData();
    if (this.viewMode === 'calendar') {
      this.onCalendarDateSelected(this.formatIsoDate(this.startDate));
    }
  }

  onPeriodChange(): void {
    if (this.filterPreset === 'month_year') {
      const m = Number(this.selectedMonth);
      const y = Number(this.selectedYear);
      this.startDate = new Date(y, m, 1);
      this.endDate = new Date(y, m + 1, 0);
      this.page = 0;
      this.loadData();
      if (this.viewMode === 'calendar') {
        this.onCalendarDateSelected(this.formatIsoDate(this.startDate));
      }
    }
  }

  onFilterParamChange(): void {
    this.page = 0;
    this.loadData();
    if (this.viewMode === 'calendar' && this.selectedCalendarDate) {
      this.calendarPage = 0;
      this.loadCalendarData();
    }
  }

  onSearchChange(): void {
    this.page = 0;
    this.loadData();
    if (this.viewMode === 'calendar' && this.selectedCalendarDate) {
      this.calendarPage = 0;
      this.loadCalendarData();
    }
  }

  onPageChange(newPage: number): void {
    if (newPage >= 0 && newPage < this.totalPages) {
      this.page = newPage;
      this.loadData();
    }
  }

  onPageSizeChange(): void {
    this.page = 0;
    this.loadData();
  }

  onCalendarMonthYearChange(event: { month: number; year: number }): void {
    this.selectedMonth = Number(event.month);
    this.selectedYear = Number(event.year);
    this.filterPreset = 'month_year';
    this.startDate = new Date(this.selectedYear, this.selectedMonth, 1);
    this.endDate = new Date(this.selectedYear, this.selectedMonth + 1, 0);
    this.page = 0;
    this.loadData();
  }

  onCalendarDateSelected(dateStr: string): void {
    this.selectedCalendarDate = dateStr;
    this.calendarPage = 0;
    this.loadCalendarData();
  }

  loadCalendarData(): void {
    if (!this.selectedCalendarDate) return;
    this.attendanceService.getPagedDailyAttendance(
      this.selectedCalendarDate,
      this.selectedCalendarDate,
      this.selectedEmployeeId,
      this.selectedStatus,
      this.searchQuery,
      this.calendarPage,
      this.calendarPageSize
    ).subscribe({
      next: (res) => {
        this.calendarRecords = res.content || [];
        this.calendarTotalRecords = res.totalElements || 0;
        this.calendarTotalPages = res.totalPages || 0;
      },
      error: (err) => console.error(err)
    });
  }

  onCalendarPageChange(newPage: number): void {
    if (newPage >= 0 && newPage < this.calendarTotalPages) {
      this.calendarPage = newPage;
      this.loadCalendarData();
    }
  }

  onCalendarPageSizeChange(): void {
    this.calendarPage = 0;
    this.loadCalendarData();
  }

  onEmployeeFilterChange(empId: string): void {
    this.selectedEmployeeId = empId || '';
    this.page = 0;
    this.loadData();
    if (this.selectedCalendarDate) {
      this.onCalendarDateSelected(this.selectedCalendarDate);
    }
  }

  loadData(): void {
    const startDateStr = this.formatIsoDate(this.startDate);
    const endDateStr = this.formatIsoDate(this.endDate);

    this.attendanceService.getPagedDailyAttendance(
      startDateStr,
      endDateStr,
      this.selectedEmployeeId,
      this.selectedStatus,
      this.searchQuery,
      this.page,
      this.pageSize
    ).subscribe({
      next: (res) => {
        this.records = res.content || [];
        this.totalRecords = res.totalElements || 0;
        this.totalPages = res.totalPages || 0;
        if (res.presentCount !== undefined) this.kpiPresentCount = res.presentCount;
        if (res.absentCount !== undefined) this.kpiAbsentCount = res.absentCount;
        if (res.lateCount !== undefined) this.kpiLateCount = res.lateCount;
      },
      error: (err) => console.error(err)
    });
  }

  promptDeleteRecord(record: AttendanceRecord): void {
    this.selectedRecordToDelete = record;
    this.showDeleteModal = true;
  }

  confirmDeleteRecord(): void {
    if (!this.selectedRecordToDelete || !this.selectedRecordToDelete.id) return;
    const recordId = this.selectedRecordToDelete.id;
    this.attendanceService.deleteAttendance(recordId).subscribe({
      next: () => {
        this.toastService.success('Success', 'Attendance record deleted successfully.');
        this.showDeleteModal = false;
        this.selectedRecordToDelete = null;
        this.loadData();
        if (this.selectedCalendarDate) {
          this.onCalendarDateSelected(this.selectedCalendarDate);
        }
      },
      error: (err) => {
        const msg = err.error?.message || err.message || 'Unable to delete attendance record. Please try again.';
        this.toastService.error('Delete Failed', msg);
      }
    });
  }

  exportExcel(): void {
    const startDateStr = this.formatIsoDate(this.startDate);
    const endDateStr = this.formatIsoDate(this.endDate);

    this.apiService.getBlob('/attendance/daily/export/excel', { startDate: startDateStr, endDate: endDateStr, employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Daily_Attendance_${startDateStr}_${endDateStr}.xlsx`;
        a.click();
      }
    });
  }

  exportPdf(): void {
    const startDateStr = this.formatIsoDate(this.startDate);
    const endDateStr = this.formatIsoDate(this.endDate);

    this.apiService.getBlob('/attendance/daily/export/pdf', { startDate: startDateStr, endDate: endDateStr, employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Daily_Attendance_${startDateStr}_${endDateStr}.pdf`;
        a.click();
      }
    });
  }

  openUpdateModal(r?: AttendanceRecord): void {
    const todayStr = this.formatIsoDate(new Date());
    if (r) {
      let inDate = r.date || todayStr;
      let inTime = '';
      if (r.checkInTime) {
        const parts = r.checkInTime.split('T');
        inDate = parts[0];
        if (parts[1]) inTime = parts[1].substring(0, 5);
      }

      let outDate = r.date || todayStr;
      let outTime = '';
      if (r.checkOutTime) {
        const parts = r.checkOutTime.split('T');
        outDate = parts[0];
        if (parts[1]) outTime = parts[1].substring(0, 5);
      }

      this.updateForm = {
        id: r.id || '',
        employeeId: r.employeeId,
        employeeName: r.employeeName + (r.employeeCode ? ` (${r.employeeCode})` : ''),
        date: r.date || todayStr,
        checkInDate: inDate,
        checkInTime: inTime,
        checkOutDate: outDate,
        checkOutTime: outTime,
        remarks: r.remarks || r.correctionReason || ''
      };
    } else {
      this.updateForm = {
        id: '',
        employeeId: this.selectedEmployeeId || '',
        employeeName: '',
        date: todayStr,
        checkInDate: todayStr,
        checkInTime: '',
        checkOutDate: todayStr,
        checkOutTime: '',
        remarks: ''
      };
    }
    this.openUpdateModalFlag = true;
  }

  closeUpdateModal(): void {
    this.openUpdateModalFlag = false;
  }

  saveAttendanceUpdate(): void {
    if (!this.updateForm.employeeId) {
      this.toastService.error('Error', 'Please select an employee');
      return;
    }
    if (!this.updateForm.date) {
      this.toastService.error('Error', 'Please select an attendance date');
      return;
    }

    let checkInTimeIso: string | undefined = undefined;
    if (this.updateForm.checkInDate && this.updateForm.checkInTime) {
      checkInTimeIso = `${this.updateForm.checkInDate}T${this.updateForm.checkInTime}:00`;
    }

    let checkOutTimeIso: string | undefined = undefined;
    if (this.updateForm.checkOutDate && this.updateForm.checkOutTime) {
      checkOutTimeIso = `${this.updateForm.checkOutDate}T${this.updateForm.checkOutTime}:00`;
    }

    if (checkInTimeIso && checkOutTimeIso) {
      const inDt = new Date(checkInTimeIso);
      const outDt = new Date(checkOutTimeIso);
      if (outDt < inDt) {
        this.toastService.error('Error', 'Punch Out time cannot be before Punch In time');
        return;
      }
    }

    const payload = {
      id: this.updateForm.id || undefined,
      employeeId: this.updateForm.employeeId,
      date: this.updateForm.date,
      checkInTime: checkInTimeIso,
      checkOutTime: checkOutTimeIso,
      remarks: this.updateForm.remarks,
      reason: this.updateForm.remarks || 'Manual update by Admin'
    };

    this.attendanceService.updateAttendance(payload).subscribe({
      next: () => {
        this.toastService.success('Success', 'Attendance record updated successfully');
        this.closeUpdateModal();
        this.loadData();
        if (this.selectedCalendarDate) {
          this.onCalendarDateSelected(this.selectedCalendarDate);
        }
      },
      error: (err) => {
        this.toastService.error('Error', err?.error?.message || err?.message || 'Failed to update attendance record');
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
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}

