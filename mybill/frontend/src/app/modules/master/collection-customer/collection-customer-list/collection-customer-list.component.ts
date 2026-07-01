import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { UiConfigService } from '../../../../core/services/ui-config.service';
import { buildYearOptions, MONTH_NAMES } from '../../../../core/utils/month-year.util';

export interface CollectionCustomer {
  id: string;
  customerCode: string;
  customerName: string;
  contactPerson: string;
  email: string;
  phone: string;
  city: string;
  state: string;
  pincode: string;
}

@Component({
  selector: 'app-collection-customer-list',
  template: `
    <div class="cc-page">
      <div class="cc-head">
        <div>
          <h1>Collection Customer</h1>
          <p class="cc-sub">Master list — register AWBs on each customer (edit screen). Collection Center uses those numbers for entries.</p>
        </div>
        <button *appHasPermission="'COLLECTION_CUSTOMER:create'" mat-raised-button color="primary" (click)="goCreate()"><mat-icon>add</mat-icon> Add</button>
      </div>

      <mat-card class="cc-card">
        <mat-expansion-panel [expanded]="filtersOpen">
          <mat-expansion-panel-header>
            <mat-panel-title>Search collection entries (AWB / date / status…)</mat-panel-title>
          </mat-expansion-panel-header>
          <div class="cc-filter-grid">
            <mat-form-field appearance="outline"><mat-label>Customer name</mat-label>
              <input matInput [(ngModel)]="fCustomerName"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>AWB</mat-label>
              <input matInput [(ngModel)]="fAwb"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>From</mat-label>
              <input matInput [matDatepicker]="df" [(ngModel)]="fFrom"><mat-datepicker-toggle matSuffix [for]="df"></mat-datepicker-toggle><mat-datepicker #df></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>To</mat-label>
              <input matInput [matDatepicker]="dt" [(ngModel)]="fTo"><mat-datepicker-toggle matSuffix [for]="dt"></mat-datepicker-toggle><mat-datepicker #dt></mat-datepicker>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Status</mat-label>
              <input matInput [(ngModel)]="fStatus"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Amount status</mat-label>
              <mat-select [(ngModel)]="fAmountStatus"><mat-option value="">Any</mat-option>
                <mat-option value="Paid">Paid</mat-option><mat-option value="Pending">Pending</mat-option>
                <mat-option value="UnPaid">UnPaid</mat-option><mat-option value="CashOnDelivery">CashOnDelivery</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Pincode</mat-label>
              <input matInput [(ngModel)]="fPincode"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Courier</mat-label>
              <input matInput [(ngModel)]="fCourier"></mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Entry month</mat-label>
              <mat-select [(ngModel)]="fEntryMonth"><mat-option value="">Any</mat-option>
                <mat-option *ngFor="let m of monthNames" [value]="m">{{ m }}</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline"><mat-label>Entry year</mat-label>
              <mat-select [(ngModel)]="fEntryYear"><mat-option [value]="null">Any</mat-option>
                <mat-option *ngFor="let y of yearOptions" [value]="y">{{ y }}</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <div class="cc-filter-actions">
            <button mat-stroked-button type="button" (click)="resetEntryFilters()">Reset filters</button>
            <button mat-raised-button color="primary" type="button" (click)="searchEntries(0)"><mat-icon>search</mat-icon> Search entries</button>
          </div>
        </mat-expansion-panel>

        <div *ngIf="entryRows.length" class="cc-entry-wrap">
          <h3>Matching collection entries</h3>
          <div class="cc-table-scroll">
            <table class="cc-table">
              <thead>
                <tr><th>Date</th><th>Month</th><th>Year</th><th>Customer</th><th>AWB</th><th>Receiver</th><th>Amount</th><th>Amt status</th><th></th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let r of entryRows">
                  <td>{{ r.entryDate | date:'yyyy-MM-dd' }}</td>
                  <td>{{ r.entryMonth || '—' }}</td>
                  <td>{{ r.entryYear != null ? r.entryYear : '—' }}</td>
                  <td>{{ r.customerName }}</td>
                  <td>{{ r.awbNo || '—' }}</td>
                  <td>{{ r.receiverName || '—' }}</td>
                  <td>{{ r.amount != null ? (r.amount | number:'1.2-2') : '—' }}</td>
                  <td><span class="cc-badge">{{ r.amountStatus || '—' }}</span></td>
                  <td><button mat-icon-button (click)="editEntry(r.id)"><mat-icon>edit</mat-icon></button></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="cc-pag-hint" *ngIf="entryRows.length || entryTotal > 0">{{ entryPaginationHint }}</p>
          <mat-paginator [length]="entryTotal" [pageIndex]="entryPage" [pageSize]="entrySize"
            [pageSizeOptions]="[10,20,50]" (page)="onEntryPage($event)"></mat-paginator>
        </div>
      </mat-card>

      <mat-card class="cc-card cc-mt">
        <h2 class="cc-h2">Customers</h2>
        <div class="cc-search-row">
          <mat-form-field appearance="outline" class="cc-grow">
            <mat-label>Search customers</mat-label>
            <input matInput [(ngModel)]="searchTerm" (keyup.enter)="searchCustomers()">
          </mat-form-field>
          <button mat-stroked-button (click)="searchCustomers()"><mat-icon>search</mat-icon></button>
        </div>
        <table mat-table [dataSource]="customers" class="cc-mat-table">
          <ng-container matColumnDef="customerCode"><th mat-header-cell *matHeaderCellDef>Code</th><td mat-cell *matCellDef="let r">{{ r.customerCode }}</td></ng-container>
          <ng-container matColumnDef="customerName"><th mat-header-cell *matHeaderCellDef>Name</th><td mat-cell *matCellDef="let r">{{ r.customerName }}</td></ng-container>
          <ng-container matColumnDef="phone"><th mat-header-cell *matHeaderCellDef>Phone</th><td mat-cell *matCellDef="let r">{{ r.phone }}</td></ng-container>
          <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th><td mat-cell *matCellDef="let r">
            <button mat-icon-button (click)="edit(r)" matTooltip="Edit" [appDisableIfNoPermission]="'COLLECTION_CUSTOMER:edit'"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button (click)="openCenter(r)" matTooltip="Collection center"><mat-icon>inventory_2</mat-icon></button>
            <button *appHasPermission="'COLLECTION_CUSTOMER:delete'" mat-icon-button color="warn" (click)="deleteCustomer(r)" matTooltip="Deactivate customer"><mat-icon>delete</mat-icon></button>
          </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="cols"></tr>
          <tr mat-row *matRowDef="let row; columns: cols"></tr>
        </table>
        <p class="cc-pag-hint" *ngIf="customers.length || totalElements > 0">{{ customerPaginationHint }}</p>
        <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
          [pageSizeOptions]="[10,25,50]" (page)="onPageChange($event)"></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .cc-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .cc-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .cc-head h1 { margin: 0; font-size: 1.5rem; font-weight: 700; }
    .cc-sub { margin: 4px 0 0; color: #64748b; font-size: 0.9rem; }
    .cc-card { padding: 8px 16px 16px; margin-bottom: 16px; border-radius: 12px; }
    .cc-mt { margin-top: 8px; }
    .cc-h2 { font-size: 1.1rem; margin: 8px 0 12px; }
    .cc-filter-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
    .cc-filter-actions { display: flex; gap: 12px; margin-top: 12px; }
    .cc-search-row { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
    .cc-grow { flex: 1; min-width: 200px; }
    .cc-table-scroll { overflow: auto; max-height: 420px; border: 1px solid #e5e7eb; border-radius: 8px; }
    .cc-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .cc-table th { position: sticky; top: 0; background: #f8fafc; z-index: 1; text-align: left; padding: 10px 8px; border-bottom: 1px solid #e5e7eb; }
    .cc-table td { padding: 8px; border-bottom: 1px solid #f1f5f9; }
    .cc-badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: #eef2ff; color: #3730a3; }
    .cc-entry-wrap { margin-top: 16px; }
    .cc-mat-table { width: 100%; }
    .cc-pag-hint { font-size: 13px; color: #475569; margin: 8px 0 4px; }
  `]
})
export class CollectionCustomerListComponent implements OnInit {
  customers: CollectionCustomer[] = [];
  cols = ['customerCode', 'customerName', 'phone', 'actions'];
  searchTerm = '';
  page = 0;
  pageSize = 10;
  totalElements = 0;

  filtersOpen = false;
  fCustomerName = '';
  fAwb = '';
  fFrom: Date | null = null;
  fTo: Date | null = null;
  fStatus = '';
  fAmountStatus = '';
  fPincode = '';
  fCourier = '';
  monthNames = MONTH_NAMES;
  yearOptions: number[] = [];
  fEntryMonth = '';
  fEntryYear: number | null = null;

  entryRows: any[] = [];
  entryTotal = 0;
  entryPage = 0;
  entrySize = 20;

  constructor(
    private api: ApiService,
    private router: Router,
    private toast: ToastService,
    private uiConfig: UiConfigService
  ) {}

  get customerPaginationHint(): string {
    const pages = Math.max(1, Math.ceil(this.totalElements / Math.max(1, this.pageSize)));
    return `Total Records: ${this.totalElements} — Page ${this.page + 1} of ${pages}`;
  }

  get entryPaginationHint(): string {
    const pages = Math.max(1, Math.ceil(this.entryTotal / Math.max(1, this.entrySize)));
    return `Total Records: ${this.entryTotal} — Page ${this.entryPage + 1} of ${pages}`;
  }

  ngOnInit() {
    this.uiConfig.getUi().subscribe({
      next: (u) => (this.yearOptions = buildYearOptions(u.collectionYearRangePast, u.collectionYearRangeFuture)),
      error: () => (this.yearOptions = buildYearOptions(5, 5))
    });
    this.loadCustomers();
  }

  loadCustomers() {
    this.api.getPaged<CollectionCustomer>('/collection-customers', this.page, this.pageSize).subscribe({
      next: (res) => {
        this.customers = res?.content ?? [];
        this.totalElements = res?.totalElements ?? 0;
      },
      error: () => { this.customers = []; this.totalElements = 0; }
    });
  }

  searchCustomers() {
    const t = (this.searchTerm || '').trim();
    this.page = 0;
    if (!t) {
      this.loadCustomers();
      return;
    }
    this.api.get<PageResponse<CollectionCustomer>>('/collection-customers/search', { term: t, page: this.page, size: this.pageSize }).subscribe({
      next: (res) => {
        this.customers = res?.content ?? [];
        this.totalElements = res?.totalElements ?? 0;
      },
      error: () => this.toast.error('Error', 'Search failed')
    });
  }

  onPageChange(e: any) {
    this.page = e.pageIndex;
    this.pageSize = e.pageSize;
    const t = (this.searchTerm || '').trim();
    if (t) this.searchCustomers();
    else this.loadCustomers();
  }

  resetEntryFilters() {
    this.fCustomerName = '';
    this.fAwb = '';
    this.fFrom = null;
    this.fTo = null;
    this.fStatus = '';
    this.fAmountStatus = '';
    this.fPincode = '';
    this.fCourier = '';
    this.fEntryMonth = '';
    this.fEntryYear = null;
    this.entryRows = [];
    this.entryTotal = 0;
  }

  searchEntries(pageIdx: number) {
    this.entryPage = pageIdx;
    const p: Record<string, string | number> = { page: this.entryPage, size: this.entrySize };
    if (this.fCustomerName.trim()) p['customerName'] = this.fCustomerName.trim();
    if (this.fAwb.trim()) p['awbNo'] = this.fAwb.trim();
    if (this.fFrom) p['dateFrom'] = this.toIsoDate(this.fFrom);
    if (this.fTo) p['dateTo'] = this.toIsoDate(this.fTo);
    if (this.fStatus.trim()) p['status'] = this.fStatus.trim();
    if (this.fAmountStatus) p['amountStatus'] = this.fAmountStatus;
    if (this.fPincode.trim()) p['pincode'] = this.fPincode.trim();
    if (this.fCourier.trim()) p['courier'] = this.fCourier.trim();
    if (this.fEntryMonth) p['entryMonth'] = this.fEntryMonth;
    if (this.fEntryYear != null) p['entryYear'] = this.fEntryYear;
    this.api.get<PageResponse<any>>('/collection-center/entries', p).subscribe({
      next: (res) => {
        this.entryRows = res?.content ?? [];
        this.entryTotal = res?.totalElements ?? 0;
      },
      error: () => this.toast.error('Error', 'Entry search failed')
    });
  }

  onEntryPage(e: any) {
    this.entrySize = e.pageSize;
    this.searchEntries(e.pageIndex);
  }

  private toIsoDate(d: Date): string {
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  goCreate() {
    this.router.navigate(['/collection-customer/create']);
  }

  edit(r: CollectionCustomer) {
    this.router.navigate(['/collection-customer/edit', r.id]);
  }

  openCenter(r: CollectionCustomer) {
    this.router.navigate(['/client-entries/collection-center'], { queryParams: { customerId: r.id } });
  }

  editEntry(id: string) {
    this.router.navigate(['/client-entries/collection-center/edit', id]);
  }

  deleteCustomer(r: CollectionCustomer): void {
    const msg =
      `Deactivate collection customer "${r.customerName}"?\n\n` +
      `They will be hidden from lists. Existing collection entries are not deleted.`;
    if (!window.confirm(msg)) {
      return;
    }
    this.api.deletePath(`/collection-customers/${r.id}`).subscribe({
      next: () => {
        this.toast.success('Deactivated', 'Customer marked inactive.');
        const t = (this.searchTerm || '').trim();
        if (t) {
          this.searchCustomers();
        } else {
          this.loadCustomers();
        }
      },
      error: (e) => {
        const m = e?.error?.message || e?.error?.error || 'Could not deactivate';
        this.toast.error('Error', typeof m === 'string' ? m : 'Could not deactivate');
      }
    });
  }
}
