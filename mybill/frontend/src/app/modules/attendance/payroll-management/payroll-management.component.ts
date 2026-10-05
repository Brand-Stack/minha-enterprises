import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AttendanceService } from '../../../core/services/attendance.service';
import { AdvanceTransaction, AdvanceTransactionType, EmployeeAdvanceAccount, EmployeeBonus, PayrollRecord, SalaryStructure } from '../../../core/models/attendance.model';
import { PermissionService } from '../../../core/services/permission.service';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-payroll-management',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header emerald-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>payments</mat-icon>
          </div>
          <div>
            <h2>Payroll & Payslip Management</h2>
            <p class="subtitle">Monthly payroll processing, employee salary advance accounts, repayment ledgers, and snapshot payslips</p>
          </div>
        </div>

        <div class="header-actions">
          <div style="min-width: 220px;" *ngIf="isAdmin && (activeTab === 'EMPLOYEE_PAYROLL' || activeTab === 'SALARY_ADVANCES')">
            <app-employee-selector label="Select Employee" [allowAll]="true" (employeeChange)="onEmployeeFilterChange($event)"></app-employee-selector>
          </div>
          <div class="month-picker-wrapper" *ngIf="activeTab === 'EMPLOYEE_PAYROLL' || activeTab === 'REPORTS'">
            <mat-icon class="calendar-icon">calendar_month</mat-icon>
            <input type="month" [(ngModel)]="payrollMonth" class="month-picker" (change)="loadData()">
          </div>

          <button *ngIf="isAdmin && activeTab === 'EMPLOYEE_PAYROLL' && selectedEmployeeId" mat-flat-button class="btn-primary-blue" (click)="generateSelectedEmployeePayslip()">
            <mat-icon>person</mat-icon> Generate Payslip (Selected Employee)
          </button>
          <button *ngIf="isAdmin && activeTab === 'EMPLOYEE_PAYROLL' && !selectedEmployeeId" mat-flat-button class="btn-primary-emerald" (click)="promptBatchProcessPayroll()">
            <mat-icon>engineering</mat-icon> Generate All Payslips
          </button>
          <button *ngIf="isAdmin && activeTab === 'SALARY_ADVANCES'" mat-flat-button class="btn-primary-amber" (click)="openAddAdvanceModal()">
            <mat-icon>add_circle</mat-icon> Add Salary Advance
          </button>
          <button *ngIf="isAdmin && activeTab === 'SALARY_ADVANCES'" mat-flat-button class="btn-primary-blue" (click)="openRecordRepaymentModal()">
            <mat-icon>price_check</mat-icon> Record Repayment
          </button>
        </div>
      </div>

      <!-- Navigation Tabs -->
      <div class="nav-tabs-bar">
        <button *ngIf="isAdmin" class="tab-btn" [class.active]="activeTab === 'EMPLOYEE_PAYROLL'" (click)="activeTab = 'EMPLOYEE_PAYROLL'">
          <mat-icon>people_alt</mat-icon> Monthly Payroll
        </button>
        <button *ngIf="isAdmin" class="tab-btn" [class.active]="activeTab === 'SALARY_MANAGEMENT'" (click)="activeTab = 'SALARY_MANAGEMENT'">
          <mat-icon>account_balance</mat-icon> Salary Management
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'SALARY_ADVANCES'" (click)="activeTab = 'SALARY_ADVANCES'">
          <mat-icon>account_balance_wallet</mat-icon> {{ isAdmin ? 'Salary Advance Management' : 'My Salary Advance' }}
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'MY_PAYSLIPS'" (click)="activeTab = 'MY_PAYSLIPS'">
          <mat-icon>badge</mat-icon> My Payslips
        </button>
        <button class="tab-btn" [class.active]="activeTab === 'REPORTS'" (click)="activeTab = 'REPORTS'">
          <mat-icon>assessment</mat-icon> Reports & Statement Exports
        </button>
      </div>

      <!-- TAB 1: MONTHLY EMPLOYEE PAYROLL LIST (ADMIN VIEW) -->
      <div *ngIf="activeTab === 'EMPLOYEE_PAYROLL' && isAdmin">
        <!-- KPI Cards -->
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-title">Total Employees</div>
            <div class="kpi-value">{{ kpis.totalEmployees }}</div>
            <div class="kpi-sub">Active staff</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-title">Total Gross Payroll</div>
            <div class="kpi-value text-blue">₹{{ kpis.totalGross | number:'1.2-2' }}</div>
            <div class="kpi-sub">Earnings & bonuses</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-title">Total Deductions</div>
            <div class="kpi-value text-red">₹{{ kpis.totalDeductions | number:'1.2-2' }}</div>
            <div class="kpi-sub">PF, Tax, LOP, Advance</div>
          </div>
          <div class="kpi-card highlight">
            <div class="kpi-title">Net Payable</div>
            <div class="kpi-value text-emerald">₹{{ kpis.totalNet | number:'1.2-2' }}</div>
            <div class="kpi-sub">Final payout</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-title">Processed / Generated</div>
            <div class="kpi-value">{{ kpis.processedCount }} / {{ kpis.totalEmployees }}</div>
            <div class="kpi-sub">Payslips finalized</div>
          </div>
        </div>

        <!-- Filter & Search Bar -->
        <div class="filter-card">
          <div class="search-input-wrapper">
            <mat-icon>search</mat-icon>
            <input type="text" [(ngModel)]="searchQuery" (input)="applyFilters()" placeholder="Search employee name or code..." class="search-input">
          </div>

          <div class="filter-group">
            <label>Department:</label>
            <select [(ngModel)]="selectedDept" (change)="applyFilters()" class="filter-select">
              <option value="ALL">All Departments</option>
              <option *ngFor="let dept of departments" [value]="dept">{{ dept }}</option>
            </select>
          </div>

          <div class="filter-group">
            <label>Status:</label>
            <select [(ngModel)]="selectedStatus" (change)="applyFilters()" class="filter-select">
              <option value="ALL">All Statuses</option>
              <option value="DRAFT">Draft</option>
              <option value="GENERATED">Generated</option>
              <option value="LOCKED">Locked</option>
            </select>
          </div>
        </div>

        <!-- Employee Payroll Table -->
        <div class="section-card">
          <div class="section-header">
            <mat-icon class="section-icon">receipt_long</mat-icon>
            <h3>Employee Monthly Payroll Records — {{ payrollMonth }}</h3>
          </div>

          <div class="table-container">
            <table class="custom-table wide-table">
              <thead>
                <tr>
                  <th>Payslip No</th>
                  <th>Employee</th>
                  <th>Department</th>
                  <th>Working / LOP</th>
                  <th>Gross Salary</th>
                  <th>Deductions</th>
                  <th>Net Salary</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let p of filteredPayrolls">
                  <td>
                    <span class="payslip-code">{{ p.payslipNumber || 'DRAFT' }}</span>
                  </td>
                  <td>
                    <div class="emp-cell">
                      <div class="emp-avatar">{{ p.employeeName?.charAt(0) || 'E' }}</div>
                      <div>
                        <div class="emp-name">{{ p.employeeName }}</div>
                        <div class="emp-code">{{ p.employeeCode }}</div>
                      </div>
                    </div>
                  </td>
                  <td>{{ p.department || 'General' }}</td>
                  <td>
                    <div class="working-cell">
                      <span class="days-chip">{{ p.presentDays || 0 }}d Present</span>
                      <span *ngIf="(p.lopDays || 0) > 0" class="lop-chip">{{ p.lopDays }}d LOP</span>
                    </div>
                  </td>
                  <td class="font-medium text-dark">₹{{ p.grossSalary | number:'1.2-2' }}</td>
                  <td class="font-medium text-red">-₹{{ p.totalDeductions | number:'1.2-2' }}</td>
                  <td>
                    <span class="net-salary-badge">₹{{ p.netSalary | number:'1.2-2' }}</span>
                  </td>
                  <td>
                    <span class="status-badge" [ngClass]="p.status?.toLowerCase() || 'draft'">
                      <mat-icon *ngIf="p.isLocked" class="lock-icon">lock</mat-icon>
                      {{ p.isLocked ? 'LOCKED' : (p.status || 'DRAFT') }}
                    </span>
                  </td>
                  <td>
                    <div class="action-buttons">
                      <button mat-flat-button class="btn-generate-individual" (click)="initiateIndividualGenerate(p)">
                        <mat-icon>send</mat-icon> Generate Payslip
                      </button>
                      <button mat-stroked-button class="btn-manage" (click)="openDrawer(p)">
                        <mat-icon>edit</mat-icon> Edit
                      </button>
                      <button mat-stroked-button class="btn-download" (click)="downloadPdf(p.employeeId, p.payrollMonth)">
                        <mat-icon>picture_as_pdf</mat-icon> PDF
                      </button>
                      <button mat-stroked-button class="btn-delete" (click)="promptDeletePayslip(p)">
                        <mat-icon>delete</mat-icon> Delete
                      </button>
                    </div>
                  </td>
                </tr>
                <tr *ngIf="filteredPayrolls.length === 0">
                  <td colspan="9" class="empty-cell">
                    <mat-icon class="empty-icon">search_off</mat-icon>
                    <p>No employee payroll records match the selected filters for {{ payrollMonth }}.</p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- TAB: SALARY MANAGEMENT (ADMIN VIEW - SOURCE OF TRUTH) -->
      <div *ngIf="activeTab === 'SALARY_MANAGEMENT' && isAdmin">
        <div class="section-card">
          <div class="section-header" style="justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 10px;">
              <mat-icon class="section-icon">account_balance</mat-icon>
              <h3>Salary Management — Employee Base Salary Configuration</h3>
            </div>
            <div style="min-width: 260px;">
              <app-employee-selector label="Select Employee to Manage Salary" [allowAll]="false" (employeeChange)="onSalaryStructEmployeeChange($event)"></app-employee-selector>
            </div>
          </div>

          <div *ngIf="selectedSalaryStructEmployeeId; else noSalaryEmpSelected" class="salary-struct-form-box margin-top">
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; background: #F0FDF4; padding: 12px 16px; border-radius: 8px; border: 1px solid #A7F3D0;">
              <div>
                <strong style="color: #047857; font-size: 15px;">{{ currentSalaryStruct.employeeName || 'Selected Employee' }}</strong>
                <span style="font-size: 12px; color: #475569; margin-left: 10px;">(Code: {{ currentSalaryStruct.employeeCode || 'N/A' }})</span>
              </div>
              <span class="badge-count" style="background: #D1FAE5; color: #047857;">Authoritative Salary Source</span>
            </div>

            <div class="form-grid-2">
              <div class="field-group">
                <label class="font-bold">Basic Salary (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.basicSalary" class="drawer-input">
                <span class="text-muted" style="font-size: 11px;">* Loss of Pay (LOP) is calculated strictly on Basic Salary / Total Working Days</span>
              </div>

              <div class="field-group">
                <label class="font-bold">House Rent Allowance / HRA (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.hra" class="drawer-input">
              </div>

              <div class="field-group">
                <label class="font-bold">Allowances (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.allowances" class="drawer-input">
              </div>

              <div class="field-group">
                <label class="font-bold">Provident Fund / PF Deduction (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.pfDeduction" class="drawer-input">
              </div>

              <div class="field-group">
                <label class="font-bold">Professional Tax (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.professionalTax" class="drawer-input">
              </div>

              <div class="field-group">
                <label class="font-bold">Income Tax / TDS (₹)</label>
                <input type="number" [(ngModel)]="currentSalaryStruct.incomeTax" class="drawer-input">
              </div>
            </div>

            <div style="display: flex; justify-content: flex-end; margin-top: 20px;">
              <button mat-flat-button class="btn-primary-emerald" (click)="saveSalaryStructure()">
                <mat-icon>save</mat-icon> Save Salary Configuration
              </button>
            </div>
          </div>

          <ng-template #noSalaryEmpSelected>
            <div class="empty-cell" style="padding: 48px !important;">
              <mat-icon class="empty-icon">person_search</mat-icon>
              <p>Please select an employee from the dropdown above to view and configure their base salary components.</p>
            </div>
          </ng-template>
        </div>
      </div>

      <!-- TAB 2: SALARY ADVANCE MANAGEMENT (ADMIN & EMPLOYEE VIEWS) -->
      <div *ngIf="activeTab === 'SALARY_ADVANCES'">
        <div class="section-card">
          <div class="section-header" style="justify-content: space-between;">
            <div style="display: flex; align-items: center; gap: 10px;">
              <mat-icon class="section-icon">account_balance_wallet</mat-icon>
              <h3>Salary Advance Account — {{ selectedEmployeeName || 'All Employees' }}</h3>
            </div>
            <div *ngIf="isAdmin" class="advance-header-actions">
              <button mat-flat-button class="btn-primary-amber" (click)="openAddAdvanceModal()">
                <mat-icon>add_circle</mat-icon> Add Advance
              </button>
              <button mat-flat-button class="btn-primary-blue" (click)="openRecordRepaymentModal()">
                <mat-icon>price_check</mat-icon> Repayment
              </button>
            </div>
          </div>

          <!-- Advance Account Balance Card -->
          <div *ngIf="activeAdvanceAccount" class="advance-summary-card">
            <div class="adv-card-col">
              <span class="adv-card-label">Total Advance Given</span>
              <span class="adv-card-val">₹{{ activeAdvanceAccount.totalAdvanceGiven || 0 | number:'1.2-2' }}</span>
            </div>
            <div class="adv-card-divider"></div>
            <div class="adv-card-col">
              <span class="adv-card-label">Total Repaid (Direct + Payroll)</span>
              <span class="adv-card-val text-blue">₹{{ (activeAdvanceAccount.totalRepaid || 0) + (activeAdvanceAccount.totalPayrollDeducted || 0) | number:'1.2-2' }}</span>
            </div>
            <div class="adv-card-divider"></div>
            <div class="adv-card-col highlight-outstanding">
              <span class="adv-card-label">Outstanding Advance Balance</span>
              <span class="adv-card-val text-red font-extrabold">₹{{ activeAdvanceAccount.outstandingBalance || 0 | number:'1.2-2' }}</span>
            </div>
          </div>

          <!-- Transaction Ledger History Table -->
          <div class="history-table-container margin-top">
            <div class="history-table-header">
              <h4>Transaction History Ledger</h4>
              <span class="badge-count">{{ activeAdvanceTransactions.length }} Entries</span>
            </div>

            <table class="custom-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Employee</th>
                  <th>Transaction Type</th>
                  <th>Amount</th>
                  <th>Payment Mode & Ref</th>
                  <th>Description / Reason</th>
                  <th>Resulting Balance</th>
                  <th>Added By</th>
                  <th *ngIf="isAdmin">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tx of activeAdvanceTransactions">
                  <td>{{ tx.transactionDate }}</td>
                  <td>
                    <div class="font-medium text-dark">{{ tx.employeeName || selectedEmployeeName || 'N/A' }}</div>
                    <div *ngIf="tx.employeeCode" class="text-muted" style="font-size: 11px;">{{ tx.employeeCode }}</div>
                  </td>
                  <td>
                    <span class="status-badge" [ngClass]="tx.transactionType.toLowerCase()">
                      {{ formatTxType(tx.transactionType) }}
                    </span>
                  </td>
                  <td class="font-bold text-dark">₹{{ tx.amount | number:'1.2-2' }}</td>
                  <td>
                    <span class="font-medium">{{ tx.paymentMode || 'N/A' }}</span>
                    <span *ngIf="tx.otherPaymentModeDetails" class="text-muted"> ({{ tx.otherPaymentModeDetails }})</span>
                    <div *ngIf="tx.referenceNumber" class="text-muted" style="font-size: 11px;">Ref: {{ tx.referenceNumber }}</div>
                  </td>
                  <td>
                    <div>{{ tx.description || tx.reason || '—' }}</div>
                    <div *ngIf="tx.notes" class="text-muted" style="font-size: 11px;">Notes: {{ tx.notes }}</div>
                  </td>
                  <td class="font-bold text-dark">₹{{ tx.resultingBalance | number:'1.2-2' }}</td>
                  <td>{{ tx.createdBy || 'System' }}</td>
                  <td *ngIf="isAdmin">
                    <div class="action-buttons">
                      <button mat-stroked-button class="btn-manage" (click)="openEditTransactionModal(tx)">
                        <mat-icon>edit</mat-icon> Edit
                      </button>
                      <button mat-stroked-button class="btn-delete" (click)="promptDeleteTransaction(tx)">
                        <mat-icon>delete</mat-icon> Delete
                      </button>
                    </div>
                  </td>
                </tr>
                <tr *ngIf="activeAdvanceTransactions.length === 0">
                  <td [attr.colspan]="isAdmin ? 9 : 8" class="empty-cell">
                    <mat-icon class="empty-icon">account_balance_wallet</mat-icon>
                    <p>No salary advance transactions recorded for this employee.</p>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- TAB 3: MY PAYSLIPS (EMPLOYEE VIEW) -->
      <div *ngIf="activeTab === 'MY_PAYSLIPS'">
        <div class="grid-2-col">
          <!-- Advance Account Ledger Summary -->
          <div class="section-card">
            <div class="section-header">
              <mat-icon class="section-icon">account_balance_wallet</mat-icon>
              <h3>My Salary Advance Account</h3>
            </div>

            <div *ngIf="myAdvanceAccount" class="advance-summary-card">
              <div class="adv-card-col">
                <span class="adv-card-label">Total Given</span>
                <span class="adv-card-val">₹{{ myAdvanceAccount.totalAdvanceGiven || 0 | number:'1.2-2' }}</span>
              </div>
              <div class="adv-card-divider"></div>
              <div class="adv-card-col">
                <span class="adv-card-label">Total Repaid</span>
                <span class="adv-card-val text-blue">₹{{ (myAdvanceAccount.totalRepaid || 0) + (myAdvanceAccount.totalPayrollDeducted || 0) | number:'1.2-2' }}</span>
              </div>
              <div class="adv-card-divider"></div>
              <div class="adv-card-col highlight-outstanding">
                <span class="adv-card-label">Outstanding Balance</span>
                <span class="adv-card-val text-red font-extrabold">₹{{ myAdvanceAccount.outstandingBalance || 0 | number:'1.2-2' }}</span>
              </div>
            </div>

            <div class="history-table-container margin-top">
              <div class="history-table-header">
                <h4>Advance History Ledger</h4>
              </div>
              <table class="custom-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Type</th>
                    <th>Amount</th>
                    <th>Payment Mode</th>
                    <th>Resulting Balance</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let tx of myAdvanceTransactions">
                    <td>{{ tx.transactionDate }}</td>
                    <td>
                      <span class="status-badge" [ngClass]="tx.transactionType.toLowerCase()">{{ formatTxType(tx.transactionType) }}</span>
                    </td>
                    <td class="font-bold">₹{{ tx.amount | number:'1.2-2' }}</td>
                    <td>{{ tx.paymentMode || 'N/A' }}</td>
                    <td class="font-medium text-dark">₹{{ tx.resultingBalance | number:'1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="myAdvanceTransactions.length === 0">
                    <td colspan="5" class="empty-cell">No advance transactions found for your account.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- My Bonuses -->
          <div class="section-card">
            <div class="section-header">
              <mat-icon class="section-icon">card_giftcard</mat-icon>
              <h3>My Bonus Records</h3>
            </div>

            <div class="table-container">
              <table class="custom-table">
                <thead>
                  <tr>
                    <th>Bonus Type</th>
                    <th>Payroll Month</th>
                    <th>Amount</th>
                    <th>Approved By</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let b of myBonuses">
                    <td><strong class="text-dark">{{ b.bonusType ? b.bonusType.replace('_', ' ') : '' }}</strong></td>
                    <td>{{ b.payrollMonth || 'N/A' }}</td>
                    <td class="text-emerald font-bold">₹{{ b.amount | number:'1.2-2' }}</td>
                    <td>{{ b.approvedBy || 'Admin' }}</td>
                  </tr>
                  <tr *ngIf="myBonuses.length === 0">
                    <td colspan="4" class="empty-cell">No bonus entries found.</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- My Payslips History List -->
        <div class="section-card margin-top">
          <div class="section-header">
            <mat-icon class="section-icon">history</mat-icon>
            <h3>My Payslip History</h3>
          </div>

          <div class="table-container">
            <table class="custom-table">
              <thead>
                <tr>
                  <th>Payslip No</th>
                  <th>Month</th>
                  <th>Gross Salary</th>
                  <th>Advance Deducted</th>
                  <th>Other Deductions</th>
                  <th>Net Salary</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let p of myPayslips">
                  <td><span class="payslip-code">{{ p.payslipNumber }}</span></td>
                  <td><strong>{{ p.payrollMonth }}</strong></td>
                  <td>₹{{ p.grossSalary | number:'1.2-2' }}</td>
                  <td class="text-red font-medium">₹{{ p.advanceDeductionAmount || 0 | number:'1.2-2' }}</td>
                  <td class="text-red">-₹{{ (p.totalDeductions || 0) - (p.advanceDeductionAmount || 0) | number:'1.2-2' }}</td>
                  <td><span class="net-salary-badge">₹{{ p.netSalary | number:'1.2-2' }}</span></td>
                  <td>
                    <button mat-stroked-button class="btn-download" (click)="downloadPdf(p.employeeId, p.payrollMonth)">
                      <mat-icon>picture_as_pdf</mat-icon> Download PDF
                    </button>
                  </td>
                </tr>
                <tr *ngIf="myPayslips.length === 0">
                  <td colspan="7" class="empty-cell">No payslips found for your account.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- TAB 4: REPORTS & STATEMENT EXPORTS -->
      <div *ngIf="activeTab === 'REPORTS'">
        <div class="section-card">
          <div class="section-header">
            <mat-icon class="section-icon">download_for_offline</mat-icon>
            <h3>Payroll & Advance Statement Exports</h3>
          </div>

          <div class="reports-grid">
            <div class="report-box">
              <mat-icon class="box-icon">description</mat-icon>
              <h4>Monthly Payroll Summary</h4>
              <p>Export complete employee earnings, deductions, bonuses, LOP, and net salary payouts for {{ payrollMonth }}.</p>
              <div class="box-actions">
                <button mat-flat-button class="btn-export-excel" (click)="exportPayrollExcel()">
                  <mat-icon>table_view</mat-icon> Excel
                </button>
                <button mat-flat-button class="btn-export-pdf" (click)="exportPayrollPdf()">
                  <mat-icon>picture_as_pdf</mat-icon> PDF
                </button>
              </div>
            </div>

            <div class="report-box">
              <mat-icon class="box-icon">account_balance_wallet</mat-icon>
              <h4>Salary Advances & Recovery Log</h4>
              <p>Export all employee active advance accounts, total granted amounts, direct repayments, and outstanding balances.</p>
              <div class="box-actions">
                <button mat-flat-button class="btn-export-excel" (click)="exportAdvanceExcel()">
                  <mat-icon>table_view</mat-icon> Excel
                </button>
                <button mat-flat-button class="btn-export-pdf" (click)="exportAdvancePdf()">
                  <mat-icon>picture_as_pdf</mat-icon> PDF
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ACTIVE SALARY ADVANCE POPUP MODAL (INDIVIDUAL GENERATION FLOW) -->
      <div class="modal-overlay" *ngIf="showActiveAdvancePopup" (click)="showActiveAdvancePopup = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 style="color: #D97706; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #D97706;">warning</mat-icon> Active Salary Advance Found
            </h3>
            <button mat-icon-button (click)="showActiveAdvancePopup = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Employee <strong>{{ selectedEmpForGeneration?.employeeName }}</strong> has an outstanding salary advance of <strong class="text-red font-bold">₹{{ popupOutstandingAdvance | number:'1.2-2' }}</strong>.</p>
            <p style="font-size: 13px; color: #475569; margin-top: 8px;">Do you want to deduct an amount from this month's salary towards advance recovery?</p>

            <div *ngIf="chooseDeductOption" class="deduction-input-box margin-top">
              <div class="field-group">
                <label class="font-bold">Advance Deduction Amount (₹):</label>
                <input type="number" [(ngModel)]="popupDeductionAmount" (ngModelChange)="updatePopupRemainingPreview()" class="drawer-input deduction-highlight">
                <div *ngIf="popupDeductionError" class="error-msg">
                  <mat-icon>error</mat-icon> {{ popupDeductionError }}
                </div>
              </div>
              <div class="remaining-preview-badge" style="margin-top: 8px;">
                Remaining Advance After Deduction: <strong>₹{{ popupRemainingBalancePreview | number:'1.2-2' }}</strong>
              </div>
            </div>
          </div>
          <div class="modal-actions" style="flex-wrap: wrap; justify-content: space-between;">
            <button mat-button (click)="showActiveAdvancePopup = false">Cancel</button>
            <div style="display: flex; gap: 8px;">
              <button *ngIf="!chooseDeductOption" mat-stroked-button (click)="generatePayslipNoDeduction()">
                No, Generate Payslip
              </button>
              <button *ngIf="!chooseDeductOption" mat-flat-button class="btn-primary-amber" (click)="chooseDeductOption = true">
                Yes, Deduct Advance
              </button>
              <button *ngIf="chooseDeductOption" mat-flat-button class="btn-primary-emerald" (click)="confirmGenerateWithDeduction()" [disabled]="!!popupDeductionError">
                Generate Payslip
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- DUPLICATE PAYSLIP CONFIRMATION MODAL -->
      <div class="modal-overlay" *ngIf="showDuplicatePayslipModal" (click)="showDuplicatePayslipModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-amber">
            <h3 style="color: #D97706; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #D97706;">copy_all</mat-icon> Duplicate Payslip Detected
            </h3>
            <button mat-icon-button (click)="showDuplicatePayslipModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>A payslip already exists for <strong>{{ existingDuplicateRecord?.employeeName }}</strong> for month <strong>{{ payrollMonth }}</strong> (Payslip No: <code>{{ existingDuplicateRecord?.payslipNumber }}</code>).</p>
            <p style="font-size: 12px; color: #64748B; margin-top: 8px;">Regenerating will replace the existing record and update any advance deduction ledger entries.</p>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showDuplicatePayslipModal = false">Cancel</button>
            <button mat-stroked-button (click)="viewExistingPayslip()">View Existing</button>
            <button mat-flat-button class="btn-primary-emerald" (click)="proceedRegenerateDuplicate()">Regenerate / Replace</button>
          </div>
        </div>
      </div>

      <!-- ADD SALARY ADVANCE MODAL (ADMIN) -->
      <div class="modal-overlay" *ngIf="showAddAdvanceModal" (click)="showAddAdvanceModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Add Salary Advance — {{ targetEmpNameForAdvance }}</h3>
            <button mat-icon-button (click)="showAddAdvanceModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <div class="field-group">
              <label>Advance Amount (₹)</label>
              <input type="number" [(ngModel)]="advanceForm.amount" class="drawer-input" placeholder="e.g. 20000">
            </div>
            <div class="field-group margin-top-sm">
              <label>Advance Date</label>
              <input type="date" [(ngModel)]="advanceForm.transactionDate" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Payment Mode</label>
              <select [(ngModel)]="advanceForm.paymentMode" class="drawer-input">
                <option value="Cash">Cash</option>
                <option value="GPAY">GPAY</option>
                <option value="PhonePay">PhonePay</option>
                <option value="Bank Transfer">Bank Transfer</option>
                <option value="Other">Other</option>
              </select>
            </div>
            <div *ngIf="advanceForm.paymentMode === 'Other'" class="field-group margin-top-sm">
              <label>Specify Other Payment Method / Details</label>
              <input type="text" [(ngModel)]="advanceForm.otherPaymentModeDetails" class="drawer-input" placeholder="Specify details...">
            </div>
            <div class="field-group margin-top-sm">
              <label>Reference / Transaction Number</label>
              <input type="text" [(ngModel)]="advanceForm.referenceNumber" class="drawer-input" placeholder="e.g. TXN12345">
            </div>
            <div class="field-group margin-top-sm">
              <label>Reason / Description</label>
              <input type="text" [(ngModel)]="advanceForm.reason" class="drawer-input" placeholder="e.g. Emergency personal expense">
            </div>
            <div class="field-group margin-top-sm">
              <label>Notes</label>
              <input type="text" [(ngModel)]="advanceForm.notes" class="drawer-input" placeholder="Additional notes...">
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showAddAdvanceModal = false">Cancel</button>
            <button mat-flat-button class="btn-primary-amber" (click)="submitAddAdvance()">Save Advance Entry</button>
          </div>
        </div>
      </div>

      <!-- RECORD REPAYMENT MODAL (ADMIN) -->
      <div class="modal-overlay" *ngIf="showRepaymentModal" (click)="showRepaymentModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Record Advance Repayment — {{ targetEmpNameForAdvance }}</h3>
            <button mat-icon-button (click)="showRepaymentModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <div class="current-outstanding-notice">
              Current Outstanding Balance: <strong class="text-red font-bold">₹{{ activeAdvanceAccount?.outstandingBalance || 0 | number:'1.2-2' }}</strong>
            </div>

            <div class="field-group margin-top-sm">
              <label>Repayment Amount (₹)</label>
              <input type="number" [(ngModel)]="repaymentForm.amount" (ngModelChange)="validateRepaymentAmount()" class="drawer-input">
              <div *ngIf="repaymentValidationError" class="error-msg">
                <mat-icon>error</mat-icon> {{ repaymentValidationError }}
              </div>
            </div>
            <div class="field-group margin-top-sm">
              <label>Repayment Date</label>
              <input type="date" [(ngModel)]="repaymentForm.transactionDate" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Payment Mode</label>
              <select [(ngModel)]="repaymentForm.paymentMode" class="drawer-input">
                <option value="Bank Transfer">Bank Transfer</option>
                <option value="GPAY">GPAY</option>
                <option value="PhonePay">PhonePay</option>
                <option value="Cash">Cash</option>
                <option value="Other">Other</option>
              </select>
            </div>
            <div class="field-group margin-top-sm">
              <label>Reference Number</label>
              <input type="text" [(ngModel)]="repaymentForm.referenceNumber" class="drawer-input" placeholder="e.g. ABC12345">
            </div>
            <div class="field-group margin-top-sm">
              <label>Notes / Remarks</label>
              <input type="text" [(ngModel)]="repaymentForm.notes" class="drawer-input" placeholder="e.g. Partial repayment">
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showRepaymentModal = false">Cancel</button>
            <button mat-flat-button class="btn-primary-blue" (click)="submitRepayment()" [disabled]="!!repaymentValidationError">Save Repayment Entry</button>
          </div>
        </div>
      </div>

      <!-- EDIT TRANSACTION MODAL -->
      <div class="modal-overlay" *ngIf="showEditTransactionModal" (click)="showEditTransactionModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3>Edit Advance Transaction</h3>
            <button mat-icon-button (click)="showEditTransactionModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body" *ngIf="editingTransaction">
            <div class="field-group">
              <label>Amount (₹)</label>
              <input type="number" [(ngModel)]="editingTransaction.amount" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Transaction Date</label>
              <input type="date" [(ngModel)]="editingTransaction.transactionDate" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Payment Mode</label>
              <select [(ngModel)]="editingTransaction.paymentMode" class="drawer-input">
                <option value="Cash">Cash</option>
                <option value="GPAY">GPAY</option>
                <option value="PhonePay">PhonePay</option>
                <option value="Bank Transfer">Bank Transfer</option>
                <option value="Payroll Deduction">Payroll Deduction</option>
                <option value="Other">Other</option>
              </select>
            </div>
            <div class="field-group margin-top-sm">
              <label>Reference Number</label>
              <input type="text" [(ngModel)]="editingTransaction.referenceNumber" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Reason / Description</label>
              <input type="text" [(ngModel)]="editingTransaction.reason" class="drawer-input">
            </div>
            <div class="field-group margin-top-sm">
              <label>Notes</label>
              <input type="text" [(ngModel)]="editingTransaction.notes" class="drawer-input">
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showEditTransactionModal = false">Cancel</button>
            <button mat-flat-button class="btn-primary-emerald" (click)="submitEditTransaction()">Update Entry</button>
          </div>
        </div>
      </div>

      <!-- DELETE TRANSACTION CONFIRMATION MODAL -->
      <div class="modal-overlay" *ngIf="showDeleteTransactionModal" (click)="showDeleteTransactionModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <h3 style="color: #EF4444; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #EF4444;">warning</mat-icon> Confirm Transaction Void / Delete
            </h3>
            <button mat-icon-button (click)="showDeleteTransactionModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to void the <strong>{{ transactionToDelete?.transactionType }}</strong> transaction of <strong class="text-red">₹{{ transactionToDelete?.amount | number:'1.2-2' }}</strong>?</p>
            <p style="font-size: 12px; color: #64748B; margin-top: 8px;">Voiding will recalculate the employee's advance ledger totals and outstanding balance immediately.</p>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showDeleteTransactionModal = false">Cancel</button>
            <button mat-flat-button class="btn-delete-confirm" (click)="confirmDeleteTransaction()">Confirm Void/Delete</button>
          </div>
        </div>
      </div>

      <!-- DELETE PAYSLIP CONFIRMATION MODAL -->
      <div class="modal-overlay" *ngIf="showDeleteModal" (click)="showDeleteModal = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header text-red">
            <h3 style="color: #EF4444; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #EF4444;">warning</mat-icon> Confirm Payslip Deletion
            </h3>
            <button mat-icon-button (click)="showDeleteModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p>Are you sure you want to delete the payslip for <strong>{{ selectedPayslipToDelete?.employeeName }}</strong> (Month: {{ selectedPayslipToDelete?.payrollMonth }})?</p>
            <p style="font-size: 12px; color: #64748B; margin-top: 8px;">If this payslip included a salary advance deduction, the deduction transaction will be voided and restored to the advance account balance.</p>
            <div class="field-group margin-top-sm" style="margin-top: 12px;">
              <label style="font-size: 13px; font-weight: 600; color: #475569;">Reason for Deletion (Optional):</label>
              <input type="text" [(ngModel)]="deleteReason" placeholder="e.g. Incorrect calculation / Re-generation" class="drawer-input">
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showDeleteModal = false">Cancel</button>
            <button mat-flat-button class="btn-delete-confirm" (click)="confirmDeletePayslip()">Delete Payslip</button>
          </div>
        </div>
      </div>

      <!-- BATCH PROCESS MONTHLY PAYROLL MODAL -->
      <div class="modal-overlay" *ngIf="showBatchProcessModal" (click)="showBatchProcessModal = false">
        <div class="modal-card" style="max-width: 720px;" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h3 style="color: #047857; display: flex; align-items: center; gap: 8px;">
              <mat-icon style="color: #047857;">engineering</mat-icon> Bulk Payroll & Advance Deduction Review — {{ payrollMonth }}
            </h3>
            <button mat-icon-button (click)="showBatchProcessModal = false"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <p style="margin: 0 0 12px; font-size: 13px; color: #475569;">
              Review employee outstanding advance balances and adjust advance deductions for month <strong>{{ payrollMonth }}</strong> before bulk payslip generation.
            </p>

            <div class="table-container" style="max-height: 320px; overflow-y: auto;">
              <table class="mini-table">
                <thead>
                  <tr>
                    <th>Employee</th>
                    <th>Gross Salary</th>
                    <th>LOP Days</th>
                    <th>Outstanding Advance</th>
                    <th>Month Deduction (₹)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of batchEmployeeList">
                    <td>
                      <strong>{{ item.employeeName }}</strong>
                      <div style="font-size: 11px; color: #64748B;">{{ item.employeeCode }}</div>
                    </td>
                    <td>₹{{ item.grossSalary | number:'1.2-2' }}</td>
                    <td>{{ item.lopDays }}d</td>
                    <td>
                      <span [class.text-red]="item.outstandingBalance > 0" class="font-bold">
                        ₹{{ item.outstandingBalance | number:'1.2-2' }}
                      </span>
                    </td>
                    <td>
                      <input type="number" [(ngModel)]="batchDeductionsMap[item.employeeId]"
                             [disabled]="item.outstandingBalance <= 0"
                             class="drawer-input" style="width: 120px; padding: 4px 8px; font-size: 12px;">
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-actions">
            <button mat-button (click)="showBatchProcessModal = false">Cancel</button>
            <button mat-flat-button class="btn-primary-emerald" (click)="confirmBatchProcessPayroll()">
              <mat-icon>engineering</mat-icon> Confirm & Process All Payslips
            </button>
          </div>
        </div>
      </div>

      <!-- EMPLOYEE PAYROLL MANAGEMENT DRAWER MODAL -->
      <div class="drawer-overlay" *ngIf="showDrawer" (click)="closeDrawer()"></div>
      <div class="drawer-panel" *ngIf="showDrawer" [class.open]="showDrawer">
        <div class="drawer-header">
          <div class="drawer-title">
            <mat-icon class="header-drawer-icon">edit_note</mat-icon>
            <div>
              <h3>Edit Payroll — {{ activeRecord?.employeeName }}</h3>
              <p class="drawer-sub">Code: {{ activeRecord?.employeeCode }} • Dept: {{ activeRecord?.department || 'General' }} • Month: {{ payrollMonth }}</p>
            </div>
          </div>
          <button mat-icon-button (click)="closeDrawer()" class="close-btn"><mat-icon>close</mat-icon></button>
        </div>

        <div class="drawer-body" *ngIf="activeRecord">
          <div *ngIf="activeRecord.isLocked" class="locked-banner">
            <mat-icon>lock</mat-icon>
            <span>This payroll is LOCKED by {{ activeRecord.lockedBy }} on {{ activeRecord.lockedAt | date:'medium' }}. Unlock to modify entries.</span>
          </div>

          <!-- Base Earnings (Configured in Salary Management) -->
          <div class="form-section">
            <div class="section-title">
              <span><mat-icon>payments</mat-icon> Base Salary & Earnings Breakdown</span>
              <span class="days-chip" style="font-size: 11px;">Configured in Salary Management</span>
            </div>
            <div class="form-grid-2">
              <div class="field-group">
                <label>Basic Salary (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.basicSalary" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>HRA (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.hra" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>Allowances (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.allowances" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>Other Earnings (₹) [Monthly Adjustment]</label>
                <input type="number" [(ngModel)]="activeRecord.otherEarnings" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
            </div>
          </div>

          <!-- Attendance & Overtime -->
          <div class="form-section">
            <div class="section-title"><mat-icon>punch_clock</mat-icon> Attendance & Overtime Auto-Summary</div>
            <div class="info-chips-row">
              <div class="chip-item">Working Days: <strong>{{ activeRecord.totalWorkingDays || 30 }}</strong></div>
              <div class="chip-item">Present: <strong class="text-emerald">{{ activeRecord.presentDays || 0 }}</strong></div>
              <div class="chip-item">Leave: <strong>{{ activeRecord.leaveDays || 0 }}</strong></div>
              <div class="chip-item">LOP Days: <strong class="text-red">{{ activeRecord.lopDays || 0 }}</strong></div>
            </div>
            <div class="form-grid-2 margin-top-sm">
              <div class="field-group">
                <label>Loss of Pay Amount (₹) [* Based strictly on Basic Salary]</label>
                <input type="number" [(ngModel)]="activeRecord.lopAmount" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
              <div class="field-group">
                <label>Overtime Amount (₹) [{{ activeRecord.overtimeHours || 0 }} hrs]</label>
                <input type="number" [(ngModel)]="activeRecord.overtimeAmount" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
            </div>
          </div>

          <!-- Bonuses -->
          <div class="form-section">
            <div class="section-title">
              <span><mat-icon>card_giftcard</mat-icon> Bonus Management</span>
              <button mat-button class="btn-text-emerald" (click)="toggleAddBonusForm()" [disabled]="!!activeRecord.isLocked">+ Add Bonus</button>
            </div>

            <div *ngIf="showAddBonus" class="inline-box">
              <div class="form-grid-2">
                <div class="field-group">
                  <label>Bonus Type</label>
                  <select [(ngModel)]="newBonus.bonusType" class="drawer-input">
                    <option value="PERFORMANCE_BONUS">Performance Bonus</option>
                    <option value="FESTIVAL_BONUS">Festival Bonus</option>
                    <option value="SPECIAL_BONUS">Special Bonus</option>
                    <option value="YEARLY_BONUS">Yearly Bonus</option>
                    <option value="INCENTIVE">Incentive</option>
                    <option value="OTHER_BONUS">Other Bonus</option>
                  </select>
                </div>
                <div class="field-group">
                  <label>Amount (₹)</label>
                  <input type="number" [(ngModel)]="newBonus.amount" class="drawer-input">
                </div>
              </div>
              <div class="field-group margin-top-sm">
                <label>Remarks</label>
                <input type="text" [(ngModel)]="newBonus.remarks" placeholder="Bonus justification..." class="drawer-input">
              </div>
              <div class="inline-actions">
                <button mat-flat-button class="btn-primary-emerald" (click)="submitBonus()">Save Bonus</button>
                <button mat-button (click)="showAddBonus = false">Cancel</button>
              </div>
            </div>

            <div class="form-grid-2">
              <div class="field-group">
                <label>Monthly Bonus (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.bonusAmount" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
              <div class="field-group">
                <label>Yearly Bonus (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.yearlyBonusAmount" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
            </div>
          </div>

          <!-- Deductions (Configured in Salary Management) -->
          <div class="form-section">
            <div class="section-title">
              <span><mat-icon>remove_circle_outline</mat-icon> Statutory Deductions</span>
              <span class="days-chip" style="font-size: 11px;">Configured in Salary Management</span>
            </div>
            <div class="form-grid-2">
              <div class="field-group">
                <label>Provident Fund / PF (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.pfAmount" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>Professional Tax (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.professionalTax" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>Income Tax (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.taxAmount" disabled class="drawer-input" style="background: #F1F5F9;">
              </div>
              <div class="field-group">
                <label>Other Deductions (₹) [Monthly Adjustment]</label>
                <input type="number" [(ngModel)]="activeRecord.otherDeductions" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input">
              </div>
            </div>
          </div>

          <!-- Salary Advance Deduction Section -->
          <div class="form-section">
            <div class="section-title">
              <span><mat-icon>account_balance_wallet</mat-icon> Salary Advance Recovery</span>
            </div>
            <div *ngIf="activeAdvanceAccount" class="advance-panel">
              <div class="adv-info-grid">
                <div>Total Issued: <strong>₹{{ activeAdvanceAccount.totalAdvanceGiven || 0 | number:'1.2-2' }}</strong></div>
                <div>Total Repaid: <strong class="text-blue">₹{{ (activeAdvanceAccount.totalRepaid || 0) + (activeAdvanceAccount.totalPayrollDeducted || 0) | number:'1.2-2' }}</strong></div>
                <div>Outstanding Balance: <strong class="text-red">₹{{ activeAdvanceAccount.outstandingBalance || 0 | number:'1.2-2' }}</strong></div>
              </div>

              <div class="field-group margin-top">
                <label class="font-bold">Current Month Advance Deduction Amount (₹)</label>
                <input type="number" [(ngModel)]="activeRecord.advanceDeductionAmount" (ngModelChange)="recalculateTotals()" [disabled]="!!activeRecord.isLocked" class="drawer-input deduction-highlight">
                <div *ngIf="advanceValidationError" class="error-msg">
                  <mat-icon>error</mat-icon> {{ advanceValidationError }}
                </div>
              </div>
            </div>
          </div>

          <!-- Summary Banner -->
          <div class="summary-banner">
            <div class="sum-col">
              <span class="sum-label">Gross Earnings</span>
              <span class="sum-val text-blue">₹{{ activeRecord.grossSalary | number:'1.2-2' }}</span>
            </div>
            <div class="sum-sign">-</div>
            <div class="sum-col">
              <span class="sum-label">Total Deductions</span>
              <span class="sum-val text-red">₹{{ activeRecord.totalDeductions | number:'1.2-2' }}</span>
            </div>
            <div class="sum-sign">=</div>
            <div class="sum-col highlight-net">
              <span class="sum-label">Net Payable Salary</span>
              <span class="sum-val text-emerald font-extrabold">₹{{ activeRecord.netSalary | number:'1.2-2' }}</span>
            </div>
          </div>

          <!-- Audit Logs -->
          <div *ngIf="activeRecord.auditLogs && activeRecord.auditLogs.length > 0" class="form-section">
            <div class="section-title"><mat-icon>history_edu</mat-icon> Payroll Audit History</div>
            <div class="audit-list">
              <div *ngFor="let log of activeRecord.auditLogs" class="audit-item">
                <div class="audit-head">
                  <span class="audit-action">{{ log.action }}</span>
                  <span class="audit-time">{{ log.timestamp | date:'short' }}</span>
                </div>
                <div class="audit-desc">{{ log.remarks }} (by {{ log.modifiedBy }})</div>
              </div>
            </div>
          </div>
        </div>

        <div class="drawer-footer" *ngIf="activeRecord">
          <button mat-stroked-button (click)="saveDraft()" [disabled]="!!activeRecord.isLocked || advanceValidationError !== ''">
            <mat-icon>save</mat-icon> Save Draft
          </button>
          <button mat-flat-button class="btn-primary-emerald" (click)="generatePayslipFromDrawer()" [disabled]="!!activeRecord.isLocked || advanceValidationError !== ''">
            <mat-icon>receipt_long</mat-icon> Generate Payslip
          </button>
          <button *ngIf="!activeRecord.isLocked" mat-stroked-button class="btn-lock" (click)="lockPayrollRecord()">
            <mat-icon>lock</mat-icon> Lock Payroll
          </button>
          <button *ngIf="activeRecord.isLocked" mat-stroked-button class="btn-unlock" (click)="unlockPayrollRecord()">
            <mat-icon>lock_open</mat-icon> Unlock Payroll
          </button>
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
      margin-bottom: 20px;
      padding: 20px 24px;
      background: #FFFFFF;
      border-radius: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border-left: 6px solid #047857;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: #D1FAE5;
      color: #047857;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }

    .header-actions { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .advance-header-actions { display: flex; gap: 8px; align-items: center; }
    .month-picker-wrapper { display: flex; align-items: center; background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 4px 10px; gap: 6px; }
    .calendar-icon { color: #64748B; font-size: 18px; width: 18px; height: 18px; }
    .month-picker { border: none; background: transparent; outline: none; font-size: 13px; font-weight: 600; color: #0F172A; }

    .btn-export-excel { background: #10B981 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-export-pdf { background: #EF4444 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-primary-emerald { background: #047857 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-primary-amber { background: #D97706 !important; color: white !important; font-weight: 600; border-radius: 8px; }
    .btn-primary-blue { background: #2563EB !important; color: white !important; font-weight: 600; border-radius: 8px; }

    /* Tabs Bar */
    .nav-tabs-bar { display: flex; gap: 8px; margin-bottom: 20px; border-bottom: 2px solid #E2E8F0; }
    .tab-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px;
      border: none;
      background: transparent;
      font-size: 14px;
      font-weight: 600;
      color: #64748B;
      cursor: pointer;
      border-bottom: 3px solid transparent;
      transition: all 0.2s;
    }
    .tab-btn:hover { color: #047857; }
    .tab-btn.active { color: #047857; border-bottom-color: #047857; background: #F0FDF4; border-radius: 8px 8px 0 0; }
    .tab-btn mat-icon { font-size: 20px; width: 20px; height: 20px; }

    /* KPI Grid */
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 20px; }
    .kpi-card { background: white; padding: 18px; border-radius: 12px; border: 1px solid #E2E8F0; box-shadow: 0 1px 4px rgba(0,0,0,0.02); }
    .kpi-card.highlight { background: #ECFDF5; border-color: #A7F3D0; }
    .kpi-title { font-size: 12px; font-weight: 700; color: #64748B; text-transform: uppercase; letter-spacing: 0.5px; }
    .kpi-value { font-size: 22px; font-weight: 800; color: #0F172A; margin: 6px 0 2px; }
    .kpi-sub { font-size: 11px; color: #94A3B8; }

    .text-blue { color: #2563EB; }
    .text-red { color: #DC2626; }
    .text-emerald { color: #047857; }
    .font-bold { font-weight: 700; }
    .font-extrabold { font-weight: 800; }
    .font-medium { font-weight: 600; }
    .text-dark { color: #0F172A; }
    .text-muted { color: #64748B; }

    /* Filter Bar */
    .filter-card { display: flex; gap: 16px; align-items: center; background: white; padding: 16px; border-radius: 12px; border: 1px solid #E2E8F0; margin-bottom: 20px; flex-wrap: wrap; }
    .search-input-wrapper { display: flex; align-items: center; background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 6px 12px; gap: 8px; flex: 1; min-width: 240px; }
    .search-input-wrapper mat-icon { color: #94A3B8; }
    .search-input { border: none; background: transparent; outline: none; width: 100%; font-size: 13.5px; }
    .filter-group { display: flex; align-items: center; gap: 8px; }
    .filter-group label { font-size: 13px; font-weight: 600; color: #475569; }
    .filter-select { background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 7px 12px; font-size: 13px; outline: none; }

    /* Tables & Cards */
    .section-card { background: white; padding: 22px; border-radius: 14px; border: 1px solid #E2E8F0; margin-bottom: 24px; box-shadow: 0 2px 10px rgba(0,0,0,0.03); }
    .section-header { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
    .section-icon { color: #047857; font-size: 22px; width: 22px; height: 22px; }
    .section-card h3 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0; }

    .table-container { width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .custom-table { width: 100%; border-collapse: separate; border-spacing: 0; text-align: left; }
    .custom-table.wide-table { min-width: 850px; }
    .custom-table th { background: #F8FAFC; color: #475569; font-weight: 700; font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.5px; padding: 12px 14px; border-bottom: 2px solid #E2E8F0; white-space: nowrap; }
    .custom-table td { padding: 12px 14px; font-size: 13px; border-bottom: 1px solid #F1F5F9; color: #334155; vertical-align: middle; white-space: nowrap; }
    .custom-table tr:hover { background-color: #F8FAFC; }

    .payslip-code { font-family: monospace; font-weight: 700; background: #F1F5F9; padding: 4px 8px; border-radius: 6px; color: #334155; font-size: 12px; white-space: nowrap; display: inline-block; }
    .emp-cell { display: inline-flex; align-items: center; gap: 10px; white-space: nowrap; }
    .emp-avatar { width: 32px; height: 32px; border-radius: 50%; background: #D1FAE5; color: #047857; font-weight: 700; font-size: 13px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .emp-name { font-weight: 600; color: #0F172A; white-space: nowrap; }
    .emp-code { font-size: 11px; color: #64748B; white-space: nowrap; }

    .working-cell { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
    .days-chip { background: #DCFCE7; color: #15803D; font-weight: 700; padding: 3px 8px; border-radius: 6px; font-size: 11px; white-space: nowrap; }
    .lop-chip { background: #FEE2E2; color: #B91C1C; font-weight: 700; padding: 3px 8px; border-radius: 6px; font-size: 11px; white-space: nowrap; }

    .net-salary-badge { background: #ECFDF5; color: #047857; font-weight: 800; padding: 4px 10px; border-radius: 8px; border: 1px solid #A7F3D0; font-size: 13.5px; white-space: nowrap; display: inline-block; }
    .status-badge { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 12px; font-size: 11px; font-weight: 700; text-transform: uppercase; white-space: nowrap; }
    .status-badge.draft { background: #F1F5F9; color: #475569; }
    .status-badge.generated, .status-badge.processed { background: #DBEAFE; color: #1D4ED8; }
    .status-badge.locked { background: #FEF3C7; color: #D97706; }
    .status-badge.advance_given { background: #FEF3C7; color: #B45309; }
    .status-badge.repayment { background: #DBEAFE; color: #1D4ED8; }
    .status-badge.payroll_deduction { background: #F3E8FF; color: #7E22CE; }
    .status-badge.adjustment { background: #FFEDD5; color: #C2410C; }

    .lock-icon { font-size: 14px; width: 14px; height: 14px; }

    .action-buttons { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; }
    .btn-generate-individual { background: #047857 !important; color: white !important; font-weight: 600; font-size: 12px !important; border-radius: 6px; height: 32px !important; line-height: 32px !important; padding: 0 10px !important; }
    .btn-manage { border-color: #047857 !important; color: #047857 !important; font-weight: 600; font-size: 12px !important; border-radius: 6px; height: 32px !important; line-height: 32px !important; padding: 0 10px !important; }
    .btn-download { border-color: #2563EB !important; color: #2563EB !important; font-weight: 600; font-size: 12px !important; border-radius: 6px; height: 32px !important; line-height: 32px !important; padding: 0 10px !important; }
    .btn-generate-individual mat-icon, .btn-manage mat-icon, .btn-download mat-icon { font-size: 16px !important; width: 16px !important; height: 16px !important; margin-right: 4px !important; }

    .empty-cell { text-align: center; padding: 36px !important; color: #64748B; }
    .empty-icon { font-size: 36px; width: 36px; height: 36px; color: #CBD5E1; margin-bottom: 6px; }

    /* Single Advance Account Summary Card */
    .advance-summary-card { display: flex; align-items: center; justify-content: space-around; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 12px; padding: 12px 16px; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
    .adv-card-col { display: flex; flex-direction: column; align-items: center; text-align: center; }
    .adv-card-label { font-size: 11px; font-weight: 700; color: #64748B; text-transform: uppercase; letter-spacing: 0.5px; }
    .adv-card-val { font-size: 18px; font-weight: 800; color: #0F172A; margin-top: 2px; }
    .adv-card-divider { width: 1px; height: 32px; background: #CBD5E1; }
    .highlight-outstanding { background: #FEF2F2; padding: 6px 14px; border-radius: 8px; border: 1px solid #FECACA; }

    .history-table-container { background: white; border: 1px solid #E2E8F0; border-radius: 12px; overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .history-table-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; background: #F8FAFC; border-bottom: 1px solid #E2E8F0; }
    .history-table-header h4 { margin: 0; font-size: 14px; font-weight: 700; color: #0F172A; }
    .badge-count { background: #FEF3C7; color: #B45309; font-size: 11px; font-weight: 800; padding: 2px 8px; border-radius: 12px; }

    /* Layout Grids */
    .grid-2-col { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 20px; }
    @media (max-width: 900px) { .grid-2-col { grid-template-columns: 1fr; } }
    .margin-top { margin-top: 24px; }
    .margin-top-sm { margin-top: 12px; }

    /* Reports Grid */
    .reports-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    .report-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 12px; padding: 20px; text-align: center; }
    .box-icon { font-size: 40px; width: 40px; height: 40px; color: #047857; margin-bottom: 8px; }
    .report-box h4 { font-size: 16px; font-weight: 700; color: #0F172A; margin: 0 0 6px; }
    .report-box p { font-size: 13px; color: #64748B; margin: 0 0 16px; }
    .box-actions { display: flex; justify-content: center; gap: 10px; }

    /* DRAWER OVERLAY & PANEL */
    .drawer-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15, 23, 42, 0.5); z-index: 1000; }
    .drawer-panel {
      position: fixed;
      top: 0; right: 0; bottom: 0;
      width: 640px;
      max-width: 90vw;
      background: white;
      z-index: 1001;
      box-shadow: -4px 0 20px rgba(0,0,0,0.15);
      display: flex;
      flex-direction: column;
    }
    .drawer-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; border-bottom: 1px solid #E2E8F0; background: #F8FAFC; }
    .drawer-title { display: flex; align-items: center; gap: 12px; }
    .header-drawer-icon { color: #047857; font-size: 28px; width: 28px; height: 28px; }
    .drawer-header h3 { font-size: 17px; font-weight: 700; margin: 0; color: #0F172A; }
    .drawer-sub { font-size: 12px; color: #64748B; margin: 2px 0 0; }
    .close-btn { color: #64748B; }

    .drawer-body { flex: 1; overflow-y: auto; padding: 24px; }
    .locked-banner { background: #FEF3C7; border: 1px solid #F59E0B; border-radius: 8px; padding: 12px 16px; display: flex; align-items: center; gap: 10px; color: #92400E; font-size: 13px; font-weight: 600; margin-bottom: 20px; }

    .form-section { background: white; border: 1px solid #E2E8F0; border-radius: 12px; padding: 18px; margin-bottom: 20px; }
    .section-title { font-size: 14px; font-weight: 700; color: #0F172A; display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; padding-bottom: 8px; border-bottom: 1px solid #F1F5F9; }
    .section-title mat-icon { font-size: 18px; width: 18px; height: 18px; color: #047857; vertical-align: middle; margin-right: 4px; }

    .form-grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
    .field-group { display: flex; flex-direction: column; gap: 4px; }
    .field-group label { font-size: 12px; font-weight: 600; color: #475569; }
    .drawer-input { background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 6px; padding: 8px 12px; font-size: 13.5px; outline: none; font-weight: 600; color: #0F172A; }
    .drawer-input:focus { border-color: #047857; background: white; }
    .deduction-highlight { border-color: #EF4444; background: #FEF2F2; }

    .info-chips-row { display: flex; gap: 12px; flex-wrap: wrap; background: #F8FAFC; padding: 10px; border-radius: 8px; font-size: 12px; }
    .chip-item { background: white; border: 1px solid #E2E8F0; padding: 4px 10px; border-radius: 6px; }

    .btn-text-emerald { color: #047857 !important; font-weight: 700 !important; font-size: 12px !important; }
    .inline-box { background: #F0FDF4; border: 1px solid #A7F3D0; border-radius: 8px; padding: 14px; margin-bottom: 14px; }
    .inline-actions { display: flex; gap: 10px; margin-top: 12px; }

    .advance-panel { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 14px; }
    .adv-info-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 8px; font-size: 12.5px; color: #475569; }
    .error-msg { color: #DC2626; font-size: 12px; font-weight: 600; display: flex; align-items: center; gap: 4px; margin-top: 4px; }
    .error-msg mat-icon { font-size: 14px; width: 14px; height: 14px; }

    .summary-banner { display: flex; align-items: center; justify-content: space-between; background: #0F172A; color: white; padding: 18px; border-radius: 12px; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .sum-col { display: flex; flex-direction: column; }
    .sum-label { font-size: 11px; text-transform: uppercase; color: #94A3B8; font-weight: 700; letter-spacing: 0.5px; }
    .sum-val { font-size: 18px; font-weight: 800; }
    .sum-sign { font-size: 20px; font-weight: 700; color: #64748B; }
    .highlight-net { background: rgba(4, 120, 87, 0.3); padding: 8px 16px; border-radius: 8px; border: 1px solid #047857; }

    .audit-list { display: flex; flex-direction: column; gap: 8px; max-height: 160px; overflow-y: auto; }
    .audit-item { background: #F8FAFC; border: 1px solid #E2E8F0; padding: 8px 12px; border-radius: 6px; font-size: 12px; }
    .audit-head { display: flex; justify-content: space-between; font-weight: 700; color: #0F172A; margin-bottom: 2px; }
    .audit-action { color: #047857; }
    .audit-time { color: #94A3B8; font-size: 11px; }
    .audit-desc { color: #475569; }

    .drawer-footer { padding: 16px 24px; border-top: 1px solid #E2E8F0; background: #F8FAFC; display: flex; gap: 10px; flex-wrap: wrap; }
    .btn-lock { border-color: #F59E0B !important; color: #D97706 !important; }
    .btn-unlock { border-color: #10B981 !important; color: #059669 !important; }
    .btn-delete { background: #FEF2F2 !important; color: #DC2626 !important; border-color: #FCA5A5 !important; font-weight: 600; border-radius: 8px; }

    .deduction-input-box { background: #FEF3C7; border: 1px solid #F59E0B; border-radius: 8px; padding: 14px; }
    .remaining-preview-badge { font-size: 12.5px; color: #92400E; background: white; padding: 6px 12px; border-radius: 6px; border: 1px solid #FCD34D; }

    .current-outstanding-notice { background: #FEF2F2; border: 1px solid #FECACA; padding: 10px 14px; border-radius: 8px; font-size: 13px; color: #991B1B; margin-bottom: 12px; }

    /* MODAL OVERLAY & POPUP CARD */
    .modal-overlay {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(15, 23, 42, 0.6);
      backdrop-filter: blur(2px);
      z-index: 2000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    .modal-card {
      background: white;
      border-radius: 14px;
      width: 100%;
      max-width: 520px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
      overflow: hidden;
      border: 1px solid #E2E8F0;
      animation: modalPopIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) both;
    }
    @keyframes modalPopIn {
      from { opacity: 0; transform: scale(0.95) translateY(10px); }
      to { opacity: 1; transform: scale(1) translateY(0); }
    }
    .modal-header {
      padding: 16px 20px;
      background: #F8FAFC;
      border-bottom: 1px solid #E2E8F0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .modal-header h3 { margin: 0; font-size: 16px; font-weight: 700; color: #0F172A; }
    .modal-body { padding: 20px; }
    .modal-actions {
      padding: 16px 20px;
      background: #F8FAFC;
      border-top: 1px solid #E2E8F0;
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }
    .btn-delete-confirm {
      background: #EF4444 !important;
      color: white !important;
      font-weight: 600 !important;
      border-radius: 8px !important;
    }
  `]
})
export class PayrollManagementComponent implements OnInit {
  activeTab: 'EMPLOYEE_PAYROLL' | 'SALARY_MANAGEMENT' | 'SALARY_ADVANCES' | 'MY_PAYSLIPS' | 'REPORTS' = 'EMPLOYEE_PAYROLL';
  payrollMonth: string = new Date().toISOString().substring(0, 7);
  isAdmin = false;
  selectedEmployeeId: string = '';
  selectedEmployeeName: string = '';

  selectedSalaryStructEmployeeId: string = '';
  currentSalaryStruct: SalaryStructure = {
    employeeId: '',
    basicSalary: 30000,
    hra: 10000,
    allowances: 5000,
    pfDeduction: 1800,
    professionalTax: 200,
    incomeTax: 0
  };

  payrolls: PayrollRecord[] = [];
  filteredPayrolls: PayrollRecord[] = [];
  myPayslips: PayrollRecord[] = [];
  myAdvanceAccount: EmployeeAdvanceAccount | null = null;
  myAdvanceTransactions: AdvanceTransaction[] = [];
  myBonuses: EmployeeBonus[] = [];

  activeAdvanceAccount: EmployeeAdvanceAccount | null = null;
  activeAdvanceTransactions: AdvanceTransaction[] = [];

  searchQuery = '';
  selectedDept = 'ALL';
  selectedStatus = 'ALL';
  departments: string[] = [];

  kpis = {
    totalEmployees: 0,
    totalGross: 0,
    totalDeductions: 0,
    totalNet: 0,
    processedCount: 0
  };

  // Drawer / Manage Modal
  showDrawer = false;
  activeRecord: PayrollRecord | null = null;
  advanceValidationError = '';

  // Active Advance Popup (Individual Generation)
  showActiveAdvancePopup = false;
  selectedEmpForGeneration: PayrollRecord | null = null;
  popupOutstandingAdvance = 0;
  chooseDeductOption = false;
  popupDeductionAmount = 0;
  popupRemainingBalancePreview = 0;
  popupDeductionError = '';

  // Duplicate Payslip Modal
  showDuplicatePayslipModal = false;
  existingDuplicateRecord: PayrollRecord | null = null;
  pendingDuplicateEmpRecord: PayrollRecord | null = null;

  // Add Advance Modal (Admin)
  showAddAdvanceModal = false;
  targetEmpNameForAdvance = '';
  advanceForm: Partial<AdvanceTransaction> = {
    amount: 0,
    transactionDate: new Date().toISOString().substring(0, 10),
    paymentMode: 'Cash',
    otherPaymentModeDetails: '',
    referenceNumber: '',
    reason: '',
    notes: ''
  };

  // Record Repayment Modal (Admin)
  showRepaymentModal = false;
  repaymentForm: Partial<AdvanceTransaction> = {
    amount: 0,
    transactionDate: new Date().toISOString().substring(0, 10),
    paymentMode: 'Bank Transfer',
    referenceNumber: '',
    notes: ''
  };
  repaymentValidationError = '';

  // Edit Transaction Modal
  showEditTransactionModal = false;
  editingTransaction: AdvanceTransaction | null = null;

  // Delete Transaction Modal
  showDeleteTransactionModal = false;
  transactionToDelete: AdvanceTransaction | null = null;

  // Delete Payslip Modal
  showDeleteModal = false;
  selectedPayslipToDelete: PayrollRecord | null = null;
  deleteReason = '';

  // Batch Process Modal
  showBatchProcessModal = false;
  batchEmployeeList: Array<{
    employeeId: string;
    employeeCode: string;
    employeeName: string;
    grossSalary: number;
    lopDays: number;
    outstandingBalance: number;
  }> = [];
  batchDeductionsMap: { [employeeId: string]: number } = {};

  // Inline Bonus form
  showAddBonus = false;
  newBonus: Partial<EmployeeBonus> = {
    bonusType: 'PERFORMANCE_BONUS',
    amount: 0,
    remarks: ''
  };

  constructor(
    private route: ActivatedRoute,
    private attendanceService: AttendanceService,
    private permissionService: PermissionService,
    private apiService: ApiService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.isAdmin = this.permissionService.isAdmin();
    if (!this.isAdmin) {
      this.activeTab = 'MY_PAYSLIPS';
    }

    this.route.data.subscribe(data => {
      if (data && data['tab']) {
        this.activeTab = data['tab'];
      }
    });

    this.loadData();
    this.loadMyData();
  }

  formatTxType(type?: string): string {
    if (!type) return '—';
    switch (type) {
      case 'ADVANCE_GIVEN': return 'ADVANCE GIVEN';
      case 'REPAYMENT': return 'REPAYMENT';
      case 'PAYROLL_DEDUCTION': return 'PAYROLL DEDUCTION';
      case 'ADJUSTMENT': return 'ADJUSTMENT';
      default: return type.replace('_', ' ');
    }
  }

  onEmployeeFilterChange(empId: string): void {
    this.selectedEmployeeId = empId || '';
    if (!empId) {
      this.selectedEmployeeName = 'All Employees';
    }
    this.loadData();
  }

  onSalaryStructEmployeeChange(empId: string): void {
    this.selectedSalaryStructEmployeeId = empId || '';
    if (this.selectedSalaryStructEmployeeId) {
      this.attendanceService.getSalaryStructure(this.selectedSalaryStructEmployeeId).subscribe({
        next: (struct) => {
          this.currentSalaryStruct = struct || {
            employeeId: empId,
            basicSalary: 30000,
            hra: 10000,
            allowances: 5000,
            pfDeduction: 1800,
            professionalTax: 200,
            incomeTax: 0
          };
        },
        error: () => {
          this.currentSalaryStruct = {
            employeeId: empId,
            basicSalary: 30000,
            hra: 10000,
            allowances: 5000,
            pfDeduction: 1800,
            professionalTax: 200,
            incomeTax: 0
          };
        }
      });
    }
  }

  saveSalaryStructure(): void {
    if (!this.selectedSalaryStructEmployeeId || !this.currentSalaryStruct) {
      this.toastService.error('Error', 'Please select an employee first.');
      return;
    }
    this.currentSalaryStruct.employeeId = this.selectedSalaryStructEmployeeId;
    this.attendanceService.saveSalaryStructure(this.currentSalaryStruct).subscribe({
      next: (saved) => {
        this.currentSalaryStruct = saved;
        this.toastService.success('Salary Saved', 'Employee base salary configuration saved successfully!');
        this.loadData();
      },
      error: (err) => this.toastService.error('Save Failed', err?.error?.message || 'Failed to save salary configuration')
    });
  }

  loadData(): void {
    if (this.isAdmin) {
      this.attendanceService.getPayslipsByMonth(this.payrollMonth, this.selectedEmployeeId).subscribe({
        next: (data) => {
          this.payrolls = data || [];
          this.extractDepartments();
          this.applyFilters();
          this.calculateKpis();
        },
        error: (err) => console.error('Failed to load payslips', err)
      });

      if (this.selectedEmployeeId) {
        this.loadAdvanceAccountForEmployee(this.selectedEmployeeId);
      } else {
        this.loadAllAdvanceAccounts();
      }
    }
  }

  loadAllAdvanceAccounts(): void {
    this.selectedEmployeeName = 'All Employees';
    this.attendanceService.getAllAdvanceAccounts().subscribe({
      next: (accounts) => {
        const list = accounts || [];
        let totalGiven = 0;
        let totalRepaid = 0;
        let totalPayroll = 0;
        let totalBal = 0;
        list.forEach(a => {
          totalGiven += (a.totalAdvanceGiven || 0);
          totalRepaid += (a.totalRepaid || 0);
          totalPayroll += (a.totalPayrollDeducted || 0);
          totalBal += (a.outstandingBalance || 0);
        });
        this.activeAdvanceAccount = {
          employeeName: 'All Employees',
          totalAdvanceGiven: totalGiven,
          totalRepaid: totalRepaid,
          totalPayrollDeducted: totalPayroll,
          outstandingBalance: totalBal
        } as any;

        const allTxPromises = list.map(a => this.attendanceService.getAdvanceTransactions(a.employeeId!).toPromise());
        Promise.all(allTxPromises).then(results => {
          let combined: AdvanceTransaction[] = [];
          results.forEach(res => {
            if (res) combined = combined.concat(res);
          });
          combined.sort((a, b) => (b.transactionDate || '').localeCompare(a.transactionDate || ''));
          this.activeAdvanceTransactions = combined;
        }).catch(() => {
          this.activeAdvanceTransactions = [];
        });
      },
      error: () => {
        this.activeAdvanceAccount = null;
        this.activeAdvanceTransactions = [];
      }
    });
  }

  loadAdvanceAccountForEmployee(empId: string): void {
    this.attendanceService.getEmployeeAdvanceAccount(empId).subscribe({
      next: (acc) => {
        this.activeAdvanceAccount = acc;
        if (acc) this.selectedEmployeeName = acc.employeeName || '';
      },
      error: () => this.activeAdvanceAccount = null
    });

    this.attendanceService.getAdvanceTransactions(empId).subscribe({
      next: (txs) => this.activeAdvanceTransactions = txs || [],
      error: () => this.activeAdvanceTransactions = []
    });
  }

  loadMyData(): void {
    this.attendanceService.getMyPayslips().subscribe({
      next: (data) => this.myPayslips = data || [],
      error: (err) => console.error(err)
    });

    const currentEmp = this.permissionService.getCurrentUser();
    if (currentEmp?.id || currentEmp?.employeeId) {
      const empId = currentEmp.id || currentEmp.employeeId;
      this.attendanceService.getEmployeeAdvanceAccount(empId).subscribe({
        next: (acc) => this.myAdvanceAccount = acc,
        error: (err) => console.error(err)
      });

      this.attendanceService.getAdvanceTransactions(empId).subscribe({
        next: (txs) => this.myAdvanceTransactions = txs || [],
        error: (err) => console.error(err)
      });
    }

    this.attendanceService.getMyBonuses().subscribe({
      next: (bonuses) => this.myBonuses = bonuses || [],
      error: (err) => console.error(err)
    });
  }

  extractDepartments(): void {
    const depts = new Set<string>();
    this.payrolls.forEach(p => {
      if (p.department) depts.add(p.department);
    });
    this.departments = Array.from(depts);
  }

  applyFilters(): void {
    this.filteredPayrolls = this.payrolls.filter(p => {
      const matchesSearch = !this.searchQuery ||
        p.employeeName?.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        p.employeeCode?.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesDept = this.selectedDept === 'ALL' || p.department === this.selectedDept;

      const matchesStatus = this.selectedStatus === 'ALL' ||
        (this.selectedStatus === 'LOCKED' && p.isLocked) ||
        (!p.isLocked && p.status === this.selectedStatus);

      return matchesSearch && matchesDept && matchesStatus;
    });
  }

  calculateKpis(): void {
    this.kpis.totalEmployees = this.payrolls.length;
    this.kpis.totalGross = this.payrolls.reduce((sum, p) => sum + (p.grossSalary || 0), 0);
    this.kpis.totalDeductions = this.payrolls.reduce((sum, p) => sum + (p.totalDeductions || 0), 0);
    this.kpis.totalNet = this.payrolls.reduce((sum, p) => sum + (p.netSalary || 0), 0);
    this.kpis.processedCount = this.payrolls.filter(p => p.status === 'GENERATED' || p.status === 'PROCESSED' || p.isLocked).length;
  }

  // --- INDIVIDUAL PAYSLIP GENERATION FLOW WITH ACTIVE ADVANCE DETECTION ---
  generateSelectedEmployeePayslip(): void {
    if (!this.selectedEmployeeId) {
      this.toastService.warning('Select Employee', 'Please select an employee from the dropdown first.');
      return;
    }
    const found = this.payrolls.find(p => p.employeeId === this.selectedEmployeeId);
    if (found) {
      this.initiateIndividualGenerate(found);
    } else {
      const dummyRecord: any = { employeeId: this.selectedEmployeeId, payrollMonth: this.payrollMonth };
      this.checkActiveAdvanceAndProceed(dummyRecord);
    }
  }

  initiateIndividualGenerate(p: PayrollRecord): void {
    this.selectedEmpForGeneration = p;

    // Check if duplicate payslip already generated
    if (p.status === 'GENERATED' || p.status === 'PROCESSED') {
      this.existingDuplicateRecord = p;
      this.showDuplicatePayslipModal = true;
      return;
    }

    this.checkActiveAdvanceAndProceed(p);
  }

  viewExistingPayslip(): void {
    this.showDuplicatePayslipModal = false;
    if (this.existingDuplicateRecord) {
      this.openDrawer(this.existingDuplicateRecord);
    }
  }

  proceedRegenerateDuplicate(): void {
    this.showDuplicatePayslipModal = false;
    if (this.existingDuplicateRecord) {
      this.checkActiveAdvanceAndProceed(this.existingDuplicateRecord);
    }
  }

  checkActiveAdvanceAndProceed(p: PayrollRecord): void {
    this.selectedEmpForGeneration = p;
    this.attendanceService.checkOutstandingAdvance(p.employeeId).subscribe({
      next: (balance) => {
        const outstanding = balance || 0;
        if (outstanding > 0) {
          this.popupOutstandingAdvance = outstanding;
          this.popupDeductionAmount = outstanding;
          this.chooseDeductOption = false;
          this.popupDeductionError = '';
          this.updatePopupRemainingPreview();
          this.showActiveAdvancePopup = true;
        } else {
          // No active advance, generate directly
          this.executeGeneratePayslip(p.employeeId, undefined);
        }
      },
      error: () => this.executeGeneratePayslip(p.employeeId, undefined)
    });
  }

  updatePopupRemainingPreview(): void {
    const amt = Number(this.popupDeductionAmount || 0);
    if (amt <= 0) {
      this.popupDeductionError = 'Deduction amount must be greater than zero';
    } else if (amt > this.popupOutstandingAdvance) {
      this.popupDeductionError = `Deduction cannot exceed outstanding advance of ₹${this.popupOutstandingAdvance.toFixed(2)}`;
    } else {
      this.popupDeductionError = '';
    }
    this.popupRemainingBalancePreview = Math.max(0, this.popupOutstandingAdvance - amt);
  }

  generatePayslipNoDeduction(): void {
    this.showActiveAdvancePopup = false;
    if (this.selectedEmpForGeneration) {
      this.executeGeneratePayslip(this.selectedEmpForGeneration.employeeId, 0);
    }
  }

  confirmGenerateWithDeduction(): void {
    if (this.popupDeductionError) return;
    this.showActiveAdvancePopup = false;
    if (this.selectedEmpForGeneration) {
      this.executeGeneratePayslip(this.selectedEmpForGeneration.employeeId, Number(this.popupDeductionAmount));
    }
  }

  executeGeneratePayslip(employeeId: string, advanceDeduction?: number): void {
    this.attendanceService.processPayrollForEmployee(employeeId, this.payrollMonth, advanceDeduction).subscribe({
      next: (generated) => {
        this.toastService.success('Payslip Generated', `Payslip generated for ${generated.employeeName}!`);
        this.loadData();
      },
      error: (err) => this.toastService.error('Generation Failed', err?.error?.message || 'Failed to generate payslip')
    });
  }

  // --- ADD SALARY ADVANCE (ADMIN) ---
  openAddAdvanceModal(): void {
    const targetEmpId = this.selectedEmployeeId || this.permissionService.getCurrentUser()?.id;
    if (!targetEmpId) {
      this.toastService.error('Select Employee', 'Please select an employee to add advance');
      return;
    }

    this.targetEmpNameForAdvance = this.selectedEmployeeName || 'Selected Employee';
    this.advanceForm = {
      amount: 0,
      transactionDate: new Date().toISOString().substring(0, 10),
      paymentMode: 'Cash',
      otherPaymentModeDetails: '',
      referenceNumber: '',
      reason: '',
      notes: ''
    };
    this.showAddAdvanceModal = true;
  }

  submitAddAdvance(): void {
    const targetEmpId = this.selectedEmployeeId || this.permissionService.getCurrentUser()?.id;
    if (!targetEmpId || !this.advanceForm.amount || this.advanceForm.amount <= 0) {
      this.toastService.error('Validation Error', 'Please enter a valid advance amount greater than zero');
      return;
    }

    this.attendanceService.addSalaryAdvance(targetEmpId, this.advanceForm).subscribe({
      next: (tx) => {
        this.toastService.success('Advance Added', `Salary advance of ₹${tx.amount} recorded successfully!`);
        this.showAddAdvanceModal = false;
        this.loadData();
      },
      error: (err) => this.toastService.error('Failed to Add Advance', err?.error?.message || 'Failed to add advance')
    });
  }

  // --- RECORD REPAYMENT (ADMIN) ---
  openRecordRepaymentModal(): void {
    const targetEmpId = this.selectedEmployeeId || this.permissionService.getCurrentUser()?.id;
    if (!targetEmpId) {
      this.toastService.error('Select Employee', 'Please select an employee to record repayment');
      return;
    }

    this.targetEmpNameForAdvance = this.selectedEmployeeName || 'Selected Employee';
    this.repaymentForm = {
      amount: 0,
      transactionDate: new Date().toISOString().substring(0, 10),
      paymentMode: 'Bank Transfer',
      referenceNumber: '',
      notes: ''
    };
    this.repaymentValidationError = '';
    this.showRepaymentModal = true;
  }

  validateRepaymentAmount(): void {
    const amt = Number(this.repaymentForm.amount || 0);
    const outstanding = Number(this.activeAdvanceAccount?.outstandingBalance || 0);

    if (amt <= 0) {
      this.repaymentValidationError = 'Repayment amount must be greater than zero';
    } else if (amt > outstanding) {
      this.repaymentValidationError = `Repayment amount cannot exceed outstanding balance of ₹${outstanding.toFixed(2)}`;
    } else {
      this.repaymentValidationError = '';
    }
  }

  submitRepayment(): void {
    const targetEmpId = this.selectedEmployeeId || this.permissionService.getCurrentUser()?.id;
    this.validateRepaymentAmount();
    if (this.repaymentValidationError || !targetEmpId) return;

    this.attendanceService.recordRepayment(targetEmpId, this.repaymentForm).subscribe({
      next: (tx) => {
        this.toastService.success('Repayment Recorded', `Repayment of ₹${tx.amount} recorded successfully!`);
        this.showRepaymentModal = false;
        this.loadData();
      },
      error: (err) => this.toastService.error('Failed to Record Repayment', err?.error?.message || 'Failed to record repayment')
    });
  }

  // --- EDIT & DELETE TRANSACTION (ADMIN) ---
  openEditTransactionModal(tx: AdvanceTransaction): void {
    this.editingTransaction = { ...tx };
    this.showEditTransactionModal = true;
  }

  submitEditTransaction(): void {
    if (!this.editingTransaction || !this.editingTransaction.id) return;
    this.attendanceService.updateAdvanceTransaction(this.editingTransaction.id, this.editingTransaction).subscribe({
      next: () => {
        this.toastService.success('Transaction Updated', 'Advance transaction updated successfully.');
        this.showEditTransactionModal = false;
        this.editingTransaction = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Update Failed', err?.error?.message || 'Failed to update transaction')
    });
  }

  promptDeleteTransaction(tx: AdvanceTransaction): void {
    this.transactionToDelete = tx;
    this.showDeleteTransactionModal = true;
  }

  confirmDeleteTransaction(): void {
    if (!this.transactionToDelete || !this.transactionToDelete.id) return;
    this.attendanceService.deleteAdvanceTransaction(this.transactionToDelete.id).subscribe({
      next: () => {
        this.toastService.success('Transaction Voided', 'Advance transaction voided and account balance recalculated.');
        this.showDeleteTransactionModal = false;
        this.transactionToDelete = null;
        this.loadData();
      },
      error: (err) => this.toastService.error('Void Failed', err?.error?.message || 'Failed to void transaction')
    });
  }

  // --- BATCH PROCESS MODAL ---
  promptBatchProcessPayroll(): void {
    this.batchEmployeeList = [];
    this.batchDeductionsMap = {};

    if (this.payrolls && this.payrolls.length > 0) {
      this.payrolls.forEach(p => {
        const outstanding = p.remainingAdvanceBalance || 0;
        this.batchEmployeeList.push({
          employeeId: p.employeeId,
          employeeCode: p.employeeCode || '',
          employeeName: p.employeeName || '',
          grossSalary: p.grossSalary || 0,
          lopDays: p.lopDays || 0,
          outstandingBalance: outstanding
        });
        this.batchDeductionsMap[p.employeeId] = p.advanceDeductionAmount || outstanding;
      });
      this.showBatchProcessModal = true;
    } else {
      this.confirmBatchProcessPayroll();
    }
  }

  confirmBatchProcessPayroll(): void {
    this.showBatchProcessModal = false;
    this.attendanceService.batchProcessPayroll(this.payrollMonth, this.batchDeductionsMap).subscribe({
      next: (data) => {
        this.toastService.success('Batch Processing', `Payroll successfully processed for ${data.length} employees for ${this.payrollMonth}!`);
        this.loadData();
      },
      error: (err) => this.toastService.error('Processing Failed', err?.error?.message || 'Payroll processing failed')
    });
  }

  // --- DRAWER ACTIONS ---
  openDrawer(record: PayrollRecord): void {
    this.activeRecord = { ...record };
    this.showDrawer = true;
    this.advanceValidationError = '';

    if (!record.isLocked && record.employeeId) {
      this.attendanceService.getSalaryStructure(record.employeeId).subscribe({
        next: (struct) => {
          if (struct && this.activeRecord) {
            this.activeRecord.basicSalary = struct.basicSalary ?? this.activeRecord.basicSalary;
            this.activeRecord.hra = struct.hra ?? this.activeRecord.hra;
            this.activeRecord.allowances = struct.allowances ?? this.activeRecord.allowances;
            this.activeRecord.pfAmount = struct.pfDeduction ?? this.activeRecord.pfAmount;
            this.activeRecord.professionalTax = struct.professionalTax ?? this.activeRecord.professionalTax;
            this.activeRecord.taxAmount = struct.incomeTax ?? this.activeRecord.taxAmount;
            this.recalculateTotals();
          }
        }
      });
    }

    this.attendanceService.getEmployeeAdvanceAccount(record.employeeId).subscribe({
      next: (acc) => this.activeAdvanceAccount = acc,
      error: () => this.activeAdvanceAccount = null
    });
  }

  closeDrawer(): void {
    this.showDrawer = false;
    this.activeRecord = null;
    this.showAddBonus = false;
  }

  recalculateTotals(): void {
    if (!this.activeRecord) return;

    const basic = Number(this.activeRecord.basicSalary || 0);
    const hra = Number(this.activeRecord.hra || 0);
    const allowances = Number(this.activeRecord.allowances || 0);
    const ot = Number(this.activeRecord.overtimeAmount || 0);
    const bonus = Number(this.activeRecord.bonusAmount || 0);
    const yearlyBonus = Number(this.activeRecord.yearlyBonusAmount || 0);
    const otherEarn = Number(this.activeRecord.otherEarnings || 0);

    const pf = Number(this.activeRecord.pfAmount || 0);
    const profTax = Number(this.activeRecord.professionalTax || 0);
    const tax = Number(this.activeRecord.taxAmount || 0);
    const lop = Number(this.activeRecord.lopAmount || 0);
    const advDeduct = Number(this.activeRecord.advanceDeductionAmount || 0);
    const otherDed = Number(this.activeRecord.otherDeductions || 0);

    const gross = basic + hra + allowances + ot + bonus + yearlyBonus + otherEarn;
    const totalDed = pf + profTax + tax + lop + advDeduct + otherDed;
    const net = Math.max(0, gross - totalDed);

    this.activeRecord.grossSalary = gross;
    this.activeRecord.totalEarnings = gross;
    this.activeRecord.totalDeductions = totalDed;
    this.activeRecord.netSalary = net;

    this.validateAdvanceDeductionInDrawer();
  }

  validateAdvanceDeductionInDrawer(): void {
    if (!this.activeRecord) return;
    const deduct = Number(this.activeRecord.advanceDeductionAmount || 0);

    if (deduct < 0) {
      this.advanceValidationError = 'Advance deduction cannot be negative';
      return;
    }

    if (this.activeAdvanceAccount) {
      const balance = Number(this.activeAdvanceAccount.outstandingBalance || 0);
      if (deduct > balance) {
        this.advanceValidationError = `Advance deduction cannot exceed outstanding balance of ₹${balance.toFixed(2)}`;
        return;
      }
    }

    this.advanceValidationError = '';
  }

  toggleAddBonusForm(): void {
    this.showAddBonus = !this.showAddBonus;
    this.newBonus = { bonusType: 'PERFORMANCE_BONUS', amount: 0, remarks: '' };
  }

  submitBonus(): void {
    if (!this.activeRecord || !this.newBonus.amount || this.newBonus.amount <= 0) {
      this.toastService.error('Validation Error', 'Please enter a valid bonus amount');
      return;
    }

    const payload: EmployeeBonus = {
      employeeId: this.activeRecord.employeeId,
      bonusType: this.newBonus.bonusType as any,
      amount: this.newBonus.amount,
      payrollMonth: this.payrollMonth,
      remarks: this.newBonus.remarks
    };

    this.attendanceService.addBonus(payload).subscribe({
      next: (b) => {
        this.toastService.success('Bonus Added', `Added ${b.bonusType.replace('_', ' ')} of ₹${b.amount}!`);
        this.showAddBonus = false;
        if (this.activeRecord) {
          if (b.bonusType === 'YEARLY_BONUS') {
            this.activeRecord.yearlyBonusAmount = (this.activeRecord.yearlyBonusAmount || 0) + b.amount;
          } else {
            this.activeRecord.bonusAmount = (this.activeRecord.bonusAmount || 0) + b.amount;
          }
          this.recalculateTotals();
        }
      },
      error: (err) => this.toastService.error('Bonus Failed', err?.error?.message || 'Failed to add bonus')
    });
  }

  saveDraft(): void {
    if (!this.activeRecord) return;
    this.validateAdvanceDeductionInDrawer();
    if (this.advanceValidationError) return;

    this.attendanceService.saveDraftPayroll(this.activeRecord).subscribe({
      next: (saved) => {
        this.toastService.success('Draft Saved', `Draft payroll saved for ${saved.employeeName}!`);
        this.activeRecord = saved;
        this.loadData();
      },
      error: (err) => this.toastService.error('Save Failed', err?.error?.message || 'Failed to save draft')
    });
  }

  generatePayslipFromDrawer(): void {
    if (!this.activeRecord) return;
    this.validateAdvanceDeductionInDrawer();
    if (this.advanceValidationError) return;

    const deduct = Number(this.activeRecord.advanceDeductionAmount || 0);

    this.attendanceService.processPayrollForEmployee(this.activeRecord.employeeId, this.payrollMonth, deduct).subscribe({
      next: (generated) => {
        this.toastService.success('Payslip Generated', `Payslip generated for ${generated.employeeName}!`);
        this.activeRecord = generated;
        this.loadData();
      },
      error: (err) => this.toastService.error('Generation Failed', err?.error?.message || 'Failed to generate payslip')
    });
  }

  lockPayrollRecord(): void {
    if (!this.activeRecord || !this.activeRecord.id) return;
    this.attendanceService.lockPayroll(this.activeRecord.id).subscribe({
      next: (locked) => {
        this.toastService.success('Payroll Locked', `Payroll record locked successfully.`);
        this.activeRecord = locked;
        this.loadData();
      },
      error: (err) => this.toastService.error('Lock Failed', err?.error?.message || 'Failed to lock payroll')
    });
  }

  unlockPayrollRecord(reason: string = 'Unlocked by Admin'): void {
    if (!this.activeRecord || !this.activeRecord.id) return;
    this.attendanceService.unlockPayroll(this.activeRecord.id, reason).subscribe({
      next: (unlocked) => {
        this.toastService.success('Success', 'Payroll record unlocked successfully');
        this.activeRecord = unlocked;
        this.loadData();
      },
      error: (err) => this.toastService.error('Error', err?.error?.message || 'Failed to unlock payroll')
    });
  }

  promptDeletePayslip(record: PayrollRecord): void {
    if (record.isLocked) {
      this.toastService.error('Locked Payslip', 'Cannot delete a locked payslip. Unlock it first.');
      return;
    }
    this.selectedPayslipToDelete = record;
    this.deleteReason = '';
    this.showDeleteModal = true;
  }

  confirmDeletePayslip(): void {
    if (!this.selectedPayslipToDelete) return;
    const target = this.selectedPayslipToDelete;
    const recordId = target.id || (target as any)._id || target.payslipNumber || target.employeeId;

    if (!recordId) {
      this.toastService.success('Payslip Deleted', 'Draft payslip removed successfully.');
      this.showDeleteModal = false;
      this.selectedPayslipToDelete = null;
      this.loadData();
      return;
    }

    this.attendanceService.deletePayslip(recordId, this.deleteReason).subscribe({
      next: () => {
        this.toastService.success('Payslip Deleted', 'Payslip deleted successfully.');
        this.showDeleteModal = false;
        this.selectedPayslipToDelete = null;
        this.loadData();
      },
      error: (err) => {
        this.toastService.error('Delete Failed', err?.error?.message || 'Failed to delete payslip');
      }
    });
  }

  downloadPdf(employeeId: string, month: string): void {
    if (!employeeId) {
      this.toastService.error('Download Failed', 'Invalid employee or payslip ID');
      return;
    }
    this.attendanceService.downloadPayslipPdf(employeeId, month).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Payslip_${month}_${employeeId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => this.toastService.error('Download Failed', 'Failed to download payslip PDF')
    });
  }

  exportPayrollExcel(): void {
    this.apiService.getBlob('/payroll/export/excel', { payrollMonth: this.payrollMonth }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Payroll_Report_${this.payrollMonth}.xlsx`;
        a.click();
      }
    });
  }

  exportPayrollPdf(): void {
    this.apiService.getBlob('/payroll/export/pdf', { payrollMonth: this.payrollMonth }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Payroll_Report_${this.payrollMonth}.pdf`;
        a.click();
      }
    });
  }

  exportAdvanceExcel(): void {
    this.apiService.getBlob('/payroll/advance/export/excel').subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Salary_Advances.xlsx';
        a.click();
      }
    });
  }

  exportAdvancePdf(): void {
    this.apiService.getBlob('/payroll/advance/export/pdf').subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'Salary_Advances.pdf';
        a.click();
      }
    });
  }
}