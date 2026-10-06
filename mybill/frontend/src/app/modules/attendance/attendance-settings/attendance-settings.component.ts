import { Component, OnInit } from '@angular/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AttendanceService } from '../../../core/services/attendance.service';

@Component({
  selector: 'app-attendance-settings',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header slate-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>tune</mat-icon>
          </div>
          <div>
            <h2>Attendance Config</h2>
            <p class="subtitle">Configure organization shift timings, weekly off days, grace periods, overtime rules, annual leave quotas, and holiday calendar</p>
          </div>
        </div>

        <div class="header-actions">
          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
          <button mat-flat-button class="btn-primary-slate" (click)="saveSettings()">
            <mat-icon>save</mat-icon> Save Settings
          </button>
        </div>
      </div>

      <div class="settings-grid" *ngIf="settings">
        <!-- 1. Shift Timings & Grace Periods -->
        <div class="section-card border-blue">
          <div class="section-header">
            <div class="icon-badge bg-blue-light text-blue">
              <mat-icon>schedule</mat-icon>
            </div>
            <div>
              <h3>Shift Timings & Grace Periods</h3>
              <p class="card-subtitle">Define working hours, mandatory breaks, and grace limits</p>
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Working Start Time</label>
              <input type="text" [(ngModel)]="settings.workingStartTime" class="form-input" placeholder="10:00">
            </div>
            <div class="form-group">
              <label class="form-label">Working End Time</label>
              <input type="text" [(ngModel)]="settings.workingEndTime" class="form-input" placeholder="18:00">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Expected Working Hours / Day</label>
              <input type="number" [(ngModel)]="settings.workingHoursPerDay" class="form-input" placeholder="8">
            </div>
            <div class="form-group">
              <label class="form-label">Break Duration (Minutes)</label>
              <input type="number" [(ngModel)]="settings.breakDurationMinutes" class="form-input" placeholder="45">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Late Arrival Grace Period (Mins)</label>
              <input type="number" [(ngModel)]="settings.lateGracePeriodMinutes" class="form-input" placeholder="15">
            </div>
            <div class="form-group">
              <label class="form-label">Early Checkout Grace Period (Mins)</label>
              <input type="number" [(ngModel)]="settings.earlyCheckoutGracePeriodMinutes" class="form-input" placeholder="15">
            </div>
          </div>
        </div>

        <!-- 2. Weekly Off Days Configuration -->
        <div class="section-card border-indigo">
          <div class="section-header">
            <div class="icon-badge bg-indigo-light text-indigo">
              <mat-icon>date_range</mat-icon>
            </div>
            <div>
              <h3>Weekly Off Days Configuration</h3>
              <p class="card-subtitle">Select non-working Weekly Off (WO) days for the organization</p>
            </div>
          </div>

          <div class="weekly-off-grid">
            <label *ngFor="let day of allDays" class="day-checkbox-card" [class.selected]="isWeeklyOff(day)">
              <input type="checkbox" [checked]="isWeeklyOff(day)" (change)="toggleWeeklyOff(day)" class="form-checkbox">
              <span class="day-label">{{ day }}</span>
            </label>
          </div>
        </div>

        <!-- 3. Overtime Rules & Annual Leave Quotas -->
        <div class="section-card border-purple">
          <div class="section-header">
            <div class="icon-badge bg-purple-light text-purple">
              <mat-icon>more_time</mat-icon>
            </div>
            <div>
              <h3>Overtime Rules & Annual Leave Quotas</h3>
              <p class="card-subtitle">Set overtime rates, LOP basis, and yearly leave caps</p>
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Overtime Start Threshold (Mins)</label>
              <input type="number" [(ngModel)]="settings.overtimeThresholdMinutes" class="form-input" placeholder="30">
            </div>
            <div class="form-group">
              <label class="form-label">Overtime Multiplier (x Base Rate)</label>
              <input type="number" step="0.1" [(ngModel)]="settings.overtimeHourlyRateMultiplier" class="form-input" placeholder="1.5">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Casual Leave Quota (Days / Year)</label>
              <input type="number" [(ngModel)]="settings.casualLeaveEntitlementPerYear" class="form-input" placeholder="12">
            </div>
            <div class="form-group">
              <label class="form-label">Medical Leave Quota (Days / Year)</label>
              <input type="number" [(ngModel)]="settings.medicalLeaveEntitlementPerYear" class="form-input" placeholder="10">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Emergency Leave Quota (Days / Year)</label>
              <input type="number" [(ngModel)]="settings.emergencyLeaveEntitlementPerYear" class="form-input" placeholder="5">
            </div>
            <div class="form-group">
              <label class="form-label">Comp Off Quota (Days / Year)</label>
              <input type="number" [(ngModel)]="settings.compOffEntitlementPerYear" class="form-input" placeholder="3">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group full-width">
              <label class="form-label">Loss of Pay (LOP) Calculation Method</label>
              <select [(ngModel)]="settings.lopDailyCalculationMethod" class="form-select">
                <option value="CALENDAR_DAYS">Calendar Days Basis (Total Month Days)</option>
                <option value="WORKING_DAYS">Working Days Basis (Fixed 26 Days)</option>
              </select>
            </div>
          </div>
        </div>

        <!-- 4. Office Geofencing & Location Boundary -->
        <div class="section-card border-emerald">
          <div class="section-header">
            <div class="icon-badge bg-emerald-light text-emerald">
              <mat-icon>location_on</mat-icon>
            </div>
            <div>
              <h3>Office Geofencing & Location Boundary</h3>
              <p class="card-subtitle">Restrict mobile punches within office GPS coordinates</p>
            </div>
          </div>

          <div class="geofence-toggle-card margin-bottom">
            <label for="geofencingEnabled" class="toggle-label">
              <input type="checkbox" id="geofencingEnabled" [(ngModel)]="settings.geofencingEnabled" class="form-checkbox">
              <div>
                <span class="toggle-title">Enable Office Location Geofence Check</span>
                <span class="toggle-sub">Employees must be within boundary radius to check-in/out</span>
              </div>
            </label>
          </div>

          <div class="form-row">
            <div class="form-group full-width">
              <label class="form-label">Attendance Location Name / Address</label>
              <input type="text" [(ngModel)]="settings.attendanceLocation" class="form-input" placeholder="e.g. Head Office, Branch Office">
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">Office Latitude</label>
              <input type="number" step="any" [(ngModel)]="settings.officeLatitude" class="form-input" placeholder="e.g. 13.0827">
            </div>
            <div class="form-group">
              <label class="form-label">Office Longitude</label>
              <input type="number" step="any" [(ngModel)]="settings.officeLongitude" class="form-input" placeholder="e.g. 80.2707">
            </div>
          </div>
          <div class="location-help-text margin-bottom-sm" style="font-size: 12px; color: #64748B; margin-top: -8px; display: flex; align-items: center; gap: 4px;">
            <mat-icon style="font-size: 16px; width: 16px; height: 16px; color: #3B82F6;">info</mat-icon>
            <span>Coordinates can be entered manually. Automatic GPS detection requires HTTPS or localhost.</span>
          </div>

          <div class="form-row align-end">
            <div class="form-group">
              <label class="form-label">Allowed Boundary Radius (Meters)</label>
              <input type="number" [(ngModel)]="settings.allowedRadiusMeters" class="form-input" placeholder="Default: 100">
            </div>
            <div class="form-group">
              <button type="button" class="btn-location-capture" (click)="useCurrentLocation()">
                <mat-icon>my_location</mat-icon> Set Current Location
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 5. Organization Holiday Calendar Section -->
      <div class="section-card full-width-card margin-top border-slate">
        <div class="section-header space-between">
          <div class="header-title-wrap">
            <div class="icon-badge bg-slate-light text-slate">
              <mat-icon>event</mat-icon>
            </div>
            <div>
              <h3>Organization Holiday Calendar</h3>
              <p class="card-subtitle">Manage official company holidays, public festival holidays, and optional leave dates</p>
            </div>
          </div>
          <div class="header-controls">
            <select [(ngModel)]="selectedHolidayYear" (change)="loadHolidays()" class="form-select year-select">
              <option *ngFor="let y of holidayYearOptions" [value]="y">{{ y }}</option>
            </select>
            <button mat-flat-button class="btn-add-holiday" (click)="openAddHolidayModal()">
              <mat-icon>add</mat-icon> Add Holiday
            </button>
          </div>
        </div>

        <div class="table-container">
          <table class="custom-table">
            <thead>
              <tr>
                <th>Holiday Date</th>
                <th>Holiday Name</th>
                <th>Category / Type</th>
                <th>Description</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let h of holidays">
                <td><strong class="date-highlight">{{ h.holidayDate }}</strong></td>
                <td class="font-bold text-dark">{{ h.name || h.holidayName }}</td>
                <td><span class="type-badge" [ngClass]="(h.type || h.holidayType || 'public').toLowerCase()">{{ h.type || h.holidayType }}</span></td>
                <td>{{ h.description || '—' }}</td>
                <td>
                  <span class="status-badge" [class.active]="h.active" [class.inactive]="!h.active">
                    {{ h.active ? 'Active' : 'Inactive' }}
                  </span>
                </td>
                <td>
                  <div class="action-buttons">
                    <button mat-icon-button color="primary" (click)="openEditHolidayModal(h)" matTooltip="Edit Holiday">
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button mat-icon-button color="warn" (click)="promptDeleteHoliday(h)" matTooltip="Delete Holiday">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                </td>
              </tr>
              <tr *ngIf="holidays.length === 0">
                <td colspan="6" class="empty-cell">No holidays configured for year {{ selectedHolidayYear }}. Click "+ Add Holiday" to configure organization holidays.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Add / Edit Holiday Modal -->
      <div class="modal-overlay" *ngIf="showHolidayModal" (click)="showHolidayModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>{{ editingHolidayId ? 'Edit Holiday' : 'Add New Holiday' }}</h3>
            <button mat-icon-button (click)="showHolidayModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <div class="form-group margin-bottom-sm">
              <label class="form-label">Holiday Date</label>
              <input type="date" [(ngModel)]="holidayForm.holidayDate" class="form-input">
            </div>
            <div class="form-group margin-bottom-sm">
              <label class="form-label">Holiday Name</label>
              <input type="text" [(ngModel)]="holidayForm.name" class="form-input" placeholder="e.g. New Year's Day, Independence Day">
            </div>
            <div class="form-group margin-bottom-sm">
              <label class="form-label">Category / Type</label>
              <select [(ngModel)]="holidayForm.type" class="form-select">
                <option value="PUBLIC">PUBLIC (National / Government Holiday)</option>
                <option value="OPTIONAL">OPTIONAL (Restricted / Restricted Holiday)</option>
                <option value="COMPANY">COMPANY (Company Specific Holiday)</option>
              </select>
            </div>
            <div class="form-group margin-bottom-sm">
              <label class="form-label">Description (Optional)</label>
              <textarea [(ngModel)]="holidayForm.description" class="form-input textarea-input" rows="3" placeholder="Brief description of the holiday"></textarea>
            </div>
            <div class="checkbox-group">
              <input type="checkbox" id="holidayActive" [(ngModel)]="holidayForm.active" class="form-checkbox">
              <label for="holidayActive" class="form-label cursor-pointer">Active Holiday</label>
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showHolidayModal = false">Cancel</button>
            <button mat-flat-button color="primary" (click)="saveHoliday()">Save Holiday</button>
          </div>
        </div>
      </div>

      <!-- Delete Holiday Confirmation Modal -->
      <div class="modal-overlay" *ngIf="showDeleteHolidayModal" (click)="showDeleteHolidayModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <mat-icon style="color: #EF4444;">warning</mat-icon> Confirm Delete Holiday
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete the holiday <strong>{{ selectedHolidayToDelete?.name }}</strong> ({{ selectedHolidayToDelete?.holidayDate }})?</p>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showDeleteHolidayModal = false">Cancel</button>
            <button mat-flat-button color="warn" (click)="confirmDeleteHoliday()">Delete</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* Universal Box Sizing Reset for exact layout bounds */
    *, *::before, *::after {
      box-sizing: border-box;
    }

    .page-container {
      padding: 24px;
      width: 100%;
      box-sizing: border-box;
    }
    
    .page-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 24px;
      padding: 20px 24px;
      background: #FFFFFF;
      border-radius: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border-left: 6px solid #475569;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #F1F5F9; 
      color: #475569; 
      display: flex; 
      align-items: center; 
      justify-content: center;
      flex-shrink: 0;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }

    .header-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .btn-export-excel { background: #10B981 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-pdf { background: #EF4444 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-primary-slate { background: #475569 !important; color: white !important; font-weight: 600; border-radius: 8px; }

    /* Responsive Grid Layout */
    .settings-grid { 
      display: grid; 
      grid-template-columns: repeat(auto-fit, minmax(480px, 1fr)); 
      gap: 24px; 
    }

    .section-card { 
      background: white; 
      padding: 24px; 
      border-radius: 14px; 
      border: 1px solid #E2E8F0; 
      box-shadow: 0 2px 10px rgba(0,0,0,0.03); 
      display: flex;
      flex-direction: column;
      width: 100%;
    }
    .full-width-card { grid-column: 1 / -1; }
    .margin-top { margin-top: 24px; }
    .margin-bottom { margin-bottom: 16px; }
    .margin-bottom-sm { margin-bottom: 12px; }

    /* Card Colored Borders */
    .border-blue { border-top: 4px solid #3B82F6; }
    .border-indigo { border-top: 4px solid #6366F1; }
    .border-purple { border-top: 4px solid #8B5CF6; }
    .border-emerald { border-top: 4px solid #10B981; }
    .border-slate { border-top: 4px solid #475569; }

    .section-header { display: flex; align-items: center; gap: 14px; margin-bottom: 20px; }
    .section-header.space-between { justify-content: space-between; flex-wrap: wrap; }
    .header-title-wrap { display: flex; align-items: center; gap: 14px; }
    
    .icon-badge {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .bg-blue-light { background: #EFF6FF; }
    .text-blue { color: #2563EB; }
    .bg-indigo-light { background: #EEF2FF; }
    .text-indigo { color: #4F46E5; }
    .bg-purple-light { background: #F3E8FF; }
    .text-purple { color: #7C3AED; }
    .bg-emerald-light { background: #ECFDF5; }
    .text-emerald { color: #059669; }
    .bg-slate-light { background: #F1F5F9; }
    .text-slate { color: #475569; }

    .section-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; }
    .card-subtitle { font-size: 12.5px; color: #64748B; margin: 2px 0 0 0; }

    /* Strict Flexbox & Input Boundaries */
    .form-row { 
      display: flex; 
      gap: 16px; 
      margin-bottom: 16px;
      width: 100%;
    }
    .form-row.align-end { align-items: flex-end; }
    .form-group { 
      flex: 1; 
      min-width: 0; 
      display: flex; 
      flex-direction: column; 
      gap: 6px; 
    }
    .form-group.full-width { flex: none; width: 100%; }

    .form-label { font-size: 13px; font-weight: 600; color: #475569; margin: 0; }
    
    .form-input, .form-select { 
      box-sizing: border-box;
      width: 100%; 
      height: 42px;
      padding: 0 14px; 
      border: 1px solid #CBD5E1; 
      border-radius: 8px; 
      font-size: 14px; 
      font-weight: 500;
      color: #0F172A;
      background: #FFFFFF;
      outline: none; 
      transition: all 0.2s ease; 
    }
    .textarea-input {
      height: auto;
      padding: 10px 14px;
      resize: vertical;
    }
    .form-input:focus, .form-select:focus { 
      border-color: #3B82F6; 
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
    }

    /* Weekly Off Checkbox Grid */
    .weekly-off-grid { 
      display: grid; 
      grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); 
      gap: 10px; 
      width: 100%;
      margin-top: 8px;
    }
    .day-checkbox-card {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 14px;
      border: 1px solid #E2E8F0;
      border-radius: 8px;
      background: #F8FAFC;
      cursor: pointer;
      font-weight: 600;
      font-size: 13px;
      color: #334155;
      transition: all 0.2s;
      user-select: none;
    }
    .day-checkbox-card:hover {
      border-color: #CBD5E1;
      background: #F1F5F9;
    }
    .day-checkbox-card.selected {
      background: #EFF6FF;
      border-color: #3B82F6;
      color: #1D4ED8;
      box-shadow: 0 1px 3px rgba(59, 130, 246, 0.1);
    }
    .form-checkbox {
      width: 16px;
      height: 16px;
      accent-color: #2563EB;
      cursor: pointer;
    }

    /* Geofence Toggle Box */
    .geofence-toggle-card {
      background: #F8FAFC;
      border: 1px solid #E2E8F0;
      border-radius: 10px;
      padding: 12px 16px;
      width: 100%;
    }
    .toggle-label {
      display: flex;
      align-items: center;
      gap: 12px;
      cursor: pointer;
      margin: 0;
    }
    .toggle-title { display: block; font-size: 13.5px; font-weight: 700; color: #0F172A; }
    .toggle-sub { display: block; font-size: 12px; color: #64748B; }

    .btn-location-capture {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 42px;
      width: 100%;
      background: #EFF6FF;
      color: #1D4ED8;
      border: 1px solid #BFDBFE;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-location-capture:hover {
      background: #DBEAFE;
      border-color: #93C5FD;
    }
    .btn-location-capture mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* Holiday Table & Controls */
    .header-controls { display: flex; gap: 12px; align-items: center; }
    .year-select { width: 130px; }
    .btn-add-holiday { background: #4F46E5 !important; color: white !important; font-weight: 600; border-radius: 8px; height: 42px; }

    .table-container { overflow-x: auto; margin-top: 12px; width: 100%; }
    .custom-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
    .custom-table th { background: #F8FAFC; padding: 12px 16px; color: #475569; font-weight: 700; border-bottom: 1px solid #E2E8F0; }
    .custom-table td { padding: 12px 16px; border-bottom: 1px solid #F1F5F9; color: #334155; }
    .date-highlight { color: #1E293B; }
    .empty-cell { text-align: center; padding: 32px !important; color: #64748B; font-style: italic; }

    .type-badge { padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.3px; }
    .type-badge.public { background: #DCFCE7; color: #166534; }
    .type-badge.optional { background: #FEF3C7; color: #92400E; }
    .type-badge.company { background: #E0E7FF; color: #3730A3; }

    .status-badge { padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; }
    .status-badge.active { background: #DEF7EC; color: #03543F; }
    .status-badge.inactive { background: #FDE8E8; color: #9B1C1C; }

    .action-buttons { display: flex; gap: 4px; }

    .modal-overlay {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(15, 23, 42, 0.5);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    .modal-card {
      background: white;
      border-radius: 14px;
      width: 100%;
      max-width: 500px;
      box-shadow: 0 10px 25px rgba(0,0,0,0.15);
      overflow: hidden;
    }
    .modal-header {
      padding: 16px 20px;
      background: #F8FAFC;
      border-bottom: 1px solid #E2E8F0;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 700;
    }
    .modal-header h3 { margin: 0; font-size: 16px; color: #0F172A; }
    .modal-body { padding: 20px; }
    .modal-actions {
      padding: 16px 20px;
      background: #F8FAFC;
      border-top: 1px solid #E2E8F0;
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }
    .checkbox-group { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
    .cursor-pointer { cursor: pointer; margin: 0; }
  `]
})
export class AttendanceSettingsComponent implements OnInit {
  settings: any = null;
  allDays = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

  // Holiday Calendar state
  holidays: any[] = [];
  selectedHolidayYear: number = new Date().getFullYear();
  holidayYearOptions: number[] = Array.from({ length: 15 }, (_, i) => new Date().getFullYear() - 2 + i);

  showHolidayModal = false;
  editingHolidayId: string | null = null;
  holidayForm: any = {
    holidayDate: '',
    name: '',
    type: 'PUBLIC',
    description: '',
    active: true
  };

  showDeleteHolidayModal = false;
  selectedHolidayToDelete: any = null;

  constructor(
    private apiService: ApiService,
    private attendanceService: AttendanceService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadSettings();
    this.loadHolidays();
  }

  loadSettings(): void {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (data) => {
        this.settings = data;
        if (!this.settings.weekendDays) {
          this.settings.weekendDays = ['SUNDAY'];
        }
      },
      error: (err) => console.error(err)
    });
  }

  isWeeklyOff(day: string): boolean {
    return this.settings?.weekendDays?.includes(day) || false;
  }

  toggleWeeklyOff(day: string): void {
    if (!this.settings) return;
    if (!this.settings.weekendDays) this.settings.weekendDays = [];
    const idx = this.settings.weekendDays.indexOf(day);
    if (idx >= 0) {
      this.settings.weekendDays.splice(idx, 1);
    } else {
      this.settings.weekendDays.push(day);
    }
  }

  saveSettings(): void {
    this.apiService.put<any>('/company-settings', this.settings).subscribe({
      next: () => this.toastService.success('Attendance Settings', 'Attendance config saved successfully!'),
      error: (err) => this.toastService.error('Save Failed', err?.error?.message || 'Failed to save settings')
    });
  }

  // --- HOLIDAY CALENDAR ---
  loadHolidays(): void {
    this.attendanceService.getHolidays(this.selectedHolidayYear).subscribe({
      next: (data) => this.holidays = data || [],
      error: (err) => console.error('Failed to load holidays', err)
    });
  }

  openAddHolidayModal(): void {
    this.editingHolidayId = null;
    this.holidayForm = {
      holidayDate: `${this.selectedHolidayYear}-01-01`,
      name: '',
      type: 'PUBLIC',
      description: '',
      active: true
    };
    this.showHolidayModal = true;
  }

  openEditHolidayModal(holiday: any): void {
    this.editingHolidayId = holiday.id;
    this.holidayForm = {
      holidayDate: holiday.holidayDate,
      name: holiday.name || holiday.holidayName || '',
      type: holiday.type || holiday.holidayType || 'PUBLIC',
      description: holiday.description || '',
      active: holiday.active !== false
    };
    this.showHolidayModal = true;
  }

  saveHoliday(): void {
    if (!this.holidayForm.holidayDate || !this.holidayForm.name) {
      this.toastService.error('Validation Error', 'Please enter holiday date and name.');
      return;
    }

    const payload = {
      ...this.holidayForm,
      holidayName: this.holidayForm.name,
      holidayType: this.holidayForm.type,
      year: new Date(this.holidayForm.holidayDate).getFullYear()
    };

    if (this.editingHolidayId) {
      this.attendanceService.updateHoliday(this.editingHolidayId, payload).subscribe({
        next: () => {
          this.toastService.success('Holiday Updated', 'Holiday updated successfully!');
          this.showHolidayModal = false;
          this.loadHolidays();
        },
        error: (err) => this.toastService.error('Save Failed', err?.error?.message || 'Failed to update holiday')
      });
    } else {
      this.attendanceService.createHoliday(payload).subscribe({
        next: () => {
          this.toastService.success('Holiday Created', 'Holiday created successfully!');
          this.showHolidayModal = false;
          this.loadHolidays();
        },
        error: (err) => this.toastService.error('Save Failed', err?.error?.message || 'Failed to create holiday')
      });
    }
  }

  promptDeleteHoliday(holiday: any): void {
    this.selectedHolidayToDelete = holiday;
    this.showDeleteHolidayModal = true;
  }

  confirmDeleteHoliday(): void {
    if (!this.selectedHolidayToDelete) return;
    this.attendanceService.deleteHoliday(this.selectedHolidayToDelete.id).subscribe({
      next: () => {
        this.toastService.success('Holiday Deleted', 'Holiday deleted successfully.');
        this.showDeleteHolidayModal = false;
        this.selectedHolidayToDelete = null;
        this.loadHolidays();
      },
      error: (err) => this.toastService.error('Delete Failed', err?.error?.message || 'Failed to delete holiday')
    });
  }

  exportExcel(): void {
    this.apiService.getBlob('/company-settings/attendance-config/export/excel').subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Company_Attendance_Config.xlsx';
        a.click();
      }
    });
  }

  exportPdf(): void {
    this.apiService.getBlob('/company-settings/attendance-config/export/pdf').subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Company_Attendance_Config.pdf';
        a.click();
      }
    });
  }

  useCurrentLocation(): void {
    if (!('geolocation' in navigator)) {
      this.toastService.error('Unsupported', 'Geolocation is not supported by your browser.');
      return;
    }

    const isInsecureContext = window.isSecureContext === false &&
      window.location.protocol === 'http:' &&
      window.location.hostname !== 'localhost' &&
      window.location.hostname !== '127.0.0.1';

    if (isInsecureContext) {
      this.toastService.warning(
        'HTTP Browser Security Notice',
        'Browsers block automatic GPS capture over plain HTTP IP connections. Please access via localhost or HTTPS, or type Office Latitude & Longitude manually.'
      );
      return;
    }

    const tryGetPosition = (highAccuracy: boolean) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          if (this.settings) {
            this.settings.officeLatitude = pos.coords.latitude;
            this.settings.officeLongitude = pos.coords.longitude;
            this.toastService.success('Location Set', 'Current GPS coordinates captured!');
          }
        },
        (err) => {
          if (highAccuracy) {
            // High accuracy failed or timed out; fallback to standard accuracy
            tryGetPosition(false);
          } else {
            const errMsg = err.message || '';
            if (err.code === 1) {
              this.toastService.error('Permission Denied', 'Location permission was denied. Please allow location access in your browser.');
            } else if (err.code === 2) {
              this.toastService.error('Location Unavailable', 'Unable to determine your current location. Please check your device location settings.');
            } else if (err.code === 3) {
              this.toastService.error('Location Timeout', 'Location request timed out. Please try again.');
            } else {
              this.toastService.error('Location Error', 'Unable to capture GPS coordinates: ' + errMsg);
            }
          }
        },
        { enableHighAccuracy: highAccuracy, timeout: highAccuracy ? 5000 : 10000, maximumAge: 0 }
      );
    };

    tryGetPosition(true);
  }
}
