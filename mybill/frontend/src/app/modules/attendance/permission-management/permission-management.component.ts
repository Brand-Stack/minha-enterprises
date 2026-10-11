import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { PermissionRequest } from '../../../core/models/attendance.model';
import { PermissionService } from '../../../core/services/permission.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-permission-management',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header purple-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>access_time_filled</mat-icon>
          </div>
          <div>
            <h2>Permission Management</h2>
            <p class="subtitle">Request short duration permissions for official outdoor duty, late arrival, or early departure</p>
          </div>
        </div>

        <div class="header-actions">
          <div style="min-width: 220px;" *ngIf="isAdmin">
            <app-employee-selector label="Filter Employee" [allowAll]="true" (employeeChange)="onEmployeeFilterChange($event)"></app-employee-selector>
          </div>
          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
          <button mat-flat-button class="btn-primary-purple" (click)="openRequestModal = true">
            <mat-icon>add_circle</mat-icon> Request Permission
          </button>
        </div>
      </div>

      <!-- Admin Pending Queue -->
      <div class="section-card" *ngIf="isAdmin && pendingRequests.length > 0">
        <div class="section-header">
          <mat-icon class="section-icon pending">pending_actions</mat-icon>
          <h3>Pending Permission Approvals</h3>
          <span class="badge-count">{{ pendingRequests.length }} Pending</span>
        </div>

        <table mat-table [dataSource]="pendingRequests" class="full-width-table">
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

          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.date }}</td>
          </ng-container>

          <ng-container matColumnDef="time">
            <th mat-header-cell *matHeaderCellDef>Time Span</th>
            <td mat-cell *matCellDef="let r">
              <span class="time-pill">
                <mat-icon>schedule</mat-icon> {{ r.startTime }} - {{ r.endTime }}
              </span>
              <span class="duration-tag">({{ r.durationMinutes || 60 }} mins)</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="reason">
            <th mat-header-cell *matHeaderCellDef>Reason</th>
            <td mat-cell *matCellDef="let r" class="text-muted">{{ r.reason }}</td>
          </ng-container>

          <ng-container matColumnDef="action">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let r">
              <div class="action-btn-group">
                <button mat-flat-button class="btn-approve" (click)="approvePermission(r.id, true)">Approve</button>
                <button mat-flat-button class="btn-reject" (click)="approvePermission(r.id, false)">Reject</button>
              </div>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="['employee', 'date', 'time', 'reason', 'action']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['employee', 'date', 'time', 'reason', 'action'];" class="table-row"></tr>
        </table>
      </div>

      <!-- History -->
      <div class="section-card">
        <div class="section-header">
          <mat-icon class="section-icon history">history</mat-icon>
          <h3>Permission Request History</h3>
        </div>

        <table mat-table [dataSource]="myRequests" class="full-width-table">
          <ng-container matColumnDef="employeeName">
            <th mat-header-cell *matHeaderCellDef>Employee Name</th>
            <td mat-cell *matCellDef="let r">
              <div class="emp-cell">
                <div class="emp-avatar">{{ r.employeeName?.charAt(0) || 'E' }}</div>
                <div class="emp-name font-medium text-dark">{{ r.employeeName || '—' }}</div>
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="employeeCode">
            <th mat-header-cell *matHeaderCellDef>Employee ID</th>
            <td mat-cell *matCellDef="let r">
              <span class="emp-code font-semibold text-dark">{{ r.employeeCode || r.employeeId || '—' }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.date }}</td>
          </ng-container>

          <ng-container matColumnDef="time">
            <th mat-header-cell *matHeaderCellDef>Time Span</th>
            <td mat-cell *matCellDef="let r">
              <span class="time-pill">
                <mat-icon>schedule</mat-icon> {{ r.startTime }} - {{ r.endTime }}
              </span>
              <span class="duration-tag">({{ r.durationMinutes || 60 }} mins)</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let r">
              <span class="status-badge" [class]="r.status?.toLowerCase()">{{ r.status }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="reason">
            <th mat-header-cell *matHeaderCellDef>Reason</th>
            <td mat-cell *matCellDef="let r" class="text-muted">{{ r.reason }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let r">
              <div class="action-btn-group">
                <button *ngIf="canEdit" mat-icon-button color="primary" (click)="openEditModal(r)" matTooltip="Edit Permission">
                  <mat-icon>edit</mat-icon>
                </button>
                <button *ngIf="canDelete" mat-icon-button color="warn" (click)="promptDeletePermission(r)" matTooltip="Delete Permission">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="['employeeName', 'employeeCode', 'date', 'time', 'status', 'reason', 'actions']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['employeeName', 'employeeCode', 'date', 'time', 'status', 'reason', 'actions'];" class="table-row"></tr>
        </table>

        <div *ngIf="myRequests.length === 0" class="empty-state">
          <mat-icon class="empty-icon">more_time</mat-icon>
          <p class="empty-title">No Permission Requests Found</p>
          <p class="empty-desc">Click "Request Permission" to submit a new short permission request.</p>
        </div>
      </div>

      <!-- Edit Permission Modal -->
      <div class="modal-overlay" *ngIf="openEditPermissionModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">edit</mat-icon>
              <h3>Edit Permission Request</h3>
            </div>
            <button class="close-btn" (click)="openEditPermissionModal = false"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body" *ngIf="editRequestForm">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Employee Name</label>
                <input type="text" [value]="editRequestForm.employeeName || '—'" class="form-input" readonly disabled style="background-color: #f3f4f6; cursor: not-allowed;">
              </div>
              <div class="form-group">
                <label class="form-label">Employee Code</label>
                <input type="text" [value]="editRequestForm.employeeCode || '—'" class="form-input" readonly disabled style="background-color: #f3f4f6; cursor: not-allowed;">
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Permission Date</label>
              <input type="date" [(ngModel)]="editRequestForm.date" class="form-input">
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Start Time</label>
                <input type="time" [(ngModel)]="editRequestForm.startTime" class="form-input">
              </div>
              <div class="form-group">
                <label class="form-label">End Time</label>
                <input type="time" [(ngModel)]="editRequestForm.endTime" class="form-input">
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Reason / Justification</label>
              <textarea [(ngModel)]="editRequestForm.reason" class="form-textarea" rows="3" placeholder="Specify reason for permission..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openEditPermissionModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit" (click)="savePermissionEdit()">Save Changes</button>
          </div>
        </div>
      </div>

      <!-- Delete Permission Confirmation Modal -->
      <div class="modal-overlay" *ngIf="showDeletePermissionModal" (click)="showDeletePermissionModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <h3 style="color: #EF4444; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #EF4444;">warning</mat-icon> Confirm Delete Permission Request
            </h3>
            <button class="close-btn" (click)="showDeletePermissionModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete the permission request for <strong>{{ selectedPermissionToDelete?.employeeName || 'this record' }}</strong> on {{ selectedPermissionToDelete?.date }} ({{ selectedPermissionToDelete?.startTime }} - {{ selectedPermissionToDelete?.endTime }})?</p>
          </div>
          <div class="modal-footer">
            <button mat-button (click)="showDeletePermissionModal = false">Cancel</button>
            <button mat-flat-button color="warn" (click)="confirmDeletePermission()">Delete Record</button>
          </div>
        </div>
      </div>

      <!-- Request Modal -->
      <div class="modal-overlay" *ngIf="openRequestModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">access_time</mat-icon>
              <h3>Request Permission</h3>
            </div>
            <button class="close-btn" (click)="openRequestModal = false"><mat-icon>close</mat-icon></button>
          </div>

          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">Permission Date</label>
              <input type="date" [(ngModel)]="newRequest.date" class="form-input">
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">Start Time</label>
                <input type="time" [(ngModel)]="newRequest.startTime" class="form-input">
              </div>
              <div class="form-group">
                <label class="form-label">End Time</label>
                <input type="time" [(ngModel)]="newRequest.endTime" class="form-input">
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Reason / Justification</label>
              <textarea [(ngModel)]="newRequest.reason" class="form-textarea" rows="3" placeholder="Specify reason for permission..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openRequestModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit" (click)="submitPermission()">Submit Request</button>
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
      border-left: 6px solid #7C3AED;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #F3E8FF; 
      color: #7C3AED; 
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
    .btn-primary-purple { background: #7C3AED !important; color: white !important; font-weight: 600; border-radius: 8px; }

    .section-card { background: white; padding: 22px; border-radius: 14px; border: 1px solid #E2E8F0; margin-bottom: 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.03); }
    .section-header { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
    .section-icon { color: #7C3AED; font-size: 22px; width: 22px; height: 22px; }
    .section-icon.pending { color: #F59E0B; }
    .section-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; }
    .badge-count { background: #FEF3C7; color: #B45309; font-size: 11px; font-weight: 800; padding: 2px 8px; border-radius: 12px; }

    .full-width-table { width: 100%; border-collapse: collapse; }
    th.mat-header-cell { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; padding: 14px 16px; border-bottom: 1px solid #E2E8F0; }
    td.mat-cell { padding: 14px 16px; font-size: 13.5px; border-bottom: 1px solid #F1F5F9; }
    .table-row:hover { background-color: #F8FAFC; }

    .emp-cell { display: flex; align-items: center; gap: 10px; }
    .emp-avatar { width: 34px; height: 34px; border-radius: 50%; background: #F3E8FF; color: #6B21A8; font-weight: 700; font-size: 14px; display: flex; align-items: center; justify-content: center; }
    .emp-name { font-weight: 600; color: #0F172A; }
    .emp-code { font-size: 11px; color: #64748B; }

    .font-medium { font-weight: 600; }
    .text-dark { color: #0F172A; }
    .text-muted { color: #64748B; font-size: 13px; }

    .time-pill { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 600; background: #F3E8FF; color: #6B21A8; }
    .time-pill mat-icon { font-size: 14px; width: 14px; height: 14px; }
    .duration-tag { font-size: 12px; color: #64748B; font-weight: 600; margin-left: 6px; }

    .action-btn-group { display: flex; gap: 8px; }
    .btn-approve { background: #10B981 !important; color: white !important; font-size: 12px; border-radius: 6px; font-weight: 700; }
    .btn-reject { background: #EF4444 !important; color: white !important; font-size: 12px; border-radius: 6px; font-weight: 700; }

    .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-badge.approved { background: #DCFCE7; color: #15803D; }
    .status-badge.pending { background: #FEF3C7; color: #B45309; }
    .status-badge.rejected { background: #FEE2E2; color: #B91C1C; }

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
    .form-input, .form-textarea { padding: 10px 12px; border: 1px solid #CBD5E1; border-radius: 8px; font-size: 14px; outline: none; transition: border 0.2s; }
    .form-input:focus, .form-textarea:focus { border-color: #7C3AED; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }
    .btn-cancel { color: #64748B !important; font-weight: 600; }
    .btn-submit { background: #7C3AED !important; color: white !important; font-weight: 700; border-radius: 8px; }

    .empty-state { text-align: center; padding: 48px 24px; color: #64748B; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #CBD5E1; margin-bottom: 8px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #334155; margin: 0 0 4px 0; }
    .empty-desc { font-size: 13px; color: #94A3B8; margin: 0; }
  `]
})
export class PermissionManagementComponent implements OnInit {
  myRequests: PermissionRequest[] = [];
  pendingRequests: PermissionRequest[] = [];
  isAdmin = false;
  selectedEmployeeId: string = '';

  openRequestModal = false;
  newRequest: Partial<PermissionRequest> = {
    date: '',
    startTime: '',
    endTime: '',
    reason: ''
  };

  openEditPermissionModal = false;
  editRequestForm: Partial<PermissionRequest> | null = null;
  editingPermissionId: string | null = null;

  showDeletePermissionModal = false;
  selectedPermissionToDelete: PermissionRequest | null = null;

  constructor(
    private attendanceService: AttendanceService,
    private permissionService: PermissionService,
    private apiService: ApiService,
    private toastService: ToastService,
    private authService: AuthService
  ) {}

  get canEdit(): boolean {
    return this.isAdmin || this.permissionService.hasPermission('PERMISSIONS', 'EDIT');
  }

  get canDelete(): boolean {
    return this.isAdmin || this.permissionService.hasPermission('PERMISSIONS', 'DELETE');
  }

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    this.loadData();
  }

  onEmployeeFilterChange(empId: string): void {
    this.selectedEmployeeId = empId || '';
    this.loadData();
  }

  exportExcel(): void {
    this.apiService.getBlob('/permissions/export/excel', { employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Permission_Requests.xlsx';
        a.click();
      }
    });
  }

  exportPdf(): void {
    this.apiService.getBlob('/permissions/export/pdf', { employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Permission_Requests.pdf';
        a.click();
      }
    });
  }

  loadData(): void {
    if (this.isAdmin) {
      this.attendanceService.getAllPermissionRequests(this.selectedEmployeeId).subscribe({
        next: (data) => this.myRequests = data || [],
        error: (err) => console.error(err)
      });

      this.attendanceService.getPendingPermissionRequests().subscribe({
        next: (data) => this.pendingRequests = data || [],
        error: (err) => console.error(err)
      });
    } else {
      this.attendanceService.getMyPermissionRequests().subscribe({
        next: (data) => this.myRequests = data || [],
        error: (err) => console.error(err)
      });
    }
  }

  submitPermission(): void {
    if (!this.newRequest.date || !this.newRequest.startTime || !this.newRequest.endTime) {
      this.toastService.warning('Validation Error', 'Please fill Date, Start Time, and End Time');
      return;
    }

    if (!this.newRequest.employeeId) {
      this.newRequest.employeeId = this.authService.getCurrentUser()?.employeeId;
    }

    this.attendanceService.requestPermission(this.newRequest as PermissionRequest).subscribe({
      next: () => {
        this.toastService.success('Permission Request', 'Permission request submitted successfully!');
        this.openRequestModal = false;
        this.loadData();
      },
      error: (err) => this.toastService.error('Submission Failed', err?.error?.message || 'Failed to submit permission')
    });
  }

  openEditModal(req: PermissionRequest): void {
    this.editingPermissionId = req.id || null;
    this.editRequestForm = {
      employeeName: req.employeeName,
      employeeCode: req.employeeCode,
      date: req.date,
      startTime: req.startTime,
      endTime: req.endTime,
      reason: req.reason
    };
    this.openEditPermissionModal = true;
  }

  savePermissionEdit(): void {
    if (!this.editingPermissionId || !this.editRequestForm) return;
    if (!this.editRequestForm.date || !this.editRequestForm.startTime || !this.editRequestForm.endTime) {
      this.toastService.warning('Validation Error', 'Please fill Date, Start Time, and End Time');
      return;
    }

    this.attendanceService.updatePermission(this.editingPermissionId, this.editRequestForm).subscribe({
      next: () => {
        this.toastService.success('Permission Updated', 'Permission request updated successfully!');
        this.openEditPermissionModal = false;
        this.editingPermissionId = null;
        this.editRequestForm = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Update Failed', err?.error?.message || 'Failed to update permission')
    });
  }

  promptDeletePermission(req: PermissionRequest): void {
    this.selectedPermissionToDelete = req;
    this.showDeletePermissionModal = true;
  }

  confirmDeletePermission(): void {
    if (!this.selectedPermissionToDelete || !this.selectedPermissionToDelete.id) return;
    this.attendanceService.deletePermission(this.selectedPermissionToDelete.id).subscribe({
      next: () => {
        this.toastService.success('Permission Deleted', 'Permission request deleted successfully.');
        this.showDeletePermissionModal = false;
        this.selectedPermissionToDelete = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Delete Failed', err?.error?.message || 'Failed to delete permission')
    });
  }

  approvePermission(id: string, approve: boolean, remarks: string = ''): void {
    this.attendanceService.approveOrRejectPermission(id, approve, remarks).subscribe({
      next: () => {
        if (approve) {
          this.toastService.success('Success', 'Permission request approved successfully');
        } else {
          this.toastService.success('Success', 'Permission request rejected successfully');
        }
        this.loadData();
      },
      error: (err) => this.toastService.error('Action Failed', err?.error?.message || 'Failed to process request')
    });
  }
}

