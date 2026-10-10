import { Component, OnInit } from '@angular/core';
import { AttendanceService } from '../../../core/services/attendance.service';
import { LeaveEntitlement, LeaveRequest } from '../../../core/models/attendance.model';
import { PermissionService } from '../../../core/services/permission.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-leave-management',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header green-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>event_note</mat-icon>
          </div>
          <div>
            <h2>Leave Management</h2>
            <p class="subtitle">Apply for leave, view balance quotas (Casual, Medical, Emergency, Comp Off), and manage approvals</p>
          </div>
        </div>

        <div class="header-actions">
          <div style="min-width: 200px;">
            <app-employee-selector label="Filter Employee" [allowAll]="true" (employeeChange)="onEmployeeFilterChange($event)"></app-employee-selector>
          </div>
          <button mat-flat-button class="btn-export-excel" (click)="exportExcel()">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-flat-button class="btn-export-pdf" (click)="exportPdf()">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
          <button mat-flat-button class="btn-primary-green" (click)="openApplyModal = true">
            <mat-icon>add_circle</mat-icon> Apply Leave
          </button>
        </div>
      </div>

      <!-- Leave Balances Cards -->
      <div class="balances-grid" *ngIf="entitlement">
        <div class="balance-card casual">
          <div class="card-top">
            <span class="leave-type-title">Casual Leave</span>
            <mat-icon class="card-icon">beach_access</mat-icon>
          </div>
          <div class="balance-numbers">
            <span class="remaining">{{ entitlement.casualLeaveTotal - entitlement.casualLeaveUsed }}</span>
            <span class="total">/ {{ entitlement.casualLeaveTotal }} days</span>
          </div>
          <div class="progress-bar">
            <div class="fill blue" [style.width.%]="(entitlement.casualLeaveUsed / (entitlement.casualLeaveTotal || 1)) * 100"></div>
          </div>
        </div>

        <div class="balance-card medical">
          <div class="card-top">
            <span class="leave-type-title">Medical Leave</span>
            <mat-icon class="card-icon">local_hospital</mat-icon>
          </div>
          <div class="balance-numbers">
            <span class="remaining">{{ entitlement.medicalLeaveTotal - entitlement.medicalLeaveUsed }}</span>
            <span class="total">/ {{ entitlement.medicalLeaveTotal }} days</span>
          </div>
          <div class="progress-bar">
            <div class="fill green" [style.width.%]="(entitlement.medicalLeaveUsed / (entitlement.medicalLeaveTotal || 1)) * 100"></div>
          </div>
        </div>

        <div class="balance-card emergency">
          <div class="card-top">
            <span class="leave-type-title">Emergency Leave</span>
            <mat-icon class="card-icon">warning_amber</mat-icon>
          </div>
          <div class="balance-numbers">
            <span class="remaining">{{ entitlement.emergencyLeaveTotal - entitlement.emergencyLeaveUsed }}</span>
            <span class="total">/ {{ entitlement.emergencyLeaveTotal }} days</span>
          </div>
          <div class="progress-bar">
            <div class="fill red" [style.width.%]="(entitlement.emergencyLeaveUsed / (entitlement.emergencyLeaveTotal || 1)) * 100"></div>
          </div>
        </div>

        <div class="balance-card comp-off">
          <div class="card-top">
            <span class="leave-type-title">Comp Off</span>
            <mat-icon class="card-icon">work_history</mat-icon>
          </div>
          <div class="balance-numbers">
            <span class="remaining">{{ (entitlement.compOffTotal || 0) - (entitlement.compOffUsed || 0) }}</span>
            <span class="total">/ {{ entitlement.compOffTotal || 0 }} days</span>
          </div>
          <div class="progress-bar">
            <div class="fill purple" [style.width.%]="((entitlement.compOffUsed || 0) / (entitlement.compOffTotal || 1)) * 100"></div>
          </div>
        </div>
      </div>

      <!-- Admin Pending Approval Queue -->
      <div class="section-card" *ngIf="isAdmin && pendingRequests.length > 0">
        <div class="section-header">
          <mat-icon class="section-icon pending">pending_actions</mat-icon>
          <h3>Pending Approvals Queue</h3>
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

          <ng-container matColumnDef="leaveType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let r">
              <span class="type-pill" [class]="r.leaveType?.toLowerCase()">{{ formatType(r.leaveType) }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="dates">
            <th mat-header-cell *matHeaderCellDef>Dates</th>
            <td mat-cell *matCellDef="let r">
              <span class="font-medium text-dark">{{ r.fromDate }}</span> to <span class="font-medium text-dark">{{ r.toDate }}</span>
              <span class="days-pill">({{ r.numberOfDays }} day{{ r.numberOfDays > 1 ? 's' : '' }})</span>
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
                <button mat-flat-button class="btn-approve" (click)="approveLeave(r.id, true)">Approve</button>
                <button mat-flat-button class="btn-reject" (click)="approveLeave(r.id, false)">Reject</button>
              </div>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="['employee', 'leaveType', 'dates', 'reason', 'action']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['employee', 'leaveType', 'dates', 'reason', 'action'];" class="table-row"></tr>
        </table>
      </div>

      <!-- Leave Requests History -->
      <div class="section-card">
        <div class="section-header">
          <mat-icon class="section-icon history">history</mat-icon>
          <h3>Leave Applications History</h3>
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

          <ng-container matColumnDef="leaveType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let r">
              <span class="type-pill" [class]="r.leaveType?.toLowerCase()">{{ formatType(r.leaveType) }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="fromDate">
            <th mat-header-cell *matHeaderCellDef>From</th>
            <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.fromDate }}</td>
          </ng-container>

          <ng-container matColumnDef="toDate">
            <th mat-header-cell *matHeaderCellDef>To</th>
            <td mat-cell *matCellDef="let r" class="font-medium text-dark">{{ r.toDate }}</td>
          </ng-container>

          <ng-container matColumnDef="days">
            <th mat-header-cell *matHeaderCellDef>Days</th>
            <td mat-cell *matCellDef="let r">
              <span class="hours-chip">{{ r.numberOfDays }} day{{ r.numberOfDays > 1 ? 's' : '' }}</span>
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
              <div class="action-buttons">
                <button *ngIf="canEdit" mat-icon-button color="primary" (click)="openEditModal(r)" matTooltip="Edit Leave">
                  <mat-icon>edit</mat-icon>
                </button>
                <button *ngIf="canDelete" mat-icon-button color="warn" (click)="promptDeleteLeave(r)" matTooltip="Delete Leave">
                  <mat-icon>delete</mat-icon>
                </button>
              </div>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="['employeeName', 'employeeCode', 'leaveType', 'fromDate', 'toDate', 'days', 'status', 'reason', 'actions']"></tr>
          <tr mat-row *matRowDef="let row; columns: ['employeeName', 'employeeCode', 'leaveType', 'fromDate', 'toDate', 'days', 'status', 'reason', 'actions'];" class="table-row"></tr>
        </table>

        <div *ngIf="myRequests.length === 0" class="empty-state">
          <mat-icon class="empty-icon">note_add</mat-icon>
          <p class="empty-title">No Leave Applications Found</p>
          <p class="empty-desc">Click "Apply Leave" to submit a new request.</p>
        </div>
      </div>

      <!-- Edit Leave Modal Dialog -->
      <div class="modal-overlay" *ngIf="openEditLeaveModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">edit</mat-icon>
              <h3>Edit Leave Request</h3>
            </div>
            <button class="close-btn" (click)="openEditLeaveModal = false"><mat-icon>close</mat-icon></button>
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
              <label class="form-label">Leave Type</label>
              <select [(ngModel)]="editRequestForm.leaveType" class="form-select">
                <option value="CASUAL_LEAVE">Casual Leave</option>
                <option value="MEDICAL_LEAVE">Medical Leave</option>
                <option value="EMERGENCY_LEAVE">Emergency Leave</option>
                <option value="COMP_OFF">Comp Off</option>
                <option value="OTHER_LEAVE">Other Leave</option>
              </select>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">From Date</label>
                <input type="date" [(ngModel)]="editRequestForm.fromDate" class="form-input">
              </div>
              <div class="form-group">
                <label class="form-label">To Date</label>
                <input type="date" [(ngModel)]="editRequestForm.toDate" class="form-input">
              </div>
            </div>

            <div class="checkbox-group">
              <label class="checkbox-label">
                <input type="checkbox" [(ngModel)]="editRequestForm.isHalfDay" class="checkbox-input">
                <span>Is Half Day Leave?</span>
              </label>
            </div>

            <div class="form-group">
              <label class="form-label">Reason / Justification</label>
              <textarea [(ngModel)]="editRequestForm.reason" class="form-textarea" rows="3" placeholder="Provide reason for leave request..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openEditLeaveModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit" (click)="saveLeaveEdit()">Save Changes</button>
          </div>
        </div>
      </div>

      <!-- Delete Leave Confirmation Modal -->
      <div class="modal-overlay" *ngIf="showDeleteLeaveModal" (click)="showDeleteLeaveModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <h3 style="color: #EF4444; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #EF4444;">warning</mat-icon> Confirm Delete Leave Request
            </h3>
            <button class="close-btn" (click)="showDeleteLeaveModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete the leave request for <strong>{{ selectedLeaveToDelete?.employeeName || 'this record' }}</strong> ({{ selectedLeaveToDelete?.fromDate }} to {{ selectedLeaveToDelete?.toDate }})?</p>
          </div>
          <div class="modal-footer">
            <button mat-button (click)="showDeleteLeaveModal = false">Cancel</button>
            <button mat-flat-button color="warn" (click)="confirmDeleteLeave()">Delete Record</button>
          </div>
        </div>
      </div>

      <!-- Apply Leave Modal Dialog -->
      <div class="modal-overlay" *ngIf="openApplyModal">
        <div class="modal-card">
          <div class="modal-header">
            <div class="modal-title">
              <mat-icon color="primary">add_circle</mat-icon>
              <h3>Apply for Leave</h3>
            </div>
            <button class="close-btn" (click)="openApplyModal = false"><mat-icon>close</mat-icon></button>
          </div>
          
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">Leave Type</label>
              <select [(ngModel)]="newRequest.leaveType" class="form-select">
                <option value="CASUAL_LEAVE">Casual Leave</option>
                <option value="MEDICAL_LEAVE">Medical Leave</option>
                <option value="EMERGENCY_LEAVE">Emergency Leave</option>
                <option value="COMP_OFF">Comp Off</option>
                <option value="OTHER_LEAVE">Other Leave</option>
              </select>
            </div>

            <div class="form-row">
              <div class="form-group">
                <label class="form-label">From Date</label>
                <input type="date" [(ngModel)]="newRequest.fromDate" class="form-input">
              </div>
              <div class="form-group">
                <label class="form-label">To Date</label>
                <input type="date" [(ngModel)]="newRequest.toDate" class="form-input">
              </div>
            </div>

            <div class="checkbox-group">
              <label class="checkbox-label">
                <input type="checkbox" [(ngModel)]="newRequest.isHalfDay" class="checkbox-input">
                <span>Is Half Day Leave?</span>
              </label>
            </div>

            <div class="form-group">
              <label class="form-label">Reason / Justification</label>
              <textarea [(ngModel)]="newRequest.reason" class="form-textarea" rows="3" placeholder="Provide detailed reason for leave request..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button mat-button class="btn-cancel" (click)="openApplyModal = false">Cancel</button>
            <button mat-flat-button class="btn-submit" (click)="submitLeave()">Submit Application</button>
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
      border-left: 6px solid #059669;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #D1FAE5; 
      color: #059669; 
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
    .btn-primary-green { background: #059669 !important; color: white !important; font-weight: 600; border-radius: 8px; }

    .balances-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .balance-card { background: white; padding: 20px; border-radius: 14px; border: 1px solid #E2E8F0; box-shadow: 0 2px 8px rgba(0,0,0,0.03); }
    .card-top { display: flex; justify-content: space-between; align-items: center; }
    .leave-type-title { font-size: 13px; font-weight: 700; color: #64748B; text-transform: uppercase; letter-spacing: 0.5px; }
    .card-icon { color: #94A3B8; font-size: 22px; width: 22px; height: 22px; }

    .balance-numbers { font-size: 26px; font-weight: 800; color: #0F172A; margin: 10px 0; }
    .balance-numbers .total { font-size: 14px; font-weight: 500; color: #94A3B8; }

    .progress-bar { height: 7px; background: #F1F5F9; border-radius: 4px; overflow: hidden; }
    .progress-bar .fill { height: 100%; }
    .progress-bar .fill.blue { background: #2563EB; }
    .progress-bar .fill.green { background: #10B981; }
    .progress-bar .fill.red { background: #EF4444; }
    .progress-bar .fill.purple { background: #8B5CF6; }

    .section-card { background: white; padding: 22px; border-radius: 14px; border: 1px solid #E2E8F0; margin-bottom: 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.03); }
    .section-header { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
    .section-icon { color: #059669; font-size: 22px; width: 22px; height: 22px; }
    .section-icon.pending { color: #F59E0B; }
    .section-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; }
    .badge-count { background: #FEF3C7; color: #B45309; font-size: 11px; font-weight: 800; padding: 2px 8px; border-radius: 12px; }

    .full-width-table { width: 100%; border-collapse: collapse; }
    th.mat-header-cell { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px; padding: 14px 16px; border-bottom: 1px solid #E2E8F0; }
    td.mat-cell { padding: 14px 16px; font-size: 13.5px; border-bottom: 1px solid #F1F5F9; }
    .table-row:hover { background-color: #F8FAFC; }

    .emp-cell { display: flex; align-items: center; gap: 10px; }
    .emp-avatar { width: 34px; height: 34px; border-radius: 50%; background: #D1FAE5; color: #047857; font-weight: 700; font-size: 14px; display: flex; align-items: center; justify-content: center; }
    .emp-name { font-weight: 600; color: #0F172A; }
    .emp-code { font-size: 11px; color: #64748B; }

    .type-pill { padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; }
    .type-pill.casual_leave { background: #EFF6FF; color: #1D4ED8; }
    .type-pill.medical_leave { background: #ECFDF5; color: #047857; }
    .type-pill.emergency_leave { background: #FEF2F2; color: #B91C1C; }
    .type-pill.comp_off { background: #F3E8FF; color: #7E22CE; }

    .font-medium { font-weight: 600; }
    .text-dark { color: #0F172A; }
    .text-muted { color: #64748B; font-size: 13px; }
    .days-pill { font-size: 12px; color: #64748B; font-weight: 600; margin-left: 6px; }

    .action-btn-group { display: flex; gap: 8px; }
    .btn-approve { background: #10B981 !important; color: white !important; font-size: 12px; border-radius: 6px; font-weight: 700; }
    .btn-reject { background: #EF4444 !important; color: white !important; font-size: 12px; border-radius: 6px; font-weight: 700; }

    .hours-chip { background: #F1F5F9; color: #334155; font-weight: 700; padding: 4px 10px; border-radius: 6px; font-size: 12px; }

    .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }
    .status-badge.approved { background: #DCFCE7; color: #15803D; }
    .status-badge.pending { background: #FEF3C7; color: #B45309; }
    .status-badge.rejected { background: #FEE2E2; color: #B91C1C; }

    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); }
    .modal-card { background: white; border-radius: 16px; width: 480px; max-width: 92%; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
    .modal-header { padding: 18px 24px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center; }
    .modal-title { display: flex; align-items: center; gap: 10px; }
    .modal-title h3 { font-size: 17px; font-weight: 700; color: #0F172A; margin: 0; }
    .close-btn { background: none; border: none; color: #94A3B8; cursor: pointer; display: flex; }
    .modal-body { padding: 24px; }
    .form-group { margin-bottom: 16px; display: flex; flex-direction: column; gap: 6px; }
    .form-row { display: flex; gap: 12px; }
    .form-row .form-group { flex: 1; }
    .form-label { font-size: 13px; font-weight: 600; color: #475569; }
    .form-input, .form-select, .form-textarea { padding: 10px 12px; border: 1px solid #CBD5E1; border-radius: 8px; font-size: 14px; outline: none; transition: border 0.2s; }
    .form-input:focus, .form-select:focus, .form-textarea:focus { border-color: #059669; }
    .checkbox-group { margin-bottom: 16px; }
    .checkbox-label { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: #475569; cursor: pointer; }
    .modal-footer { padding: 16px 24px; background: #F8FAFC; border-top: 1px solid #E2E8F0; display: flex; justify-content: flex-end; gap: 10px; }
    .btn-cancel { color: #64748B !important; font-weight: 600; }
    .btn-submit { background: #059669 !important; color: white !important; font-weight: 700; border-radius: 8px; }

    .empty-state { text-align: center; padding: 48px 24px; color: #64748B; }
    .empty-icon { font-size: 48px; width: 48px; height: 48px; color: #CBD5E1; margin-bottom: 8px; }
    .empty-title { font-size: 16px; font-weight: 700; color: #334155; margin: 0 0 4px 0; }
    .empty-desc { font-size: 13px; color: #94A3B8; margin: 0; }
  `]
})
export class LeaveManagementComponent implements OnInit {
  entitlement: LeaveEntitlement | null = null;
  myRequests: LeaveRequest[] = [];
  pendingRequests: LeaveRequest[] = [];
  isAdmin = false;
  selectedEmployeeId: string = '';

  openApplyModal = false;
  newRequest: Partial<LeaveRequest> = {
    leaveType: 'CASUAL_LEAVE',
    fromDate: '',
    toDate: '',
    isHalfDay: false,
    reason: ''
  };
  openEditLeaveModal = false;
  editRequestForm: Partial<LeaveRequest> | null = null;
  editingLeaveId: string | null = null;

  showDeleteLeaveModal = false;
  selectedLeaveToDelete: LeaveRequest | null = null;

  constructor(
    private attendanceService: AttendanceService,
    private permissionService: PermissionService,
    private apiService: ApiService,
    private toastService: ToastService,
    private authService: AuthService
  ) {}

  get canEdit(): boolean {
    return this.isAdmin || this.permissionService.hasPermission('LEAVES', 'EDIT');
  }

  get canDelete(): boolean {
    return this.isAdmin || this.permissionService.hasPermission('LEAVES', 'DELETE');
  }

  formatType(type?: string): string {
    if (!type) return '';
    return type.replace('_', ' ');
  }

  onEmployeeFilterChange(empId: string): void {
    this.selectedEmployeeId = empId || '';
    this.loadData();
  }

  exportExcel(): void {
    this.apiService.getBlob('/leaves/export/excel', { employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Leave_Requests.xlsx';
        a.click();
      }
    });
  }

  exportPdf(): void {
    this.apiService.getBlob('/leaves/export/pdf', { employeeId: this.selectedEmployeeId }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Leave_Requests.pdf';
        a.click();
      }
    });
  }

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    this.loadData();
  }

  loadData(): void {
    this.attendanceService.getMyLeaveEntitlement().subscribe({
      next: (data) => this.entitlement = data,
      error: (err) => console.error(err)
    });

    if (this.isAdmin) {
      this.attendanceService.getAllLeaveRequests(this.selectedEmployeeId).subscribe({
        next: (data) => {
          this.myRequests = data || [];
          this.pendingRequests = (data || []).filter(r => r.status === 'PENDING');
        },
        error: (err) => console.error(err)
      });
    } else {
      this.attendanceService.getMyLeaveRequests().subscribe({
        next: (data) => this.myRequests = data || [],
        error: (err) => console.error(err)
      });
    }
  }

  submitLeave(): void {
    if (!this.newRequest.fromDate || !this.newRequest.toDate) {
      this.toastService.warning('Validation Error', 'Please select From and To dates');
      return;
    }

    if (!this.newRequest.employeeId) {
      this.newRequest.employeeId = this.authService.getCurrentUser()?.employeeId;
    }

    this.attendanceService.applyLeave(this.newRequest as LeaveRequest).subscribe({
      next: () => {
        this.toastService.success('Leave Application', 'Leave request submitted successfully!');
        this.openApplyModal = false;
        this.loadData();
      },
      error: (err) => this.toastService.error('Submission Failed', err?.error?.message || 'Failed to submit leave')
    });
  }

  openEditModal(req: LeaveRequest): void {
    this.editingLeaveId = req.id || null;
    this.editRequestForm = {
      employeeName: req.employeeName,
      employeeCode: req.employeeCode,
      leaveType: req.leaveType,
      fromDate: req.fromDate,
      toDate: req.toDate,
      isHalfDay: req.isHalfDay,
      reason: req.reason
    };
    this.openEditLeaveModal = true;
  }

  saveLeaveEdit(): void {
    if (!this.editingLeaveId || !this.editRequestForm) return;
    if (!this.editRequestForm.fromDate || !this.editRequestForm.toDate) {
      this.toastService.warning('Validation Error', 'Please select From and To dates');
      return;
    }

    this.attendanceService.updateLeave(this.editingLeaveId, this.editRequestForm).subscribe({
      next: () => {
        this.toastService.success('Leave Updated', 'Leave request updated successfully!');
        this.openEditLeaveModal = false;
        this.editingLeaveId = null;
        this.editRequestForm = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Update Failed', err?.error?.message || 'Failed to update leave')
    });
  }

  promptDeleteLeave(req: LeaveRequest): void {
    this.selectedLeaveToDelete = req;
    this.showDeleteLeaveModal = true;
  }

  confirmDeleteLeave(): void {
    if (!this.selectedLeaveToDelete || !this.selectedLeaveToDelete.id) return;
    this.attendanceService.deleteLeave(this.selectedLeaveToDelete.id).subscribe({
      next: () => {
        this.toastService.success('Leave Deleted', 'Leave request deleted successfully.');
        this.showDeleteLeaveModal = false;
        this.selectedLeaveToDelete = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Delete Failed', err?.error?.message || 'Failed to delete leave')
    });
  }

  approveLeave(id: string, approve: boolean, remarks: string = ''): void {
    this.attendanceService.approveOrRejectLeave(id, approve, remarks).subscribe({
      next: () => {
        if (approve) {
          this.toastService.success('Success', 'Leave request approved successfully');
        } else {
          this.toastService.success('Success', 'Leave request rejected successfully');
        }
        this.loadData();
      },
      error: (err) => this.toastService.error('Action Failed', err?.error?.message || 'Failed to process request')
    });
  }
}
