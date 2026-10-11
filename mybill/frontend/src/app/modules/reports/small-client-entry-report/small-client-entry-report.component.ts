import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SharedModule } from '../../../shared/shared.module';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { Router } from '@angular/router';
import { InvoicePreviewService } from '../../../core/services/invoice-preview.service';
import { PdfConfirmDialogComponent } from '../../master/monthly-courier-quotation/monthly-courier-quotation-list/monthly-courier-quotation-list.component';
import { ReportConfirmDeleteDialogComponent } from '../courier-report/courier-report.component';

/**
 * Client Entry Report: monthly invoices that have been downloaded at least once.
 * Inline edits use Edit -> Save / Cancel only (no save on blur).
 */
@Component({
  selector: 'app-small-client-entry-report',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatTooltipModule,
    MatDialogModule,
    SharedModule
  ],
  template: `
    <div class="cr-container rpt-animate">

      <!-- Summary Cards -->
      <div class="cr-summary">
        <div class="cr-summary-card">
          <div class="cr-summary-label">Total Records</div>
          <div class="cr-summary-value">{{ totalRecords }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--accent">
          <div class="cr-summary-label">Total Amount</div>
          <div class="cr-summary-value">₹ {{ pageRevenue | number:'1.2-2' }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--received">
          <div class="cr-summary-label">Total Received</div>
          <div class="cr-summary-value">₹ {{ pageReceived | number:'1.2-2' }}</div>
        </div>
        <div class="cr-summary-card cr-summary-card--pending">
          <div class="cr-summary-label">Total Pending</div>
          <div class="cr-summary-value">₹ {{ pagePending | number:'1.2-2' }}</div>
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
            <input matInput formControlName="customerName" [matAutocomplete]="clientCustomerAuto" (input)="onCustomerSearchChange()" placeholder="ANBU">
            <mat-autocomplete #clientCustomerAuto="matAutocomplete">
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
            <mat-label>Invoice Date From</mat-label>
            <input matInput [matDatepicker]="fromPicker" formControlName="invoiceDateFrom">
            <mat-datepicker-toggle matSuffix [for]="fromPicker"></mat-datepicker-toggle>
            <mat-datepicker #fromPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Invoice Date To</mat-label>
            <input matInput [matDatepicker]="toPicker" formControlName="invoiceDateTo">
            <mat-datepicker-toggle matSuffix [for]="toPicker"></mat-datepicker-toggle>
            <mat-datepicker #toPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Amount Status</mat-label>
            <mat-select formControlName="amountStatus">
              <mat-option value="">All statuses</mat-option>
              <mat-option *ngFor="let opt of amountStatusOptions" [value]="opt">{{ opt }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item">
            <mat-label>Invoice No</mat-label>
            <input matInput formControlName="invoiceNumber" placeholder="e.g. 0001/2026-27">
          </mat-form-field>
          <mat-form-field appearance="outline" class="cr-filter-item cr-span-2">
            <mat-label>Description</mat-label>
            <input matInput formControlName="description" placeholder="Search in description (any case)">
          </mat-form-field>
          <div class="cr-actions">
            <button mat-raised-button color="primary" type="button" (click)="loadData()" class="cr-btn-search">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button type="button" (click)="loadData()" class="cr-btn-refresh" matTooltip="Refresh report data">
              <mat-icon>refresh</mat-icon> Refresh
            </button>
            <button mat-stroked-button type="button" (click)="resetFilters()" class="cr-btn-clear">
              <mat-icon>clear</mat-icon> Clear
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
                <th>Description</th>
                <th class="cr-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let item of data; let i = index"
                  class="cr-row-click"
                  [class.cr-row-even]="i % 2 === 0"
                  [class.cr-row-odd]="i % 2 !== 0"
                  [class.cr-row-editing]="editingItemId === item.id"
                  (click)="openEntry(item)">
                <td class="cr-td-date">{{ item.invoiceDate | date:'yyyy-MM-dd' }}</td>
                <td class="cr-td-invoice">{{ item.invoiceNumber }}</td>
                <td class="cr-td-customer">{{ item.customerName }}</td>
                <td class="cr-td-amount">₹ {{ item.totalAmount | number:'1.2-2' }}</td>
                <td class="cr-td-amount" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== item.id">
                    <span style="color: #15803d; font-weight: 600;">₹ {{ (item.receivedAmount != null ? item.receivedAmount : (item.amountStatus === 'Paid' ? item.totalAmount : 0)) | number:'1.2-2' }}</span>
                  </ng-container>
                  <input *ngIf="editingItemId === item.id" type="number" class="cr-inline-input" [(ngModel)]="editDraft.receivedAmount" (ngModelChange)="onDraftReceivedAmountChange(item)" [ngModelOptions]="{standalone: true}" placeholder="0.00" style="width: 85px; font-weight: 600; text-align: right;">
                </td>
                <td class="cr-td-amount" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== item.id">
                    <span [style.color]="(item.pendingAmount != null ? item.pendingAmount : (item.amountStatus === 'Paid' ? 0 : item.totalAmount)) > 0 ? '#b91c1c' : '#64748b'" style="font-weight: 600;">
                      ₹ {{ (item.pendingAmount != null ? item.pendingAmount : (item.amountStatus === 'Paid' ? 0 : item.totalAmount)) | number:'1.2-2' }}
                    </span>
                  </ng-container>
                  <span *ngIf="editingItemId === item.id" style="color: #b91c1c; font-weight: 700;">
                    ₹ {{ getDraftPendingAmount(item) | number:'1.2-2' }}
                  </span>
                </td>
                <td class="cr-td-status">
                  <ng-container *ngIf="editingItemId !== item.id">
                    <span class="cr-status-badge"
                          [class.cr-status-paid]="item.amountStatus === 'Paid'"
                          [class.cr-status-partial]="item.amountStatus === 'Partial'"
                          [class.cr-status-pending]="item.amountStatus === 'Pending'">
                      {{ item.amountStatus }}
                    </span>
                  </ng-container>
                  <mat-form-field *ngIf="editingItemId === item.id" appearance="outline" class="cr-edit-field" subscriptSizing="dynamic">
                    <mat-select [(ngModel)]="editDraft.amountStatus" [ngModelOptions]="{standalone: true}">
                      <mat-option *ngFor="let opt of amountStatusOptions" [value]="opt">{{ opt }}</mat-option>
                    </mat-select>
                  </mat-form-field>
                </td>
                <td class="cr-td-desc">
                  <ng-container *ngIf="editingItemId !== item.id">
                    <span class="cr-desc-readonly" [title]="item.description">{{ item.description || '—' }}</span>
                  </ng-container>
                  <input *ngIf="editingItemId === item.id" class="cr-inline-input" [(ngModel)]="editDraft.description"
                         [ngModelOptions]="{standalone: true}" placeholder="Description">
                </td>
                <td class="cr-actions-cell" (click)="$event.stopPropagation()">
                  <div class="cr-actions-flex">
                    <ng-container *ngIf="editingItemId !== item.id">
                      <button mat-icon-button type="button" (click)="viewInvoice(item)" matTooltip="View invoice" class="cr-action-btn cr-action-view">
                        <mat-icon>visibility</mat-icon>
                      </button>
                      <button mat-icon-button type="button" (click)="viewBreakup(item)" matTooltip="View breakup" class="cr-action-btn cr-action-breakup">
                        <mat-icon>table_chart</mat-icon>
                      </button>
                      <button mat-icon-button type="button" [matMenuTriggerFor]="docMenu" matTooltip="More" class="cr-action-btn">
                        <mat-icon>more_vert</mat-icon>
                      </button>
                      <mat-menu #docMenu="matMenu">
                        <button mat-menu-item type="button" (click)="printInvoice(item)"><mat-icon>print</mat-icon> Print invoice</button>
                        <button mat-menu-item type="button" (click)="downloadInvoice(item)"><mat-icon>download</mat-icon> Download invoice PDF</button>
                        <button mat-menu-item type="button" (click)="printBreakup(item)"><mat-icon>print</mat-icon> Print breakup</button>
                        <button mat-menu-item type="button" (click)="downloadBreakup(item)"><mat-icon>download</mat-icon> Download breakup PDF</button>
                        <button *appHasPermission="'SMALL_CLIENT_ENTRY_REPORT:delete'" mat-menu-item type="button" (click)="deleteEntry(item)" style="color: #dc2626;">
                          <mat-icon style="color: #dc2626;">delete</mat-icon> Delete Small Client Entry
                        </button>
                      </mat-menu>
                      <button mat-icon-button type="button" (click)="startEdit(item)" matTooltip="Edit status" class="cr-action-btn cr-action-edit">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button *appHasPermission="'SMALL_CLIENT_ENTRY_REPORT:delete'" mat-icon-button type="button" (click)="deleteEntry(item)" matTooltip="Delete Small Client Entry" class="cr-action-btn cr-action-delete">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </ng-container>
                    <ng-container *ngIf="editingItemId === item.id">
                      <button mat-icon-button type="button" color="primary" (click)="saveEdit(item)" matTooltip="Save" class="cr-action-btn cr-action-save">
                        <mat-icon>check</mat-icon>
                      </button>
                      <button mat-icon-button type="button" (click)="cancelEdit()" matTooltip="Cancel" class="cr-action-btn cr-action-cancel">
                        <mat-icon>close</mat-icon>
                      </button>
                    </ng-container>
                  </div>
                </td>
              </tr>
              <tr *ngIf="data.length === 0 && !loading" class="cr-empty-row">
                <td colspan="7" class="cr-empty-cell">
                  <mat-icon class="cr-empty-icon">inbox</mat-icon>
                  <span>No records found.</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="totalRecords" [pageSize]="pageSize" [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)"></mat-paginator>
      </div>
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
    .cr-summary-card--received {
      background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
      border-color: #bbf7d0;
    }
    .cr-summary-card--received .cr-summary-value { color: #15803d; }
    .cr-summary-card--pending {
      background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
      border-color: #fecaca;
    }
    .cr-summary-card--pending .cr-summary-value { color: #b91c1c; }
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
    .cr-row-editing { background: #fefce8 !important; }

    .cr-table td {
      padding: 12px 16px; border-bottom: 1px solid #f1f5f9;
      vertical-align: middle; line-height: 1.5;
    }
    .cr-td-date { white-space: nowrap; font-variant-numeric: tabular-nums; color: #334155; }
    .cr-td-invoice { font-weight: 500; color: #1e40af; white-space: nowrap; }
    .cr-td-customer { font-weight: 500; color: #0f172a; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cr-td-amount { text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; color: #0f172a; white-space: nowrap; }

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
    .cr-actions-cell { text-align: center; white-space: nowrap; }
    .cr-actions-flex { display: inline-flex; align-items: center; justify-content: center; gap: 4px; }
    .cr-action-btn { transition: color 0.15s ease, background-color 0.15s ease; }
    .cr-action-btn:hover { background: #f1f5f9; }
    .cr-action-view mat-icon { color: #3b82f6; }
    .cr-action-breakup mat-icon { color: #8b5cf6; }
    .cr-action-edit mat-icon { color: #f59e0b; }
    .cr-action-save mat-icon { color: #16a34a; }
    .cr-action-cancel mat-icon { color: #ef4444; }
    .cr-action-delete mat-icon { color: #dc2626; }

    /* ── Inline Edit ── */
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
export class SmallClientEntryReportComponent implements OnInit {
  amountStatusOptions = ['Pending', 'Partial', 'Paid'];
  amountStatusSuggestions = ['Pending', 'Partial', 'Paid'];
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
  customerSuggestions: string[] = [];
  filteredCustomerSuggestions: string[] = [];

  filterForm!: FormGroup;
  data: any[] = [];
  loading = false;
  totalRecords = 0;
  pageSize = 10;
  pageIndex = 0;

  /** Row being edited (only one at a time). */
  editingItemId: string | null = null;
  editDraft = { amountStatus: 'Pending', receivedAmount: null as number | null, description: '' };

  get pageRevenue(): number { return this.data.reduce((s, r) => s + (r.totalAmount || 0), 0); }
  get pageReceived(): number {
    return this.data.reduce((s, r) => s + (r.receivedAmount != null ? r.receivedAmount : (r.amountStatus === 'Paid' ? (r.totalAmount || 0) : 0)), 0);
  }
  get pagePending(): number {
    return this.data.reduce((s, r) => s + (r.pendingAmount != null ? r.pendingAmount : (r.amountStatus === 'Paid' ? 0 : (r.totalAmount || 0))), 0);
  }

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private apiService: ApiService,
    private toastService: ToastService,
    private fb: FormBuilder,
    private router: Router,
    private invoicePreview: InvoicePreviewService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.filterForm = this.fb.group({
      customerName: [''],
      month: [''],
      year: [''],
      invoiceDateFrom: [null],
      invoiceDateTo: [null],
      amountStatus: [''],
      invoiceNumber: [''],
      description: ['']
    });
    this.loadCustomerSuggestions();
    this.loadData();
  }

  private toIsoDate(d: Date): string {
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().split('T')[0];
  }

  loadData() {
    this.cancelEdit();
    this.loading = true;
    const vals = this.filterForm.value;

    const params: Record<string, string | number> = {
      page: this.pageIndex,
      size: this.pageSize,
      sortBy: 'invoiceDate',
      sortDir: 'desc',
      invoiceGenerated: 'true'
    };

    const cn = (vals.customerName || '').trim();
    if (cn) params['customerName'] = cn;

    const month = Number(vals.month);
    if (Number.isInteger(month) && month >= 1 && month <= 12) params['invoiceMonth'] = month;

    const year = Number(vals.year);
    if (Number.isInteger(year) && year > 0) params['invoiceYear'] = year;

    const inv = (vals.invoiceNumber || '').trim();
    if (inv) params['invoiceNumber'] = inv;

    const desc = (vals.description || '').trim();
    if (desc) params['description'] = desc;

    const st = (vals.amountStatus || '').trim();
    if (st) params['amountStatus'] = st;

    if (vals.invoiceDateFrom) {
      params['invoiceDateFrom'] = this.toIsoDate(new Date(vals.invoiceDateFrom));
    }
    if (vals.invoiceDateTo) {
      params['invoiceDateTo'] = this.toIsoDate(new Date(vals.invoiceDateTo));
    }

    this.apiService.get<any>('/small-client-entries', params).subscribe({
      next: (res: any) => {
        const rows = res.content || [];
        this.data = rows.map((row: any) => ({
          ...row,
          amountStatus: row.amountStatus || 'Pending',
          description: row.description ?? ''
        }));
        this.totalRecords = res.totalElements || 0;
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load report data');
        this.loading = false;
      }
    });
  }

  onPageChange(event: any) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  resetFilters() {
    this.filterForm.reset({
      customerName: '',
      month: '',
      year: '',
      invoiceDateFrom: null,
      invoiceDateTo: null,
      amountStatus: '',
      invoiceNumber: '',
      description: ''
    });
    this.pageIndex = 0;
    if (this.paginator) this.paginator.pageIndex = 0;
    this.loadData();
  }

  onCustomerSearchChange(): void {
    const q = (this.filterForm.get('customerName')?.value || '').trim().toLowerCase();
    this.filteredCustomerSuggestions = !q
      ? [...this.customerSuggestions]
      : this.customerSuggestions.filter((name) => name.toLowerCase().includes(q));
  }

  private loadCustomerSuggestions(): void {
    const names = new Set<string>();
    this.apiService.get<any[]>('/small-clients/type/CUSTOMER').subscribe({
      next: (list) => {
        (list || []).forEach((c: any) => {
          if (c?.partyName) names.add(c.partyName);
        });
        this.customerSuggestions = Array.from(names).sort();
        this.filteredCustomerSuggestions = [...this.customerSuggestions];
      },
      error: () => {}
    });
    this.apiService.get<PageResponse<any>>('/collection-customers', { page: 0, size: 1000, sortBy: 'customerName', sortDir: 'asc' }).subscribe({
      next: (res) => {
        (res?.content || []).forEach((c: any) => {
          if (c?.customerName) names.add(c.customerName);
        });
        this.customerSuggestions = Array.from(names).sort();
        this.filteredCustomerSuggestions = [...this.customerSuggestions];
      },
      error: () => {}
    });
  }

  startEdit(item: any): void {
    if (this.editingItemId != null && this.editingItemId !== item.id) {
      this.toastService.warning('Edit in progress', 'Save or cancel the current row before editing another.');
      return;
    }
    const amt = item.totalAmount != null ? Number(item.totalAmount) : 0;
    const rec = item.receivedAmount != null ? Number(item.receivedAmount) : (item.amountStatus === 'Paid' ? amt : 0);
    this.editingItemId = item.id;
    this.editDraft = {
      amountStatus: (item.amountStatus ?? 'Pending').toString(),
      receivedAmount: rec,
      description: item.description ?? ''
    };
  }

  onDraftReceivedAmountChange(item: any): void {
    const amt = item?.totalAmount != null ? Number(item.totalAmount) : 0;
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
    const amt = item?.totalAmount != null ? Number(item.totalAmount) : 0;
    const rec = this.editDraft.receivedAmount != null ? Number(this.editDraft.receivedAmount) : 0;
    return Math.round(Math.max(0, amt - rec) * 100) / 100;
  }

  cancelEdit(): void {
    this.editingItemId = null;
    this.editDraft = { amountStatus: 'Pending', receivedAmount: null, description: '' };
  }

  openEntry(item: any): void {
    if (!item?.id || this.editingItemId) return;
    this.router.navigate(['/small-client-entries/edit', item.id]);
  }

  viewInvoice(item: any): void {
    this.invoicePreview.viewInvoice(item.id, item, true);
  }

  printInvoice(item: any): void {
    this.invoicePreview.printInvoice(item.id, item, true);
  }

  downloadInvoice(item: any): void {
    const ref = this.dialog.open(PdfConfirmDialogComponent, { width: '480px', maxWidth: '95vw' });
    ref.afterClosed().subscribe((includeBreakup) => {
      if (includeBreakup === undefined) return;
      this.invoicePreview.downloadInvoice(item.id, includeBreakup, item, true);
    });
  }

  viewBreakup(item: any): void {
    this.invoicePreview.viewBreakup(item.id, true);
  }

  printBreakup(item: any): void {
    this.invoicePreview.printBreakup(item.id, true);
  }

  downloadBreakup(item: any): void {
    this.invoicePreview.downloadBreakup(item.id, item, true);
  }

  saveEdit(item: any): void {
    if (this.editingItemId !== item.id) return;
    const amt = item.totalAmount != null ? Number(item.totalAmount) : 0;
    const rec = this.editDraft.receivedAmount;
    if (rec !== null && rec !== undefined && (rec as any) !== '') {
      const numRec = Number(rec);
      if (numRec < 0) {
        this.toastService.error('Validation Error', 'Received amount cannot be negative');
        return;
      }
      if (numRec > amt && amt > 0) {
        this.toastService.error('Validation Error', `Received amount (₹${numRec}) cannot exceed total amount (₹${amt})`);
        return;
      }
    }
    const statusRaw = (this.editDraft.amountStatus ?? '').toString().trim();
    const payload: Record<string, any> = {
      amountStatus: statusRaw || 'Pending',
      receivedAmount: rec !== null && rec !== undefined && (rec as any) !== '' ? Number(rec) : null,
      description: this.editDraft.description ?? ''
    };
    this.apiService.patch<any>(`/small-client-entries/${item.id}/status`, payload).subscribe({
      next: (updated: any) => {
        if (updated && item.id === updated.id) {
          item.amountStatus = updated.amountStatus ?? payload['amountStatus'];
          item.receivedAmount = updated.receivedAmount;
          item.pendingAmount = updated.pendingAmount;
          item.description = updated.description ?? '';
        }
        this.cancelEdit();
        this.toastService.success('Success', 'Saved successfully');
      },
      error: (err) => {
        console.error('Small Client Entry Report patch failed', err);
        this.toastService.error('Error', err?.error?.message || 'Failed to save');
      }
    });
  }

  deleteEntry(item: any): void {
    if (!item?.id) return;
    const details = item.invoiceNumber
      ? `Invoice: ${item.invoiceNumber} | Customer: ${item.customerName || '—'}`
      : `Customer: ${item.customerName || '—'}`;
    const ref = this.dialog.open(ReportConfirmDeleteDialogComponent, {
      width: '440px',
      maxWidth: '95vw',
      data: { details }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.apiService.delete('/small-client-entries/report', item.id).subscribe({
          next: () => {
            this.toastService.success('Success', 'Small Client Entry deleted successfully');
            this.loadData();
          },
          error: (err: any) => {
            this.toastService.error('Error', err?.error?.message || 'Failed to delete Small Client Entry');
          }
        });
      }
    });
  }
}

