import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ExcelExportService } from '../../../core/services/excel-export.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { UiConfigService } from '../../../core/services/ui-config.service';
import { buildYearOptions, MONTH_NAMES } from '../../../core/utils/month-year.util';
import { Router } from '@angular/router';

@Component({
  selector: 'app-customer-collection-report',
  template: `
    <div class="cr-container rpt-animate">

      <!-- Summary Cards -->
      <div class="cr-summary">
        <div class="cr-summary-card">
          <div class="cr-summary-label">Total Records</div>
          <div class="cr-summary-value">{{ totals ? totals.totalRecords : '—' }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--accent">
          <div class="cr-summary-label">Total Amount</div>
          <div class="cr-summary-value">{{ totals ? '₹ ' + (totals.totalAmount | number:'1.2-2') : '—' }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--received">
          <div class="cr-summary-label">Total Received</div>
          <div class="cr-summary-value">{{ totals && totals.totalReceivedAmount != null ? '₹ ' + (totals.totalReceivedAmount | number:'1.2-2') : '—' }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--pending">
          <div class="cr-summary-label">Total Pending</div>
          <div class="cr-summary-value">{{ totals && totals.totalPendingAmount != null ? '₹ ' + (totals.totalPendingAmount | number:'1.2-2') : '—' }}</div>
        </div>
      </div>

      <!-- Filters -->
      <div class="cr-filters">
        <div class="cr-filters-header">
          <mat-icon class="cr-filters-icon">filter_list</mat-icon>
          <span class="cr-filters-title">Filters</span>
        </div>
        <form [formGroup]="filterForm" class="cr-filter-grid">
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Customer Name</mat-label>
            <input matInput formControlName="customerName" [matAutocomplete]="ccrCustomerAuto" (input)="onCustomerSearchChange()" placeholder="ANBU">
            <mat-autocomplete #ccrCustomerAuto="matAutocomplete">
              <mat-option *ngFor="let c of filteredCustomerSuggestions" [value]="c">{{ c }}</mat-option>
            </mat-autocomplete>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Month</mat-label>
            <mat-select formControlName="month">
              <mat-option value="">All months</mat-option>
              <mat-option *ngFor="let m of monthOptions" [value]="m.value">{{ m.label }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Year</mat-label>
            <input matInput type="number" formControlName="year" placeholder="e.g. 2026">
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Date From</mat-label>
            <input matInput [matDatepicker]="df" formControlName="invoiceDateFrom">
            <mat-datepicker-toggle matSuffix [for]="df"></mat-datepicker-toggle>
            <mat-datepicker #df></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Date To</mat-label>
            <input matInput [matDatepicker]="dt" formControlName="invoiceDateTo">
            <mat-datepicker-toggle matSuffix [for]="dt"></mat-datepicker-toggle>
            <mat-datepicker #dt></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Amount Status</mat-label>
            <input matInput formControlName="amountStatus" [matAutocomplete]="filterStatusAuto" placeholder="Type or choose">
            <mat-autocomplete #filterStatusAuto="matAutocomplete">
              <mat-option *ngFor="let s of amountStatusSuggestions" [value]="s">{{ s }}</mat-option>
            </mat-autocomplete>
            <mat-hint>Optional; matches any case</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Payment Mode</mat-label>
            <mat-select formControlName="paymentMode">
              <mat-option value="">All modes</mat-option>
              <mat-option *ngFor="let opt of paymentModeOptions" [value]="opt">{{ opt }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Invoice No / AWB</mat-label>
            <input matInput formControlName="invoiceNumber" placeholder="e.g. AWB123">
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item cr-span-2">
            <mat-label>Description / Remarks</mat-label>
            <input matInput formControlName="description" placeholder="Search in remarks (any case)">
          </mat-form-field>
          <div class="cr-actions">
            <button mat-raised-button color="primary" type="button" (click)="load(0)" class="cr-btn-search">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button type="button" (click)="reset()" class="cr-btn-clear">
              <mat-icon>clear</mat-icon> Reset
            </button>
            <button mat-stroked-button type="button" (click)="exportExcel()" class="cr-btn-clear" style="margin-left: auto; border-color: #cbd5e1 !important; border-style: solid !important; border-width: 1px !important;">
              <mat-icon>grid_on</mat-icon> Export Excel
            </button>
          </div>
        </form>
      </div>

      <!-- Data Table -->
      <div class="cr-table-container">
        <div class="cr-loading-shade" *ngIf="loading">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
        <div class="cr-table-scroll">
          <table class="cr-table">
            <thead>
              <tr>
                <th>Invoice Date</th>
                <th>Invoice No</th>
                <th>Customer Name</th>
                <th class="cr-th-amount">Total Amount</th>
                <th class="cr-th-amount">Received Amount</th>
                <th class="cr-th-amount">Pending Amount</th>
                <th>Amount Status</th>
                <th>Payment Mode</th>
                <th>Description</th>
                <th class="cr-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of rows; let i = index"
                  class="cr-row-click"
                  [class.cr-row-even]="i % 2 === 0"
                  [class.cr-row-odd]="i % 2 !== 0"
                  [class.cr-row-editing]="editingItemId === r.id"
                  (click)="openEntry(r)">
                <td class="cr-td-date">{{ r.entryDate | date:'yyyy-MM-dd' }}</td>
                <td class="cr-td-invoice">{{ r.awbNo || '—' }}</td>
                <td class="cr-td-customer">{{ r.customerName }}</td>
                <td class="cr-td-amount">₹ {{ r.amount != null ? (r.amount | number:'1.2-2') : '—' }}</td>
                <td class="cr-td-amount" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== r.id">
                    <span style="color: #15803d; font-weight: 600;">₹ {{ (r.receivedAmount != null ? r.receivedAmount : (r.amountStatus === 'Paid' ? r.amount : 0)) | number:'1.2-2' }}</span>
                  </ng-container>
                  <input *ngIf="editingItemId === r.id" type="number" class="cr-inline-input" [(ngModel)]="editDraft.receivedAmount" (ngModelChange)="onDraftReceivedAmountChange(r)" [ngModelOptions]="{standalone: true}" placeholder="0.00" style="width: 85px; font-weight: 600; text-align: right;">
                </td>
                <td class="cr-td-amount" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== r.id">
                    <span [style.color]="(r.pendingAmount != null ? r.pendingAmount : (r.amountStatus === 'Paid' ? 0 : r.amount)) > 0 ? '#b91c1c' : '#64748b'" style="font-weight: 600;">
                      ₹ {{ (r.pendingAmount != null ? r.pendingAmount : (r.amountStatus === 'Paid' ? 0 : r.amount)) | number:'1.2-2' }}
                    </span>
                  </ng-container>
                  <span *ngIf="editingItemId === r.id" style="color: #b91c1c; font-weight: 700;">
                    ₹ {{ getDraftPendingAmount(r) | number:'1.2-2' }}
                  </span>
                </td>
                <td class="cr-td-status">
                  <ng-container *ngIf="editingItemId !== r.id">
                    <span class="cr-status-badge"
                          [class.cr-status-paid]="r.amountStatus === 'Paid'"
                          [class.cr-status-pending]="r.amountStatus === 'Pending' || r.amountStatus === 'UnPaid'"
                          [class.cr-status-partial]="r.amountStatus === 'Partial' || r.amountStatus === 'CashOnDelivery'">
                      {{ r.amountStatus || '—' }}
                    </span>
                  </ng-container>
                  <mat-form-field *ngIf="editingItemId === r.id" appearance="outline" class="cr-edit-field" subscriptSizing="dynamic">
                    <input matInput [(ngModel)]="editDraft.amountStatus" [matAutocomplete]="rowStatusAuto"
                           [ngModelOptions]="{standalone: true}" placeholder="Pick or type">
                  </mat-form-field>
                </td>
                <td class="cr-td-mode" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== r.id">
                    <span>{{ r.paymentMode === 'Others' ? (r.otherPaymentMode || 'Others') : (r.paymentMode || '—') }}</span>
                  </ng-container>
                  <div *ngIf="editingItemId === r.id" style="display: flex; flex-direction: column; gap: 4px;">
                    <mat-form-field appearance="outline" class="cr-edit-field" subscriptSizing="dynamic">
                      <mat-select [(ngModel)]="editDraft.paymentMode" [ngModelOptions]="{standalone: true}">
                        <mat-option value="">None</mat-option>
                        <mat-option *ngFor="let opt of paymentModeOptions" [value]="opt">{{ opt }}</mat-option>
                      </mat-select>
                    </mat-form-field>
                    <input *ngIf="editDraft.paymentMode === 'Others'" class="cr-inline-input" [(ngModel)]="editDraft.otherPaymentMode" [ngModelOptions]="{standalone: true}" placeholder="Specify mode">
                  </div>
                </td>
                <td class="cr-td-desc">
                  <ng-container *ngIf="editingItemId !== r.id">
                    <span class="cr-desc-readonly" [title]="r.remarks || r.status || ''">
                      {{ r.remarks || r.status || '—' }}
                    </span>
                  </ng-container>
                  <input *ngIf="editingItemId === r.id" class="cr-inline-input" [(ngModel)]="editDraft.description"
                         [ngModelOptions]="{standalone: true}" placeholder="Remarks">
                </td>
                <td class="cr-actions-cell" (click)="$event.stopPropagation()">
                  <div class="cr-actions-flex">
                    <ng-container *ngIf="editingItemId !== r.id">
                      <button mat-icon-button type="button" (click)="openEntry(r)" matTooltip="View details" class="cr-action-btn cr-action-view">
                        <mat-icon>visibility</mat-icon>
                      </button>
                      <button mat-icon-button type="button" [disabled]="true" matTooltip="View breakup" class="cr-action-btn cr-action-breakup" style="opacity: 0.35;">
                        <mat-icon>table_chart</mat-icon>
                      </button>
                      <button mat-icon-button type="button" [disabled]="true" matTooltip="More" class="cr-action-btn" style="opacity: 0.35;">
                        <mat-icon>more_vert</mat-icon>
                      </button>
                      <button mat-icon-button type="button" (click)="startEdit(r)" matTooltip="Edit details" class="cr-action-btn cr-action-edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                    </ng-container>
                    <ng-container *ngIf="editingItemId === r.id">
                      <button mat-icon-button type="button" color="primary" (click)="saveEdit(r)" matTooltip="Save" class="cr-action-btn cr-action-save">
                        <mat-icon>check</mat-icon>
                      </button>
                      <button mat-icon-button type="button" (click)="cancelEdit()" matTooltip="Cancel" class="cr-action-btn cr-action-cancel">
                        <mat-icon>close</mat-icon>
                      </button>
                    </ng-container>
                  </div>
                </td>
              </tr>
              <tr *ngIf="rows.length === 0 && !loading" class="cr-empty-row">
                <td colspan="10" class="cr-empty-cell">
                  <mat-icon class="cr-empty-icon">inbox</mat-icon>
                  <span>No records found.</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="total" [pageIndex]="page" [pageSize]="size" [pageSizeOptions]="[20, 50, 100]" (page)="onPage($event)"></mat-paginator>
      </div>

      <mat-autocomplete #rowStatusAuto="matAutocomplete">
        <mat-option *ngFor="let s of amountStatusSuggestions" [value]="s">{{ s }}</mat-option>
      </mat-autocomplete>
    </div>
  `,
  styles: [`
    /* ── Container ── */
    .cr-container { width: 100%; box-sizing: border-box; font-family: 'Inter', 'Segoe UI', system-ui, -apple-system, sans-serif; }

    /* ── Summary Cards ── */
    .cr-summary {
      display: flex; gap: 16px; margin-bottom: 20px; flex-wrap: wrap;
    }
    .cr-summary-card {
      flex: 1; min-width: 200px;
      background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
      border: 1px solid #e2e8f0; border-radius: 12px;
      padding: 20px 24px;
      display: flex; flex-direction: column; gap: 6px;
      transition: box-shadow 0.2s ease, transform 0.2s ease;
    }
    .cr-summary-card:hover {
      box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
      transform: translateY(-1px);
    }
    .cr-summary-card--accent {
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
      border-color: #bfdbfe;
    }
    .cr-summary-label {
      font-size: 12px; font-weight: 500; text-transform: uppercase; letter-spacing: 0.05em;
      color: #64748b;
    }
    .cr-summary-value {
      font-size: 26px; font-weight: 700; color: #0f172a; line-height: 1.2;
    }
    .cr-summary-card--accent .cr-summary-value { color: #1e40af; }

    /* ── Filters Card ── */
    .cr-filters {
      margin-bottom: 20px; background: #fff;
      padding: 20px 24px; border-radius: 12px;
      border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
    }
    .cr-filters-header {
      display: flex; align-items: center; gap: 8px;
      margin-bottom: 16px; padding-bottom: 12px;
      border-bottom: 1px solid #f1f5f9;
    }
    .cr-filters-icon { color: #64748b; font-size: 20px; width: 20px; height: 20px; }
    .cr-filters-title { font-size: 14px; font-weight: 600; color: #334155; text-transform: uppercase; letter-spacing: 0.04em; }
    .cr-filter-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 12px 16px; align-items: start;
    }
    .cr-filter-item { margin-bottom: -1.25em; }
    .cr-span-2 { grid-column: span 2; }
    .cr-actions {
      grid-column: 1 / -1;
      display: flex; gap: 12px; justify-content: flex-end;
      padding-top: 4px;
    }
    .cr-btn-search {
      min-width: 110px; font-weight: 500; letter-spacing: 0.02em;
      border-radius: 8px !important;
    }
    .cr-btn-clear {
      min-width: 100px; font-weight: 500; letter-spacing: 0.02em;
      border-radius: 8px !important; color: #64748b !important;
      border-color: #cbd5e1 !important;
      border-style: solid !important;
      border-width: 1px !important;
    }

    /* ── Table Container ── */
    .cr-table-container {
      position: relative; background: #fff; border-radius: 12px;
      border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      overflow: hidden;
    }
    .cr-table-scroll { overflow-x: auto; }
    .cr-loading-shade {
      position: absolute; top: 0; left: 0; bottom: 0; right: 0;
      background: rgba(255, 255, 255, 0.8);
      backdrop-filter: blur(2px);
      display: flex; align-items: center; justify-content: center; z-index: 10;
    }

    /* ── Table ── */
    .cr-table {
      width: 100%; border-collapse: collapse;
      font-size: 13px; color: #0f172a;
    }
    .cr-table thead { position: sticky; top: 0; z-index: 5; }
    .cr-table th {
      padding: 14px 16px; text-align: left;
      background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
      border-bottom: 2px solid #e2e8f0;
      font-weight: 600; font-size: 12px;
      text-transform: uppercase; letter-spacing: 0.04em;
      color: #475569; white-space: nowrap;
    }
    .cr-th-actions { text-align: center; width: 210px; }
    .cr-th-amount { text-align: right; }

    /* ── Table Rows ── */
    .cr-row-click { cursor: pointer; transition: background-color 0.15s ease; }
    .cr-row-even { background: #ffffff; }
    .cr-row-odd { background: #f8fafc; }
    .cr-row-click:hover { background: #eff6ff !important; }

    .cr-table td {
      padding: 12px 16px; border-bottom: 1px solid #f1f5f9;
      vertical-align: middle; line-height: 1.5;
    }
    .cr-td-date { white-space: nowrap; font-variant-numeric: tabular-nums; color: #334155; }
    .cr-td-invoice { font-weight: 500; color: #64748b; white-space: nowrap; }
    .cr-td-customer { font-weight: 500; color: #0f172a; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cr-td-amount { text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; color: #0f172a; white-space: nowrap; }

    /* ── Cell Variants ── */
    .cr-cell-mono { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; color: #475569; }

    /* ── Status Badge ── */
    .cr-td-status { white-space: nowrap; }
    .cr-status-badge {
      display: inline-block; padding: 3px 10px;
      border-radius: 20px; font-size: 11px; font-weight: 600;
      text-transform: uppercase; letter-spacing: 0.03em;
      background: #f1f5f9; color: #64748b;
    }
    .cr-status-paid { background: #dcfce7; color: #166534; }
    .cr-status-partial { background: #fef3c7; color: #92400e; }
    .cr-status-pending { background: #fee2e2; color: #991b1b; }

    /* ── Description ── */
    .cr-td-desc {}
    .cr-desc-readonly {
      display: inline-block; max-width: 280px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      vertical-align: middle; color: #475569;
    }

     /* ── Actions Cell ── */
    .cr-row-editing { background: #fefce8 !important; }
    .cr-actions-cell { text-align: center; white-space: nowrap; }
    .cr-actions-flex { display: inline-flex; align-items: center; justify-content: center; gap: 4px; }
    .cr-action-btn { transition: color 0.15s ease, background-color 0.15s ease; }
    .cr-action-btn:hover { background: #f1f5f9; }
    .cr-action-view mat-icon { color: #3b82f6; }
    .cr-action-breakup mat-icon { color: #cbd5e1; }
    .cr-action-edit mat-icon { color: #f59e0b; }
    .cr-action-save mat-icon { color: #16a34a; }
    .cr-action-cancel mat-icon { color: #ef4444; }
    .cr-edit-field { width: 100%; min-width: 160px; max-width: 260px; margin-bottom: 0 !important; }
    .cr-edit-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    .cr-inline-input {
      width: 100%; max-width: 320px;
      padding: 8px 12px; border: 1px solid #cbd5e1; border-radius: 6px;
      box-sizing: border-box; font-size: 13px; color: #0f172a;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .cr-inline-input:focus {
      border-color: #3b82f6; outline: none;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
    }

    /* ── Empty State ── */
    .cr-empty-row:hover { background: transparent !important; }
    .cr-empty-cell {
      text-align: center; padding: 48px 20px !important;
      color: #94a3b8; font-size: 14px;
      display: flex; flex-direction: column; align-items: center; gap: 8px;
    }
    .cr-empty-icon { font-size: 40px; width: 40px; height: 40px; color: #cbd5e1; }

    /* ── Paginator ── */
    :host ::ng-deep .mat-mdc-paginator {
      border-top: 1px solid #f1f5f9;
      background: #f8fafc;
    }

    /* ── Responsive ── */
    @media (max-width: 768px) {
      .cr-filter-grid { grid-template-columns: 1fr; }
      .cr-span-2 { grid-column: span 1; }
      .cr-summary { flex-direction: column; }
      .cr-summary-card { max-width: 100%; }
      .cr-filters { padding: 16px; }
      .cr-table th, .cr-table td { padding: 10px 12px; }
    }
  `]
})
export class CustomerCollectionReportComponent implements OnInit {
  filterForm!: FormGroup;
  amountStatusSuggestions = ['Pending', 'Partial', 'Paid'];
  paymentModeOptions = ['Cash', 'GPAY', 'PhonePe', 'Paytm', 'Others'];
  monthOptions = [
    { value: 1, label: 'January' },
    { value: 2, label: 'February' },
    { value: 3, label: 'March' },
    { value: 4, label: 'April' },
    { value: 5, label: 'May' },
    { value: 6, label: 'June' },
    { value: 7, label: 'July' },
    { value: 8, label: 'August' },
    { value: 9, label: 'September' },
    { value: 10, label: 'October' },
    { value: 11, label: 'November' },
    { value: 12, label: 'December' }
  ];

  customers: { id: string; customerName: string }[] = [];
  rows: any[] = [];
  total = 0;
  page = 0;
  size = 50;
  loading = false;
  totals: { totalRecords: number; totalAmount: number; totalReceivedAmount?: number; totalPendingAmount?: number } | null = null;

  customerSuggestions: string[] = [];
  filteredCustomerSuggestions: string[] = [];
  yearOptions: number[] = [];

  constructor(
    private api: ApiService,
    private excel: ExcelExportService,
    private toast: ToastService,
    private uiConfig: UiConfigService,
    private router: Router,
    private fb: FormBuilder
  ) {}

  ngOnInit() {
    this.filterForm = this.fb.group({
      customerName: [''],
      month: [''],
      year: [''],
      invoiceDateFrom: [null],
      invoiceDateTo: [null],
      amountStatus: [''],
      paymentMode: [''],
      invoiceNumber: [''],
      description: ['']
    });

    this.uiConfig.getUi().subscribe({
      next: (u) => (this.yearOptions = buildYearOptions(u.collectionYearRangePast, u.collectionYearRangeFuture)),
      error: () => (this.yearOptions = buildYearOptions(5, 5))
    });

    this.api.get<PageResponse<any>>('/collection-customers', { page: 0, size: 500 }).subscribe({
      next: (res) => {
        this.customers = (res.content || []).map((c: any) => ({ id: c.id, customerName: c.customerName }));
        this.mergeCustomerSuggestions(this.customers.map((c) => c.customerName));
      },
      error: () => {}
    });

    this.api.get<any[]>('/clients/type/CUSTOMER').subscribe({
      next: (list) => this.mergeCustomerSuggestions((list || []).map((c: any) => c.partyName).filter(Boolean)),
      error: () => {}
    });

    this.load(0);
  }

  private toIsoDate(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private params(): Record<string, string | number> {
    const vals = this.filterForm.value;
    const p: Record<string, string | number> = { page: this.page, size: this.size };

    const cn = (vals.customerName || '').trim();
    if (cn) {
      // Find matches in Collection Customers dropdown first
      const matched = this.customers.find(c => c.customerName.toLowerCase() === cn.toLowerCase());
      if (matched) {
        p['collectionCustomerId'] = matched.id;
      } else {
        p['customerName'] = cn;
      }
    }

    const awb = (vals.invoiceNumber || '').trim();
    if (awb) p['awbNo'] = awb;

    const df = this.toIsoDate(vals.invoiceDateFrom);
    const dt = this.toIsoDate(vals.invoiceDateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;

    const amt = (vals.amountStatus || '').trim();
    if (amt) p['amountStatus'] = amt;

    const pm = (vals.paymentMode || '').trim();
    if (pm) p['paymentMode'] = pm;

    const m = Number(vals.month);
    if (Number.isInteger(m) && m >= 1 && m <= 12) {
      p['entryMonth'] = MONTH_NAMES[m - 1];
    }

    const y = Number(vals.year);
    if (Number.isInteger(y) && y > 0) p['entryYear'] = y;

    const desc = (vals.description || '').trim();
    if (desc) p['remarks'] = desc;

    return p;
  }

  load(pageIdx: number) {
    this.cancelEdit();
    this.page = pageIdx;
    this.loading = true;
    this.api.get<PageResponse<any>>('/collection-center/entries', this.params()).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.total = res?.totalElements ?? 0;
        this.loading = false;
        this.loadTotals();
      },
      error: () => { this.loading = false; this.toast.error('Error', 'Failed to load'); }
    });
  }

  loadTotals() {
    this.api.get<{ totalRecords: number; totalAmount: number; totalReceivedAmount?: number; totalPendingAmount?: number }>('/collection-center/entries/report-totals', this.params()).subscribe({
      next: (t) => { this.totals = t; },
      error: () => { this.totals = null; }
    });
  }

  onPage(e: any) {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  reset() {
    this.filterForm.reset();
    this.filteredCustomerSuggestions = [...this.customerSuggestions];
    this.load(0);
  }

  onCustomerSearchChange(): void {
    const value = this.filterForm.get('customerName')?.value || '';
    const q = value.trim().toLowerCase();
    this.filteredCustomerSuggestions = !q
      ? [...this.customerSuggestions]
      : this.customerSuggestions.filter((name) => name.toLowerCase().includes(q));
  }

  private mergeCustomerSuggestions(names: string[]): void {
    const set = new Set(this.customerSuggestions);
    names.forEach((name) => {
      if (name) set.add(name);
    });
    this.customerSuggestions = Array.from(set).sort();
    this.filteredCustomerSuggestions = [...this.customerSuggestions];
  }

  editingItemId: string | null = null;
  editDraft = { amountStatus: 'Pending', paymentMode: '', otherPaymentMode: '', receivedAmount: null as number | null, description: '' };

  startEdit(item: any): void {
    if (this.editingItemId != null && this.editingItemId !== item.id) {
      this.toast.warning('Edit in progress', 'Save or cancel the current row before editing another.');
      return;
    }
    const amt = item.amount != null ? Number(item.amount) : 0;
    const rec = item.receivedAmount != null ? Number(item.receivedAmount) : (item.amountStatus === 'Paid' ? amt : 0);
    this.editingItemId = item.id;
    this.editDraft = {
      amountStatus: (item.amountStatus ?? 'Pending').toString(),
      paymentMode: (item.paymentMode ?? '').toString(),
      otherPaymentMode: (item.otherPaymentMode ?? '').toString(),
      receivedAmount: rec,
      description: item.remarks ?? ''
    };
  }

  onDraftReceivedAmountChange(item: any): void {
    const amt = item?.amount != null ? Number(item.amount) : 0;
    const rec = this.editDraft.receivedAmount;
    if (rec !== null && rec !== undefined && (rec as any) !== '') {
      const numRec = Number(rec);
      if (numRec <= 0) {
        this.editDraft.amountStatus = 'Pending';
      } else if (numRec >= amt && amt > 0) {
        this.editDraft.amountStatus = 'Paid';
      } else {
        this.editDraft.amountStatus = 'Partial';
      }
    } else {
      this.editDraft.amountStatus = 'Pending';
    }
  }

  getDraftPendingAmount(item: any): number {
    const amt = item?.amount != null ? Number(item.amount) : 0;
    const rec = this.editDraft.receivedAmount != null ? Number(this.editDraft.receivedAmount) : 0;
    return Math.max(0, amt - rec);
  }

  cancelEdit(): void {
    this.editingItemId = null;
    this.editDraft = { amountStatus: '', paymentMode: '', otherPaymentMode: '', receivedAmount: null, description: '' };
  }

  saveEdit(item: any): void {
    if (this.editingItemId !== item.id) return;
    const amt = item.amount != null ? Number(item.amount) : 0;
    const rec = this.editDraft.receivedAmount;
    if (rec !== null && rec !== undefined && (rec as any) !== '') {
      const numRec = Number(rec);
      if (numRec < 0) {
        this.toast.error('Validation Error', 'Received amount cannot be negative');
        return;
      }
      if (numRec > amt && amt > 0) {
        this.toast.error('Validation Error', `Received amount (₹${numRec}) cannot exceed total amount (₹${amt})`);
        return;
      }
    }

    const statusRaw = (this.editDraft.amountStatus ?? '').toString().trim();
    const pmRaw = (this.editDraft.paymentMode ?? '').toString().trim();
    const otherPmRaw = (this.editDraft.otherPaymentMode ?? '').toString().trim();
    const payload: Record<string, any> = {
      amountStatus: statusRaw || 'Pending',
      paymentMode: pmRaw || null,
      otherPaymentMode: pmRaw === 'Others' ? otherPmRaw : null,
      receivedAmount: rec !== null && rec !== undefined && (rec as any) !== '' ? Number(rec) : null,
      description: this.editDraft.description ?? ''
    };

    this.api.patch<any>(`/collection-center/entries/${item.id}/status`, payload).subscribe({
      next: (updated: any) => {
        if (updated && item.id === updated.id) {
          item.amountStatus = updated.amountStatus ?? payload['amountStatus'];
          item.paymentMode = updated.paymentMode ?? payload['paymentMode'];
          item.otherPaymentMode = updated.otherPaymentMode ?? payload['otherPaymentMode'];
          item.receivedAmount = updated.receivedAmount;
          item.pendingAmount = updated.pendingAmount;
          item.remarks = updated.remarks ?? '';
        }
        this.cancelEdit();
        this.toast.success('Success', 'Saved successfully');
        this.loadTotals();
      },
      error: (err) => {
        console.error('Customer Collection Report patch failed', err);
        this.toast.error('Error', err?.error?.message || err?.error || 'Failed to save');
      }
    });
  }

  openEntry(r: { id?: string }): void {
    if (!r?.id || this.editingItemId) return;
    this.router.navigate(['/client-entries/collection-center/edit', r.id]);
  }

  async exportExcel() {
    const p = { ...this.params(), page: 0, size: 10000 };
    this.api.get<PageResponse<any>>('/collection-center/entries', p).subscribe({
      next: async (res) => {
        const data = res.content || [];
        if (!data.length) {
          this.toast.warning('Export', 'No rows');
          return;
        }
        const headers = ['Date', 'Month', 'Year', 'Customer', 'AWB', 'Receiver', 'Pincode', 'Courier', 'Weight', 'Amount', 'Amount Status', 'Payment Mode', 'Status'];
        const exportRows = data.map((r: any) => ({
          Date: r.entryDate,
          Month: r.entryMonth,
          Year: r.entryYear,
          Customer: r.customerName,
          AWB: r.awbNo,
          Receiver: r.receiverName,
          Pincode: r.pincode,
          Courier: r.courier,
          Weight: r.weight,
          Amount: r.amount,
          'Amount Status': r.amountStatus,
          'Payment Mode': r.paymentMode === 'Others' ? (r.otherPaymentMode || 'Others') : (r.paymentMode ?? ''),
          Status: r.status
        }));

        let party = 'CustomerCollection';
        const cn = this.filterForm.get('customerName')?.value || '';
        if (cn.trim()) {
          party = cn.trim();
        }

        let monthStr = 'ALL';
        let yearStr = 'ALL';
        const m = Number(this.filterForm.get('month')?.value);
        if (Number.isInteger(m) && m >= 1 && m <= 12) {
          monthStr = MONTH_NAMES[m - 1];
        }
        const y = Number(this.filterForm.get('year')?.value);
        if (Number.isInteger(y) && y > 0) {
          yearStr = String(y);
        }

        const cleanParty = party.replace(/[^a-zA-Z0-9]/g, '');
        const filename = `${cleanParty}_${monthStr}_${yearStr}`;

        await this.excel.exportToExcel(exportRows, filename, headers, { exactFilename: true });
        this.toast.success('Export', 'Downloaded');
      },
      error: () => this.toast.error('Error', 'Export failed')
    });
  }
}
