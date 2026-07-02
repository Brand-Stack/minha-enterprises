import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { UiConfigService } from '../../../../core/services/ui-config.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import { buildYearOptions, MONTH_NAMES } from '../../../../core/utils/month-year.util';
import {
  isoDateString,
  loadListFilterState,
  parseIsoDate,
  saveListFilterState
} from '../../../../core/utils/list-filter-state.util';

const COLLECTION_LIST_FILTER_KEY = 'collection_center_list_filters';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

interface CollectionCustomerOption {
  id: string;
  customerName: string;
}

interface PendingPreviewRow {
  id?: string;
  awbNo?: string;
  status?: string;
  createdAt?: string;
}

interface ClearPendingPreviewResponse {
  collectionCustomerId?: string;
  customerName?: string;
  totalAwbs?: number;
  pendingAwbs?: number;
  pendingPreview?: PendingPreviewRow[];
}

@Component({
  selector: 'app-collection-center-list',
  template: `
    <div class="ccl-page">
      <div class="ccl-head">
        <div>
          <h1>Collection Center</h1>
          <p class="ccl-sub">AWB lines per collection customer — unique AWB across Client Entry &amp; here.</p>
        </div>
        <div class="ccl-head-actions">
          <button mat-stroked-button type="button" (click)="togglePendingAwbs()"><mat-icon>pending_actions</mat-icon> Pending AWBs</button>
          <button mat-stroked-button type="button" color="warn" (click)="clearPendingAwbs()" *appHasPermission="'COLLECTION_CENTER:delete'"><mat-icon>cleaning_services</mat-icon> Clear All Pending AWBs</button>
          <button mat-raised-button color="primary" (click)="add()" *appHasPermission="'COLLECTION_CENTER:create'"><mat-icon>add</mat-icon> Add entry</button>
        </div>
      </div>
      <mat-card class="ccl-filters">
        <div class="ccl-grid">
          <mat-form-field appearance="outline"><mat-label>Collection customer</mat-label>
            <mat-select [(ngModel)]="collectionCustomerId">
              <mat-option value="">All</mat-option>
              <mat-option *ngFor="let c of customers" [value]="c.id">{{ c.customerName }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>AWB</mat-label><input matInput [(ngModel)]="awbNo"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>From</mat-label>
            <input matInput [matDatepicker]="df" [(ngModel)]="dateFrom"><mat-datepicker-toggle matSuffix [for]="df"></mat-datepicker-toggle><mat-datepicker #df></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>To</mat-label>
            <input matInput [matDatepicker]="dt" [(ngModel)]="dateTo"><mat-datepicker-toggle matSuffix [for]="dt"></mat-datepicker-toggle><mat-datepicker #dt></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Status</mat-label>
            <mat-select [(ngModel)]="status"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let s of statusOptions" [value]="s">{{ s }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Amount status</mat-label>
            <mat-select [(ngModel)]="amountStatus"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let s of amountStatusOptions" [value]="s">{{ s }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Pincode</mat-label><input matInput [(ngModel)]="pincode"></mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Courier</mat-label>
            <mat-select [(ngModel)]="courier"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let c of courierOptions" [value]="c">{{ c }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Month</mat-label>
            <mat-select [(ngModel)]="entryMonth"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let m of monthNames" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Year</mat-label>
            <mat-select [(ngModel)]="entryYear"><mat-option [value]="null">All</mat-option>
              <mat-option *ngFor="let y of yearOptions" [value]="y">{{ y }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Sort by</mat-label>
            <mat-select [(ngModel)]="sortBy">
              <mat-option value="entryDate">Date</mat-option>
              <mat-option value="customerName">Customer</mat-option>
              <mat-option value="awbNo">AWB</mat-option>
              <mat-option value="amount">Amount</mat-option>
              <mat-option value="status">Status</mat-option>
              <mat-option value="createdAt">Created</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline"><mat-label>Order</mat-label>
            <mat-select [(ngModel)]="sortDir">
              <mat-option value="desc">Descending</mat-option>
              <mat-option value="asc">Ascending</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div class="ccl-actions">
          <button mat-stroked-button type="button" (click)="reset()">Reset</button>
          <button mat-raised-button color="primary" type="button" (click)="load(0)"><mat-icon>search</mat-icon> Search</button>
        </div>
      </mat-card>
      <mat-card>
        <p class="ccl-total" *ngIf="rows.length || total > 0">
          Total Records: {{ total }} | Total Amount: ₹ {{ filteredTotalAmount | number:'1.2-2' }} | Page {{ page + 1 }} of {{ totalPages || 1 }}
        </p>
        <div class="ccl-scroll">
          <table class="ccl-table">
            <thead>
              <tr>
                <th>#</th><th>Date</th><th>Customer</th><th>AWB</th><th>Receiver</th><th>Pincode</th><th>Courier</th><th>Wt</th><th>Amount</th><th>Amt st.</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of rows; let i = index">
                <td>{{ page * size + i + 1 }}</td>
                <td>{{ r.entryDate | date:'yyyy-MM-dd' }}</td>
                <td>{{ r.customerName }}</td>
                <td>{{ r.awbNo || '—' }}</td>
                <td>{{ r.receiverName || '—' }}</td>
                <td>{{ r.pincode || '—' }}</td>
                <td>{{ r.courier || '—' }}</td>
                <td>{{ r.weight != null ? r.weight : '—' }}</td>
                <td>{{ r.amount != null ? (r.amount | number:'1.2-2') : '—' }}</td>
                <td><span class="ccl-badge">{{ r.amountStatus || '—' }}</span></td>
                <td>{{ r.status || '—' }}</td>
                <td><button mat-icon-button (click)="edit(r.id)" [appDisableIfNoPermission]="'COLLECTION_CENTER:edit'"><mat-icon>edit</mat-icon></button>
                    <button mat-icon-button color="warn" (click)="del(r.id)" *appHasPermission="'COLLECTION_CENTER:delete'"><mat-icon>delete</mat-icon></button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="total" [pageIndex]="page" [pageSize]="size" [pageSizeOptions]="[10,25,50,100]"
          showFirstLastButtons (page)="onPage($event)"></mat-paginator>
      </mat-card>
      <mat-card class="ccl-pending" *ngIf="pendingOpen">
        <div class="ccl-pending-head">
          <h3>Pending AWBs (registry)</h3>
          <button mat-icon-button (click)="pendingOpen=false"><mat-icon>close</mat-icon></button>
        </div>
        <p class="ccl-sub" *ngIf="!pendingRows.length">No pending AWBs for the current filters.</p>
        <table class="ccl-table" *ngIf="pendingRows.length">
          <thead><tr><th>Customer</th><th>AWB</th><th>Registered</th></tr></thead>
          <tbody>
            <tr *ngFor="let p of pendingRows">
              <td>{{ p.customerName }}</td>
              <td>{{ p.awbNo }}</td>
              <td>{{ p.createdAt | date:'yyyy-MM-dd HH:mm' }}</td>
            </tr>
          </tbody>
        </table>
      </mat-card>
    </div>
  `,
  styles: [`
    .ccl-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .ccl-head-actions { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
    .ccl-pending { margin-top: 16px; padding: 16px; }
    .ccl-pending-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .ccl-pending-head h3 { margin: 0; font-size: 1rem; }
    .ccl-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
    .ccl-head h1 { margin: 0; font-size: 1.5rem; font-weight: 700; }
    .ccl-sub { color: #64748b; margin: 4px 0 0; font-size: 0.9rem; }
    .ccl-filters { margin-bottom: 16px; padding: 16px; border-radius: 12px; }
    .ccl-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
    .ccl-actions { display: flex; gap: 12px; margin-top: 12px; }
    .ccl-scroll { overflow: auto; max-height: 560px; border: 1px solid #e5e7eb; border-radius: 8px; }
    .ccl-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .ccl-table th { position: sticky; top: 0; background: #f8fafc; z-index: 1; text-align: left; padding: 10px 8px; border-bottom: 1px solid #e5e7eb; white-space: nowrap; }
    .ccl-table td { padding: 8px; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }
    .ccl-badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: #ecfdf5; color: #047857; }
    .ccl-total { font-size: 13px; color: #475569; margin: 0 0 10px; }
  `]
})
export class CollectionCenterListComponent implements OnInit {
  customers: { id: string; customerName: string }[] = [];
  rows: any[] = [];
  total = 0;
  totals: { totalRecords: number; totalAmount: number } | null = null;
  totalPages = 0;
  page = 0;
  size = 25;
  sortBy = 'entryDate';
  sortDir = 'desc';

  monthNames = MONTH_NAMES;
  yearOptions: number[] = [];
  entryMonth = '';
  entryYear: number | null = null;

  pendingOpen = false;
  pendingRows: any[] = [];

  collectionCustomerId = '';
  awbNo = '';
  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  status = '';
  amountStatus = '';
  pincode = '';
  courier = '';
  amountStatusOptions = ['Cash', 'GPay', 'Pending', 'COD', 'Paid', 'UnPaid'];
  courierOptions: string[] = [];
  statusOptions: string[] = [];

  constructor(
    private api: ApiService,
    private router: Router,
    private route: ActivatedRoute,
    private toast: ToastService,
    private uiConfig: UiConfigService,
    private companyLists: CompanySettingsListsService,
    private dialog: MatDialog
  ) {}

  ngOnInit() {
    this.uiConfig.getUi().subscribe({
      next: (u) => (this.yearOptions = buildYearOptions(u.collectionYearRangePast, u.collectionYearRangeFuture)),
      error: () => (this.yearOptions = buildYearOptions(5, 5))
    });
    this.api.get<PageResponse<any>>('/collection-customers', { page: 0, size: 500, sortBy: 'customerName', sortDir: 'asc' }).subscribe({
      next: (res) => {
        this.customers = (res.content || []).map((c: any) => ({ id: c.id, customerName: c.customerName }));
      },
      error: () => {}
    });
    this.companyLists.getLists().subscribe((lists) => {
      this.courierOptions = lists.couriers || [];
      this.statusOptions = lists.statuses || [];
    });
    this.restoreFilters();
    this.route.queryParams.subscribe((q) => {
      if (q['customerId']) {
        this.collectionCustomerId = q['customerId'];
      }
      this.load(this.page);
    });
  }

  private persistFilters(): void {
    saveListFilterState(COLLECTION_LIST_FILTER_KEY, {
      collectionCustomerId: this.collectionCustomerId,
      awbNo: this.awbNo,
      dateFrom: isoDateString(this.dateFrom),
      dateTo: isoDateString(this.dateTo),
      status: this.status,
      amountStatus: this.amountStatus,
      pincode: this.pincode,
      courier: this.courier,
      entryMonth: this.entryMonth,
      entryYear: this.entryYear,
      page: this.page,
      size: this.size,
      sortBy: this.sortBy,
      sortDir: this.sortDir
    });
  }

  private restoreFilters(): void {
    const saved = loadListFilterState<Record<string, unknown>>(COLLECTION_LIST_FILTER_KEY);
    if (!saved) return;
    if (saved['collectionCustomerId'] != null) this.collectionCustomerId = String(saved['collectionCustomerId']);
    if (saved['awbNo'] != null) this.awbNo = String(saved['awbNo']);
    this.dateFrom = parseIsoDate(saved['dateFrom']);
    this.dateTo = parseIsoDate(saved['dateTo']);
    if (saved['status'] != null) this.status = String(saved['status']);
    if (saved['amountStatus'] != null) this.amountStatus = String(saved['amountStatus']);
    if (saved['pincode'] != null) this.pincode = String(saved['pincode']);
    if (saved['courier'] != null) this.courier = String(saved['courier']);
    if (saved['entryMonth'] != null) this.entryMonth = String(saved['entryMonth']);
    if (saved['entryYear'] != null && saved['entryYear'] !== '') this.entryYear = Number(saved['entryYear']);
    if (typeof saved['page'] === 'number') this.page = saved['page'];
    if (typeof saved['size'] === 'number') this.size = saved['size'];
    if (saved['sortBy']) this.sortBy = String(saved['sortBy']);
    if (saved['sortDir']) this.sortDir = String(saved['sortDir']);
  }

  private iso(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  load(pageIdx: number) {
    this.page = pageIdx;
    this.persistFilters();
    const p = this.currentParams();
    this.api.get<PageResponse<any>>('/collection-center/entries', p).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.total = res?.totalElements ?? 0;
        this.totalPages = res?.totalPages ?? 0;
        this.loadTotals();
      },
      error: () => this.toast.error('Error', 'Failed to load entries')
    });
  }

  get filteredTotalAmount(): number {
    return Number(this.totals?.totalAmount ?? 0);
  }

  private totalParams(): Record<string, string | number> {
    const p = { ...this.currentParams() };
    delete p['page'];
    delete p['size'];
    delete p['sortBy'];
    delete p['sortDir'];
    return p;
  }

  private currentParams(): Record<string, string | number> {
    const p: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.collectionCustomerId) p['collectionCustomerId'] = this.collectionCustomerId;
    if (this.awbNo.trim()) p['awbNo'] = this.awbNo.trim();
    const df = this.iso(this.dateFrom);
    const dt = this.iso(this.dateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;
    if (this.status.trim()) p['status'] = this.status.trim();
    if (this.amountStatus) p['amountStatus'] = this.amountStatus;
    if (this.pincode.trim()) p['pincode'] = this.pincode.trim();
    if (this.courier.trim()) p['courier'] = this.courier.trim();
    if (this.entryMonth) p['entryMonth'] = this.entryMonth;
    if (this.entryYear != null) p['entryYear'] = this.entryYear;
    p['sortBy'] = this.sortBy;
    p['sortDir'] = this.sortDir;
    return p;
  }

  loadTotals(): void {
    this.api.get<{ totalRecords: number; totalAmount: number }>('/collection-center/entries/report-totals', this.totalParams()).subscribe({
      next: (t) => (this.totals = t),
      error: () => (this.totals = null)
    });
  }

  onPage(e: any) {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  reset() {
    this.collectionCustomerId = '';
    this.awbNo = '';
    this.dateFrom = null;
    this.dateTo = null;
    this.status = '';
    this.amountStatus = '';
    this.pincode = '';
    this.courier = '';
    this.entryMonth = '';
    this.entryYear = null;
    this.load(0);
  }

  togglePendingAwbs(): void {
    this.pendingOpen = !this.pendingOpen;
    if (!this.pendingOpen) {
      return;
    }
    this.loadPendingRegistryRows();
  }

  private loadPendingRegistryRows(): void {
    const p: Record<string, string | number> = {};
    if (this.collectionCustomerId) {
      p['collectionCustomerId'] = this.collectionCustomerId;
    }
    if (this.entryYear != null) {
      p['calendarYear'] = this.entryYear;
    }
    if (this.entryMonth) {
      const idx = MONTH_NAMES.indexOf(this.entryMonth);
      if (idx >= 0) {
        p['calendarMonth'] = idx + 1;
      }
    }
    this.api.get<any[]>('/collection-center/registry/pending', p).subscribe({
      next: (rows) => (this.pendingRows = rows || []),
      error: () => {
        this.pendingRows = [];
        this.toast.error('Error', 'Could not load pending AWBs');
      }
    });
  }

  clearPendingAwbs(): void {
    const ok = confirm(
      'Clear all pending AWBs from Collection Customer registry, AWB Center, and pending queues?\n\nCompleted and used AWBs will NOT be removed.'
    );
    if (!ok) {
      return;
    }
    this.api.post<any>('/collection-customers/clear-pending-awbs', {}).subscribe({
      next: (res) => {
        const deletedCount = Number(res?.deletedCount || 0);
        if (deletedCount <= 0) {
          this.toast.info('Clear Pending AWBs', 'No pending AWBs to clear');
        } else {
          const msg = res?.message || `${deletedCount} pending AWBs cleared successfully`;
          this.toast.success('Success', msg);
        }
        this.load(this.page);
        if (this.pendingOpen) {
          this.loadPendingRegistryRows();
        }
      },
      error: (e) => {
        const msg = e?.error?.message || e?.error?.error || 'Could not clear pending AWBs';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Could not clear pending AWBs');
      }
    });
  }

  add() {
    this.persistFilters();
    const q = this.collectionCustomerId ? { customerId: this.collectionCustomerId } : {};
    this.router.navigate(['/client-entries/collection-center/create'], { queryParams: q });
  }

  edit(id: string) {
    this.persistFilters();
    this.router.navigate(['/client-entries/collection-center/edit', id]);
  }

  del(id: string) {
    if (!confirm('Delete this entry?')) return;
    this.api.delete('/collection-center/entries', id).subscribe({
      next: () => { this.toast.success('Deleted', ''); this.load(this.page); },
      error: () => this.toast.error('Error', 'Delete failed')
    });
  }
}

@Component({
  selector: 'app-clear-pending-awbs-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>Clear Pending AWBs</h2>
    <mat-dialog-content class="ccl-clear-dialog">
      <p class="ccl-clear-dialog__hint">Select Collection Customer</p>

      <mat-form-field appearance="outline" class="ccl-clear-dialog__search">
        <mat-label>Collection Customer</mat-label>
        <input
          matInput
          [(ngModel)]="customerSearch"
          (ngModelChange)="onSearchChange($event)"
          placeholder="Search customer name" />
      </mat-form-field>

      <div class="ccl-clear-dialog__selected" *ngIf="selectedCustomerName">
        Selected: <strong>{{ selectedCustomerName }}</strong>
      </div>

      <p class="ccl-clear-dialog__error" *ngIf="validationError">{{ validationError }}</p>

      <div class="ccl-clear-dialog__list" *ngIf="filteredCustomers.length">
        <button
          type="button"
          class="ccl-clear-dialog__list-item"
          [class.active]="c.id === selectedCustomerId"
          *ngFor="let c of filteredCustomers"
          (click)="selectCustomer(c)">
          <span>{{ c.customerName }}</span>
          <mat-icon *ngIf="c.id === selectedCustomerId">check_circle</mat-icon>
        </button>
      </div>
      <div class="ccl-clear-dialog__empty" *ngIf="!loadingCustomers && !filteredCustomers.length">
        No collection customers found.
      </div>

      <div class="ccl-clear-dialog__loading" *ngIf="loadingCustomers || loadingPreview">
        <mat-spinner diameter="28"></mat-spinner>
      </div>

      <div class="ccl-clear-dialog__summary" *ngIf="preview && !loadingPreview">
        <div>Total AWBs: <strong>{{ preview.totalAwbs || 0 }}</strong></div>
        <div>Pending AWBs: <strong>{{ preview.pendingAwbs || 0 }}</strong></div>
      </div>

      <div class="ccl-clear-dialog__preview-wrap" *ngIf="previewRows.length">
        <table class="ccl-clear-dialog__preview-table">
          <thead>
            <tr>
              <th>AWB No</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let r of previewRows">
              <td>{{ r.awbNo || '—' }}</td>
              <td>{{ r.status || 'PENDING' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="close()" [disabled]="clearing">Cancel</button>
      <button
        mat-flat-button
        color="warn"
        type="button"
        (click)="clearPendingAwbsForSelected()"
        [disabled]="!selectedCustomerId || clearing || loadingPreview">
        <mat-spinner *ngIf="clearing" diameter="18"></mat-spinner>
        <span *ngIf="!clearing">Clear Pending AWBs</span>
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .ccl-clear-dialog { min-width: 620px; max-width: 100%; }
    .ccl-clear-dialog__hint { margin: 0 0 8px; color: #475569; font-size: 13px; }
    .ccl-clear-dialog__search { width: 100%; }
    .ccl-clear-dialog__selected { margin: 0 0 10px; font-size: 13px; color: #1e293b; }
    .ccl-clear-dialog__error { margin: 0 0 10px; color: #b91c1c; font-size: 13px; }
    .ccl-clear-dialog__list { max-height: 180px; overflow: auto; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 12px; }
    .ccl-clear-dialog__list-item {
      width: 100%;
      text-align: left;
      border: none;
      background: #fff;
      border-bottom: 1px solid #f1f5f9;
      padding: 10px 12px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      font-size: 13px;
    }
    .ccl-clear-dialog__list-item:hover { background: #f8fafc; }
    .ccl-clear-dialog__list-item.active { background: #eff6ff; }
    .ccl-clear-dialog__empty { color: #64748b; font-size: 13px; margin-bottom: 12px; }
    .ccl-clear-dialog__loading { display: flex; justify-content: center; align-items: center; padding: 12px 0; }
    .ccl-clear-dialog__summary {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 10px 12px;
      margin-bottom: 10px;
      font-size: 13px;
      color: #0f172a;
    }
    .ccl-clear-dialog__preview-wrap { max-height: 220px; overflow: auto; border: 1px solid #e2e8f0; border-radius: 8px; }
    .ccl-clear-dialog__preview-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .ccl-clear-dialog__preview-table th,
    .ccl-clear-dialog__preview-table td { padding: 8px 10px; border-bottom: 1px solid #eef2ff; text-align: left; }
    .ccl-clear-dialog__preview-table th { position: sticky; top: 0; background: #f8fafc; z-index: 1; }
    @media (max-width: 700px) {
      .ccl-clear-dialog { min-width: 0; }
    }
  `]
})
export class ClearPendingAwbsDialogComponent implements OnInit {
  customers: CollectionCustomerOption[] = [];
  filteredCustomers: CollectionCustomerOption[] = [];
  customerSearch = '';
  selectedCustomerId = '';
  selectedCustomerName = '';
  loadingCustomers = false;
  loadingPreview = false;
  clearing = false;
  validationError = '';
  preview: ClearPendingPreviewResponse | null = null;

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private dialog: MatDialog,
    private dialogRef: MatDialogRef<ClearPendingAwbsDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { preselectedCustomerId?: string }
  ) {}

  ngOnInit(): void {
    this.loadCustomers();
  }

  get previewRows(): PendingPreviewRow[] {
    return this.preview?.pendingPreview || [];
  }

  onSearchChange(value: string): void {
    this.customerSearch = value || '';
    const q = this.customerSearch.trim().toLowerCase();
    this.filteredCustomers = !q
      ? [...this.customers]
      : this.customers.filter((c) => c.customerName.toLowerCase().includes(q));
  }

  selectCustomer(c: CollectionCustomerOption): void {
    this.selectedCustomerId = c.id;
    this.selectedCustomerName = c.customerName;
    this.customerSearch = c.customerName;
    this.validationError = '';
    this.preview = null;
    this.filteredCustomers = [...this.customers];
    this.loadPreview();
  }

  private loadCustomers(): void {
    this.loadingCustomers = true;
    this.api.get<PageResponse<any>>('/collection-customers', { page: 0, size: 1000, sortBy: 'customerName', sortDir: 'asc' }).subscribe({
      next: (res) => {
        this.loadingCustomers = false;
        this.customers = (res?.content || []).map((c: any) => ({ id: c.id, customerName: c.customerName }));
        this.filteredCustomers = [...this.customers];
        if (this.data?.preselectedCustomerId) {
          const hit = this.customers.find((c) => c.id === this.data.preselectedCustomerId);
          if (hit) {
            this.selectCustomer(hit);
          }
        }
      },
      error: () => {
        this.loadingCustomers = false;
        this.customers = [];
        this.filteredCustomers = [];
        this.toast.error('Error', 'Could not load collection customers');
      }
    });
  }

  private loadPreview(): void {
    if (!this.selectedCustomerId) {
      return;
    }
    this.loadingPreview = true;
    this.api.get<ClearPendingPreviewResponse>(`/collection-customers/${this.selectedCustomerId}/clear-pending-awbs/preview`, { limit: 10 }).subscribe({
      next: (res) => {
        this.loadingPreview = false;
        this.preview = res || null;
      },
      error: (e) => {
        this.loadingPreview = false;
        this.preview = null;
        const msg = e?.error?.message || e?.error?.error || 'Could not load pending AWB preview';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Could not load pending AWB preview');
      }
    });
  }

  clearPendingAwbsForSelected(): void {
    if (!this.selectedCustomerId) {
      this.validationError = 'Please select a collection customer';
      return;
    }
    if ((this.preview?.pendingAwbs || 0) === 0) {
      this.toast.info('Clear Pending AWBs', 'No pending AWBs to clear');
      return;
    }
    const ref = this.dialog.open(ClearPendingAwbsConfirmDialogComponent, {
      width: '440px',
      maxWidth: '95vw',
      data: { customerName: this.selectedCustomerName }
    });
    ref.afterClosed().subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.executeClear();
    });
  }

  private executeClear(): void {
    this.clearing = true;
    this.api.post<any>(`/collection-customers/${this.selectedCustomerId}/clear-pending-awbs`, {}).subscribe({
      next: (res) => {
        this.clearing = false;
        const deletedCount = Number(res?.deletedCount || 0);
        if (deletedCount <= 0) {
          this.toast.info('Clear Pending AWBs', 'No pending AWBs to clear');
          this.loadPreview();
          return;
        }
        this.dialogRef.close({
          cleared: true,
          customerId: this.selectedCustomerId,
          deletedCount,
          message: res?.message || `${deletedCount} pending AWBs cleared successfully`
        });
      },
      error: (e) => {
        this.clearing = false;
        const msg = e?.error?.message || e?.error?.error || 'Could not clear pending AWBs';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Could not clear pending AWBs');
      }
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}

@Component({
  selector: 'app-clear-pending-awbs-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Confirm</h2>
    <mat-dialog-content>
      Are you sure you want to clear all pending AWBs for this customer?
      <div *ngIf="data?.customerName" style="margin-top: 8px; color: #475569;">
        Customer: <strong>{{ data.customerName }}</strong>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="true">Confirm</button>
    </mat-dialog-actions>
  `
})
export class ClearPendingAwbsConfirmDialogComponent {
  constructor(@Inject(MAT_DIALOG_DATA) public data: { customerName?: string }) {}
}
