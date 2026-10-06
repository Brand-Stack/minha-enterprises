import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { ApiService } from '../../../core/services/api.service';
import { AttendanceDevice, DeviceEmployeeMapping } from '../../../core/models/attendance.model';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-device-integration',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header orange-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>fingerprint</mat-icon>
          </div>
          <div>
            <h2>Biometric Device Integration</h2>
            <p class="subtitle">Register hardware fingerprint scanners, check online/offline status, and map biometric employee user IDs</p>
          </div>
        </div>

        <div class="header-actions">
          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
          <button mat-flat-button class="btn-primary-orange" (click)="openRegisterModal = true">
            <mat-icon>router</mat-icon> Register Device
          </button>
          <button mat-flat-button class="btn-secondary-coral" (click)="openMappingModal = true">
            <mat-icon>person_add</mat-icon> Map Employee ID
          </button>
        </div>
      </div>

      <!-- Devices List -->
      <div class="section-card">
        <div class="section-header">
          <mat-icon class="section-icon">settings_input_component</mat-icon>
          <h3>Registered Biometric Hardware Devices</h3>
        </div>

        <table mat-table [dataSource]="devices" class="full-width-table">
          <ng-container matColumnDef="deviceId">
            <th mat-header-cell *matHeaderCellDef>Device ID</th>
            <td mat-cell *matCellDef="let d">
              <span class="device-code">{{ d.deviceId }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="deviceName">
            <th mat-header-cell *matHeaderCellDef>Device Name</th>
            <td mat-cell *matCellDef="let d" class="font-bold text-dark">{{ d.deviceName }}</td>
          </ng-container>

          <ng-container matColumnDef="ip">
            <th mat-header-cell *matHeaderCellDef>IP Address</th>
            <td mat-cell *matCellDef="let d">
              <span class="ip-chip">{{ d.deviceIp }}:{{ d.port || 4370 }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let d">
              <span class="status-badge" [class]="d.status?.toLowerCase()">
                <mat-icon class="status-dot">fiber_manual_record</mat-icon> {{ d.status }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="lastSync">
            <th mat-header-cell *matHeaderCellDef>Last Sync</th>
            <td mat-cell *matCellDef="let d" class="text-muted">{{ d.lastSyncTime || 'Never' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="['deviceId', 'deviceName', 'ip', 'status', 'lastSync']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['deviceId', 'deviceName', 'ip', 'status', 'lastSync'];" class="table-row"></tr>
        </table>

        <div *ngIf="devices.length === 0" class="empty-state">
          <mat-icon class="empty-icon">dns</mat-icon>
          <p class="empty-title">No Biometric Devices Registered</p>
          <p class="empty-desc">Click "Register Device" to connect a standalone hardware scanner.</p>
        </div>
      </div>

      <!-- Register Device Modal -->
      <div class="modal-overlay" *ngIf="openRegisterModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">router</mat-icon>
              <h3>Register Biometric Scanner</h3>
            </div>
            <button class="close-btn" (click)="openRegisterModal = false"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">Device Name / Location</label>
              <input type="text" [(ngModel)]="newDevice.deviceName" class="form-input" placeholder="e.g. Main Entrance Fingerprint Scanner">
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Device IP / Hostname</label>
                <input type="text" [(ngModel)]="newDevice.deviceIp" class="form-input" placeholder="192.168.1.100">
              </div>
              <div class="form-group">
                <label class="form-label">Port</label>
                <input type="number" [(ngModel)]="newDevice.port" class="form-input" placeholder="4370">
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Serial Number</label>
              <input type="text" [(ngModel)]="newDevice.serialNumber" class="form-input" placeholder="e.g. ZK-K40-99812">
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openRegisterModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit-orange" (click)="saveDevice()">Register Device</button>
          </div>
        </div>
      </div>

      <!-- Employee Mapping Modal -->
      <div class="modal-overlay" *ngIf="openMappingModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">person_add</mat-icon>
              <h3>Map Employee Fingerprint Enrollment ID</h3>
            </div>
            <button class="close-btn" (click)="openMappingModal = false"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <app-employee-selector [(ngModel)]="mapping.employeeId" [allowAll]="false" label="Select Employee"></app-employee-selector>
            </div>

            <div class="form-group">
              <label class="form-label">Biometric Device User ID / Enrollment Code</label>
              <input type="text" [(ngModel)]="mapping.deviceUserId" class="form-input" placeholder="e.g. 101">
            </div>

            <div class="form-group">
              <label class="form-label">Device ID</label>
              <input type="text" [(ngModel)]="mapping.deviceId" class="form-input" placeholder="Device ID">
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openMappingModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit-orange" (click)="saveMapping()">Save Mapping</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; width: 100%; max-width: none !important; margin: 0 !important; box-sizing: border-box; }
    
    .page-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 24px;
      padding: 20px 24px;
      background: #FFFFFF;
      border-radius: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border-left: 6px solid #EA580C;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #FFEDD5; 
      color: #EA580C; 
      display: flex; 
      align-items: center; 
      justify-content: center;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }

    .header-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .btn-export-excel { background: #10B981 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-pdf { background: #EF4444 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-primary-orange { background: #EA580C !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-secondary-coral { background: #C2410C !important; color: white !important; font-weight: 600; border-radius: 8px; }

    .section-card { background: white; padding: 22px; border-radius: 14px; border: 1px solid #E2E8F0; margin-bottom: 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.03); }
    .section-header { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
    .section-icon { color: #EA580C; font-size: 22px; width: 22px; height: 22px; }
    .section-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; }

    .full-width-table { width: 100%; border-collapse: collapse; }
    th.mat-header-cell { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; padding: 14px 16px; border-bottom: 1px solid #E2E8F0; }
    td.mat-cell { padding: 14px 16px; font-size: 13.5px; border-bottom: 1px solid #F1F5F9; }
    .table-row:hover { background-color: #F8FAFC; }

    .device-code { font-family: monospace; font-weight: 700; background: #F1F5F9; padding: 3px 8px; border-radius: 4px; color: #334155; }
    .ip-chip { background: #FFEDD5; color: #9A3412; font-weight: 700; padding: 3px 8px; border-radius: 6px; font-size: 12px; }

    .font-bold { font-weight: 700; }
    .text-dark { color: #0F172A; }
    .text-muted { color: #64748B; font-size: 13px; }

    .status-badge { display: inline-flex; align-items: center; gap: 4px; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-badge.online { background: #DCFCE7; color: #15803D; }
    .status-badge.offline { background: #FEE2E2; color: #B91C1C; }
    .status-badge.syncing { background: #FEF3C7; color: #B45309; }
    .status-dot { font-size: 8px; width: 8px; height: 8px; }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); }
    .modal-card { background: white; border-radius: 16px; width: 450px; max-width: 92%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
    .modal-header { padding: 18px 24px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center; }
    .modal-title { display: flex; align-items: center; gap: 10px; }
    .modal-title h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0; }
    .close-btn { background: none; border: none; color: #94A3B8; cursor: pointer; display: flex; }
    .modal-body { padding: 24px; }
    .form-group { margin-bottom: 16px; display: flex; flex-direction: column; gap: 6px; }
    .form-row { display: flex; gap: 12px; }
    .form-row .form-group { flex: 1; }
    .form-label { font-size: 13px; font-weight: 600; color: #475569; }
    .form-input { padding: 10px 12px; border: 1px solid #CBD5E1; border-radius: 8px; font-size: 14px; outline: none; transition: border 0.2s; }
    .form-input:focus { border-color: #EA580C; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }
    .btn-cancel { color: #64748B !important; font-weight: 600; }
    .btn-submit-orange { background: #EA580C !important; color: white !important; font-weight: 700; border-radius: 8px; }

    .empty-state { text-align: center; padding: 48px 24px; color: #64748B; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #CBD5E1; margin-bottom: 8px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #334155; margin: 0 0 4px 0; }
    .empty-desc { font-size: 13px; color: #94A3B8; margin: 0; }
  `]
})
export class DeviceIntegrationComponent implements OnInit {
  devices: AttendanceDevice[] = [];
  openRegisterModal = false;
  openMappingModal = false;

  newDevice: Partial<AttendanceDevice> = {
    deviceName: '',
    deviceIp: '',
    port: 4370,
    serialNumber: ''
  };

  mapping = {
    employeeId: '',
    deviceUserId: '',
    deviceId: ''
  };

  constructor(
    private attendanceService: AttendanceService,
    private apiService: ApiService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadDevices();
  }

  exportExcel(): void {
    this.apiService.getBlob('/biometric-devices/export/excel').subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Biometric_Devices.xlsx';
        a.click();
      }
    });
  }

  exportPdf(): void {
    this.apiService.getBlob('/biometric-devices/export/pdf').subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Biometric_Devices.pdf';
        a.click();
      }
    });
  }

  loadDevices(): void {
    this.attendanceService.listDevices().subscribe({
      next: (data) => this.devices = data,
      error: (err) => console.error(err)
    });
  }

  saveDevice(): void {
    if (!this.newDevice.deviceName) {
      this.toastService.error('Validation Error', 'Device Name is required');
      return;
    }

    this.attendanceService.registerDevice(this.newDevice as AttendanceDevice).subscribe({
      next: () => {
        this.toastService.success('Device Registered', 'Biometric device registered successfully!');
        this.openRegisterModal = false;
        this.loadDevices();
      },
      error: (err) => this.toastService.error('Registration Failed', err?.error?.message || 'Failed to register device')
    });
  }

  saveMapping(): void {
    if (!this.mapping.employeeId || !this.mapping.deviceUserId) {
      this.toastService.error('Validation Error', 'Employee ID and Device User ID are required');
      return;
    }

    this.attendanceService.mapEmployeeToDevice(this.mapping).subscribe({
      next: () => {
        this.toastService.success('Employee Mapped', 'Employee mapped to biometric device user ID successfully!');
        this.openMappingModal = false;
      },
      error: (err) => this.toastService.error('Mapping Failed', err?.error?.message || 'Failed to map employee')
    });
  }
}

