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
    <div class="p-6 space-y-6">
      <!-- Page Header -->
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-4 border-b border-gray-200">
        <div>
          <h1 class="text-2xl font-bold text-gray-900 tracking-tight">Collection Customer Master</h1>
          <p class="text-sm text-gray-500 mt-1">Manage registered collection customers, AWB number registries, and entry tracking</p>
        </div>
        <button *appHasPermission="'COLLECTION_CUSTOMER:create'" mat-raised-button color="primary" class="flex items-center gap-2" (click)="goCreate()">
          <mat-icon>add</mat-icon>
          <span>Add Customer</span>
        </button>
      </div>

      <!-- Search Collection Entries Filter Panel -->
      <mat-card class="shadow-sm border border-gray-200 rounded-xl overflow-hidden">
        <mat-expansion-panel [expanded]="filtersOpen" class="shadow-none">
          <mat-expansion-panel-header>
            <mat-panel-title class="font-semibold text-gray-800 flex items-center gap-2">
              <mat-icon class="text-indigo-600">search</mat-icon>
              <span>Search Collection Entries (AWB / Date / Status)</span>
            </mat-panel-title>
          </mat-expansion-panel-header>

          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3 pt-3">
            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Customer Name</mat-label>
              <input matInput [(ngModel)]="fCustomerName" placeholder="Customer name">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>AWB Number</mat-label>
              <input matInput [(ngModel)]="fAwb" placeholder="AWB number">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>From Date</mat-label>
              <input matInput [matDatepicker]="df" [(ngModel)]="fFrom">
              <mat-datepicker-toggle matSuffix [for]="df"></mat-datepicker-toggle>
              <mat-datepicker #df></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>To Date</mat-label>
              <input matInput [matDatepicker]="dt" [(ngModel)]="fTo">
              <mat-datepicker-toggle matSuffix [for]="dt"></mat-datepicker-toggle>
              <mat-datepicker #dt></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Status</mat-label>
              <input matInput [(ngModel)]="fStatus" placeholder="Status">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Amount Status</mat-label>
              <mat-select [(ngModel)]="fAmountStatus">
                <mat-option value="">Any</mat-option>
                <mat-option value="Paid">Paid</mat-option>
                <mat-option value="Pending">Pending</mat-option>
                <mat-option value="UnPaid">UnPaid</mat-option>
                <mat-option value="CashOnDelivery">Cash On Delivery</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Pincode</mat-label>
              <input matInput [(ngModel)]="fPincode" placeholder="Pincode">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Courier</mat-label>
              <input matInput [(ngModel)]="fCourier" placeholder="Courier">
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Entry Month</mat-label>
              <mat-select [(ngModel)]="fEntryMonth">
                <mat-option value="">Any</mat-option>
                <mat-option *ngFor="let m of monthNames" [value]="m">{{ m }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="w-full">
              <mat-label>Entry Year</mat-label>
              <mat-select [(ngModel)]="fEntryYear">
                <mat-option [value]="null">Any</mat-option>
                <mat-option *ngFor="let y of yearOptions" [value]="y">{{ y }}</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="flex justify-end gap-3 pt-2">
            <button mat-stroked-button type="button" (click)="resetEntryFilters()">Reset Filters</button>
            <button mat-raised-button color="primary" type="button" (click)="searchEntries(0)">
              <mat-icon>search</mat-icon> Search Entries
            </button>
          </div>
        </mat-expansion-panel>

        <!-- Matching Entries Table -->
        <div *ngIf="entryRows.length" class="p-4 bg-gray-50 border-t border-gray-200">
          <div class="flex justify-between items-center mb-3">
            <h3 class="text-base font-bold text-gray-800">Matching Collection Entries</h3>
            <span class="text-xs text-gray-500">{{ entryPaginationHint }}</span>
          </div>
          <div class="overflow-x-auto border border-gray-200 rounded-lg bg-white">
            <table class="w-full text-sm text-left">
              <thead class="bg-gray-100 text-xs font-semibold text-gray-600 uppercase border-b">
                <tr>
                  <th class="px-4 py-3">Date</th>
                  <th class="px-4 py-3">Month/Year</th>
                  <th class="px-4 py-3">Customer</th>
                  <th class="px-4 py-3">AWB</th>
                  <th class="px-4 py-3">Receiver</th>
                  <th class="px-4 py-3">Amount</th>
                  <th class="px-4 py-3">Status</th>
                  <th class="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr *ngFor="let r of entryRows" class="hover:bg-gray-50">
                  <td class="px-4 py-2.5 font-medium">{{ r.entryDate | date:'yyyy-MM-dd' }}</td>
                  <td class="px-4 py-2.5 text-gray-500">{{ r.entryMonth || '—' }} {{ r.entryYear != null ? r.entryYear : '' }}</td>
                  <td class="px-4 py-2.5 font-semibold text-gray-900">{{ r.customerName }}</td>
                  <td class="px-4 py-2.5 font-mono text-indigo-600 font-medium">{{ r.awbNo || '—' }}</td>
                  <td class="px-4 py-2.5 text-gray-600">{{ r.receiverName || '—' }}</td>
                  <td class="px-4 py-2.5 font-bold">₹{{ r.amount != null ? (r.amount | number:'1.2-2') : '0.00' }}</td>
                  <td class="px-4 py-2.5">
                    <span class="px-2.5 py-1 text-xs font-semibold rounded-full" [ngClass]="getAmountStatusClass(r.amountStatus)">
                      {{ r.amountStatus || '—' }}
                    </span>
                  </td>
                  <td class="px-4 py-2.5 text-right">
                    <button mat-icon-button color="primary" (click)="editEntry(r.id)" matTooltip="Edit Entry">
                      <mat-icon>edit</mat-icon>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <mat-paginator [length]="entryTotal" [pageIndex]="entryPage" [pageSize]="entrySize"
            [pageSizeOptions]="[10,20,50]" (page)="onEntryPage($event)"></mat-paginator>
        </div>
      </mat-card>

      <!-- Customer Registry Table Card -->
      <mat-card class="shadow-sm border border-gray-200 rounded-xl p-5">
        <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
          <div>
            <h2 class="text-lg font-bold text-gray-900">Collection Customers</h2>
            <p class="text-xs text-gray-500">Total registered: {{ totalElements }}</p>
          </div>

          <div class="w-full sm:w-80">
            <mat-form-field appearance="outline" class="w-full dense-field" subscriptSizing="dynamic">
              <mat-label>Search customers...</mat-label>
              <input matInput [(ngModel)]="searchTerm" (keyup.enter)="searchCustomers()">
              <button mat-icon-button matSuffix (click)="searchCustomers()">
                <mat-icon>search</mat-icon>
              </button>
            </mat-form-field>
          </div>
        </div>

        <div class="overflow-x-auto border border-gray-200 rounded-lg">
          <table mat-table [dataSource]="customers" class="w-full">
            <ng-container matColumnDef="customerCode">
              <th mat-header-cell *matHeaderCellDef class="font-bold text-gray-700">Code</th>
              <td mat-cell *matCellDef="let r" class="font-mono font-medium text-indigo-700">{{ r.customerCode }}</td>
            </ng-container>

            <ng-container matColumnDef="customerName">
              <th mat-header-cell *matHeaderCellDef class="font-bold text-gray-700">Customer Name</th>
              <td mat-cell *matCellDef="let r">
                <span class="font-semibold text-gray-900 block">{{ r.customerName }}</span>
                <span class="text-xs text-gray-500" *ngIf="r.contactPerson">Contact: {{ r.contactPerson }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="phone">
              <th mat-header-cell *matHeaderCellDef class="font-bold text-gray-700">Phone & Location</th>
              <td mat-cell *matCellDef="let r">
                <div class="text-sm">
                  <span class="block text-gray-800" *ngIf="r.phone">{{ r.phone }}</span>
                  <span class="text-xs text-gray-500" *ngIf="r.city || r.state">{{ r.city }}{{ r.city && r.state ? ', ' : '' }}{{ r.state }}</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="font-bold text-gray-700 text-right">Actions</th>
              <td mat-cell *matCellDef="let r" class="text-right">
                <button mat-icon-button color="primary" (click)="edit(r)" matTooltip="Edit Customer" [appDisableIfNoPermission]="'COLLECTION_CUSTOMER:edit'">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button color="accent" (click)="openCenter(r)" matTooltip="Collection Center">
                  <mat-icon>inventory_2</mat-icon>
                </button>
                <button *appHasPermission="'COLLECTION_CUSTOMER:delete'" mat-icon-button color="warn" (click)="deleteCustomer(r)" matTooltip="Deactivate Customer">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="cols" class="bg-gray-50"></tr>
            <tr mat-row *matRowDef="let row; columns: cols" class="hover:bg-gray-50/80 transition-colors"></tr>
          </table>

          <div *ngIf="!customers.length" class="text-center py-10 text-gray-500">
            <mat-icon class="text-4xl text-gray-300 mb-2">person_off</mat-icon>
            <p class="font-medium">No Collection Customers Found</p>
          </div>
        </div>

        <div class="flex justify-between items-center mt-3 pt-2">
          <span class="text-xs text-gray-500" *ngIf="customers.length || totalElements > 0">{{ customerPaginationHint }}</span>
          <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
            [pageSizeOptions]="[10,25,50]" (page)="onPageChange($event)"></mat-paginator>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .bg-paid { background-color: #d1fae5; color: #065f46; }
    .bg-pending { background-color: #fef3c7; color: #92400e; }
    .bg-unpaid { background-color: #ffe4e6; color: #9f1239; }
    .bg-cod { background-color: #e0f2fe; color: #075985; }
    .dense-field .mat-mdc-form-field-subscript-wrapper { display: none; }
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
    return `Showing page ${this.page + 1} of ${pages} (${this.totalElements} total customers)`;
  }

  get entryPaginationHint(): string {
    const pages = Math.max(1, Math.ceil(this.entryTotal / Math.max(1, this.entrySize)));
    return `Showing page ${this.entryPage + 1} of ${pages} (${this.entryTotal} total entries)`;
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

  getAmountStatusClass(status: string): string {
    if (!status) return '';
    const s = status.toLowerCase();
    if (s.includes('paid') && !s.includes('un')) return 'bg-paid';
    if (s.includes('pending')) return 'bg-pending';
    if (s.includes('unpaid')) return 'bg-unpaid';
    if (s.includes('delivery') || s.includes('cod')) return 'bg-cod';
    return '';
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
