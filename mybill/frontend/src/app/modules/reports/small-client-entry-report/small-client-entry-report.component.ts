import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { InvoicePreviewService } from '../../../core/services/invoice-preview.service';
import { MatDialog } from '@angular/material/dialog';
import { PdfConfirmDialogComponent } from '../../master/monthly-courier-quotation/monthly-courier-quotation-list/monthly-courier-quotation-list.component';

/**
 * Client Entry Report: monthly invoices that have been downloaded at least once.
 * Inline edits use Edit â†’ Save / Cancel only (no save on blur).
 */
@Component({
  selector: 'app-small-client-entry-report',
  template: `
    <div class="cr-container rpt-animate">
      <!-- Summary Cards -->
      <div class="cr-summary-row">
        <div class="cr-summary-card">
          <div class="cr-summary-icon">
            <mat-icon>receipt_long</mat-icon>
          </div>
          <div class="cr-summary-content">
            <span class="cr-summary-label">Total Records</span>
            <span class="cr-summary-value">{{ totalRecords | number }}</span>
          </div>
        </div>
        <div class="cr-summary-card cr-summary-card--revenue">
          <div class="cr-summary-icon cr-summary-icon--revenue">
            <mat-icon>currency_rupee</mat-icon>
          </div>
          <div class="cr-summary-content">
            <span class="cr-summary-label">Page Revenue</span>
            <span class="cr-summary-value">₹ {{ pageRevenue | number:'1.2-2' }}</span>
          </div>
        </div>
      </div>

      <!-- Filters Card -->
      <div class="cr-filters">
        <div class="cr-filters-header">
          <mat-icon class="cr-filters-header-icon">filter_list</mat-icon>
          <span class="cr-filters-title">Search &amp; Filters</span>
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
            <input matInput formControlName="amountStatus" [matAutocomplete]="filterStatusAuto" placeholder="Type or choose">
            <mat-autocomplete #filterStatusAuto="matAutocomplete">
              <mat-option *ngFor="let s of amountStatusSuggestions" [value]="s">{{ s }}</mat-option>
            </mat-autocomplete>
            <mat-hint>Optional; matches any case</mat-hint>
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
            <button mat-raised-button color="primary" type="button" (click)="loadData()">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button type="button" (click)="resetFilters()">
              <mat-icon>clear</mat-icon> Clear
            </button>
          </div>
        </form>
      </div>

      <!-- Data Table -->
      <div class="cr-table-container mat-elevation-z2">
        <div class="cr-loading-shade" *ngIf="loading">
          <mat-spinner></mat-spinner>
        </div>
        <div class="cr-table-scroll">
          <table class="cr-table">
            <thead>
              <tr>
                <th>Invoice Date</th>
                <th>Invoice No</th>
                <th>Customer Name</th>
                <th>Total Amount</th>
                <th>Amount Status</th>
                <th>Description</th>
                <th class="cr-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let item of data; let i = index" class="cr-row-click" [class.cr-row-zebra]="i % 2 === 1" (click)="openEntry(item)">
                <td>{{ item.invoiceDate | date:'yyyy-MM-dd' }}</td>
                <td class="cr-cell-mono">{{ item.invoiceNumber }}</td>
                <td class="cr-cell-name">{{ item.customerName }}</td>
                <td class="cr-cell-amount">₹ {{ item.totalAmount | number:'1.2-2' }}</td>
                <td>
                  <ng-container *ngIf="editingItemId !== item.id">{{ item.amountStatus }}</ng-container>
                  <mat-form-field *ngIf="editingItemId === item.id" appearance="outline" class="cr-edit-field" subscriptSizing="dynamic">
                    <input matInput [(ngModel)]="editDraft.amountStatus" [matAutocomplete]="rowStatusAuto"
                           [ngModelOptions]="{standalone: true}" placeholder="Pick or type">
                  </mat-form-field>
                </td>
                <td>
                  <ng-container *ngIf="editingItemId !== item.id">
                    <span class="cr-desc-readonly" [title]="item.description">{{ item.description || '—' }}</span>
                  </ng-container>
                  <input *ngIf="editingItemId === item.id" class="cr-inline-input" [(ngModel)]="editDraft.description"
                         [ngModelOptions]="{standalone: true}" placeholder="Description">
                </td>
                <td class="cr-actions-cell" (click)="$event.stopPropagation()">
                  <ng-container *ngIf="editingItemId !== item.id">
                    <button mat-icon-button type="button" (click)="viewInvoice(item)" matTooltip="View invoice">
                      <mat-icon>visibility</mat-icon>
                    </button>
                    <button mat-icon-button type="button" (click)="viewBreakup(item)" matTooltip="View breakup">
                      <mat-icon>table_chart</mat-icon>
                    </button>
                    <button mat-icon-button type="button" [matMenuTriggerFor]="docMenu" matTooltip="More">
                      <mat-icon>more_vert</mat-icon>
                    </button>
                    <mat-menu #docMenu="matMenu">
                      <button mat-menu-item type="button" (click)="printInvoice(item)"><mat-icon>print</mat-icon> Print invoice</button>
                      <button mat-menu-item type="button" (click)="downloadInvoice(item)"><mat-icon>download</mat-icon> Download invoice PDF</button>
                      <button mat-menu-item type="button" (click)="printBreakup(item)"><mat-icon>print</mat-icon> Print breakup</button>
                      <button mat-menu-item type="button" (click)="downloadBreakup(item)"><mat-icon>download</mat-icon> Download breakup PDF</button>
                    </mat-menu>
                    <button mat-icon-button type="button" (click)="startEdit(item)" matTooltip="Edit status">
                      <mat-icon>edit</mat-icon>
                    </button>
                  </ng-container>
                  <ng-container *ngIf="editingItemId === item.id">
                    <button mat-icon-button type="button" color="primary" (click)="saveEdit(item)" matTooltip="Save">
                      <mat-icon>check</mat-icon>
                    </button>
                    <button mat-icon-button type="button" (click)="cancelEdit()" matTooltip="Cancel">
                      <mat-icon>close</mat-icon>
                    </button>
                  </ng-container>
                </td>
              </tr>
              <tr *ngIf="data.length === 0 && !loading">
                <td colspan="7" class="cr-empty-row">No records found.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="totalRecords" [pageSize]="pageSize" [pageSizeOptions]="[10, 20, 50]" (page)="onPageChange($event)"></mat-paginator>
      </div>

      <mat-autocomplete #rowStatusAuto="matAutocomplete">
        <mat-option *ngFor="let s of amountStatusSuggestions" [value]="s">{{ s }}</mat-option>
      </mat-autocomplete>
    </div>
  `,
  styles: [`
    .cr-container { width: 100%; box-sizing: border-box; padding: 0 4px; }

    /* Summary Cards */
    .cr-summary-row { display: flex; gap: 20px; margin-bottom: 20px; flex-wrap: wrap; }
    .cr-summary-card {
      display: flex; align-items: center; gap: 14px;
      background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px;
      padding: 18px 22px; min-width: 200px; flex: 1;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      transition: box-shadow 0.2s, transform 0.2s;
    }
    .cr-summary-card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.07); transform: translateY(-1px); }
    .cr-summary-card--revenue { border-left: 3px solid #10b981; }
    .cr-summary-icon {
      display: flex; align-items: center; justify-content: center;
      width: 42px; height: 42px; border-radius: 10px;
      background: #eff6ff; color: #3b82f6;
    }
    .cr-summary-icon--revenue { background: #ecfdf5; color: #10b981; }
    .cr-summary-content { display: flex; flex-direction: column; gap: 2px; }
    .cr-summary-label { font-size: 12px; font-weight: 500; color: #64748b; text-transform: uppercase; letter-spacing: 0.4px; }
    .cr-summary-value { font-size: 20px; font-weight: 700; color: #0f172a; }

    /* Filters Card */
    .cr-filters {
      margin-bottom: 20px; background: #ffffff; padding: 20px 24px;
      border-radius: 10px; border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }
    .cr-filters-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    .cr-filters-header-icon { color: #64748b; font-size: 20px; width: 20px; height: 20px; }
    .cr-filters-title { font-size: 14px; font-weight: 600; color: #0f172a; }
    .cr-filter-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 16px; align-items: start; }
    .cr-filter-item { margin-bottom: -1.25em; }
    .cr-span-2 { grid-column: span 2; }
    .cr-actions {
      grid-column: 1 / -1; display: flex; gap: 12px;
      justify-content: flex-end; padding-top: 8px;
      border-top: 1px solid #f1f5f9; margin-top: 4px;
    }

    /* Table Container */
    .cr-table-container {
      position: relative; background: #ffffff; border-radius: 10px;
      border: 1px solid #e2e8f0; overflow: hidden;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }
    .cr-table-scroll { overflow-x: auto; }
    .cr-loading-shade {
      position: absolute; top: 0; left: 0; bottom: 0; right: 0;
      background: rgba(255, 255, 255, 0.8); display: flex;
      align-items: center; justify-content: center; z-index: 10;
      backdrop-filter: blur(2px);
    }

    /* Table */
    .cr-table {
      width: 100%; border-collapse: collapse;
      font-family: 'Inter', 'Helvetica Neue', Arial, sans-serif;
      font-size: 13px; color: #1f2937;
    }
    .cr-table thead { position: sticky; top: 0; z-index: 5; }
    .cr-table th {
      padding: 12px 14px; text-align: left;
      background: linear-gradient(180deg, #f8fafc, #f1f5f9);
      border-bottom: 2px solid #e2e8f0;
      font-weight: 600; font-size: 12px; color: #0f172a;
      text-transform: uppercase; letter-spacing: 0.3px;
      white-space: nowrap;
    }
    .cr-th-actions { text-align: center; width: 200px; }
    .cr-table td {
      padding: 10px 14px; border-bottom: 1px solid #e2e8f0;
      vertical-align: middle; color: #1f2937;
    }

    /* Row States */
    .cr-row-click { cursor: pointer; transition: background-color 0.15s; }
    .cr-row-click:hover { background: #eff6ff !important; }
    .cr-row-zebra { background: #fafbfc; }

    /* Cell Variants */
    .cr-cell-mono { font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; color: #475569; }
    .cr-cell-name { font-weight: 500; color: #0f172a; }
    .cr-cell-amount { font-weight: 600; font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px; color: #0f172a; white-space: nowrap; }

    /* Inline Edit */
    .cr-desc-readonly {
      display: inline-block; max-width: 280px; overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap; vertical-align: middle;
    }
    .cr-actions-cell { text-align: center; white-space: nowrap; }
    .cr-edit-field { width: 100%; min-width: 160px; max-width: 260px; margin-bottom: 0 !important; }
    .cr-edit-field .mat-mdc-form-field-subscript-wrapper { display: none; }
    .cr-inline-input {
      width: 100%; max-width: 320px; padding: 8px 10px;
      border: 1px solid #cbd5e1; border-radius: 6px;
      box-sizing: border-box; font-size: 13px;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    .cr-inline-input:focus { border-color: #3b82f6; outline: none; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }

    /* Empty State */
    .cr-empty-row { text-align: center; padding: 40px 20px !important; color: #64748b; font-style: italic; }

    /* Responsive */
    @media (max-width: 768px) {
      .cr-summary-row { flex-direction: column; }
      .cr-filter-grid { grid-template-columns: 1fr; }
      .cr-span-2 { grid-column: span 1; }
      .cr-summary-card { min-width: unset; }
    }
  `]
})
export class SmallClientEntryReportComponent implements OnInit {
  /** Default dropdown options; user can still type any value in the autocomplete input. */
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
  editDraft = { amountStatus: '', description: '' };

  get pageRevenue(): number { return this.data.reduce((s, r) => s + (r.totalAmount || 0), 0); }

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
      isDownloaded: 'true',
      page: this.pageIndex,
      size: this.pageSize,
      sortBy: 'invoiceDate',
      sortDir: 'desc'
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
    this.editingItemId = item.id;
    this.editDraft = {
      amountStatus: (item.amountStatus ?? 'Pending').toString(),
      description: item.description ?? ''
    };
  }

  cancelEdit(): void {
    this.editingItemId = null;
    this.editDraft = { amountStatus: '', description: '' };
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
    const statusRaw = (this.editDraft.amountStatus ?? '').toString().trim();
    const payload: Record<string, string> = {
      amountStatus: statusRaw || 'Pending',
      description: this.editDraft.description ?? ''
    };
    this.apiService.patch<any>(`/small-client-entries/${item.id}/status`, payload).subscribe({
      next: (updated: any) => {
        if (updated && item.id === updated.id) {
          item.amountStatus = updated.amountStatus ?? payload['amountStatus'];
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
}

