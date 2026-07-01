import { Component, OnInit } from '@angular/core';
import { ApiService, PageResponse } from '../../../core/services/api.service';
import { ExcelExportService } from '../../../core/services/excel-export.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { UiConfigService } from '../../../core/services/ui-config.service';
import { buildYearOptions, MONTH_NAMES } from '../../../core/utils/month-year.util';
import { Router } from '@angular/router';

@Component({
  selector: 'app-customer-collection-report',
  template: `
    <div class="ccr rpt-animate">

      <!-- Summary Cards -->
      <div class="ccr-summary">
        <div class="ccr-summary-card">
          <span class="ccr-summary-card__label">Total Records</span>
          <span class="ccr-summary-card__value">{{ totals ? totals.totalRecords : '—' }}</span>
        </div>
        <div class="ccr-summary-card ccr-summary-card--accent">
          <span class="ccr-summary-card__label">Total Amount</span>
          <span class="ccr-summary-card__value">{{ totals ? '₹ ' + (totals.totalAmount | number:'1.2-2') : '—' }}</span>
        </div>
      </div>

      <!-- Filters -->
      <div class="ccr-filter-card">
        <div class="ccr-filter-card__grid">
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Collection customer</mat-label>
            <mat-select [(ngModel)]="collectionCustomerId"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let c of customers" [value]="c.id">{{ c.customerName }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Customer search</mat-label>
            <input matInput [(ngModel)]="customerSearch" [matAutocomplete]="ccrCustomerAuto" (ngModelChange)="onCustomerSearchChange($event)" placeholder="ANBU">
            <mat-autocomplete #ccrCustomerAuto="matAutocomplete">
              <mat-option *ngFor="let c of filteredCustomerSuggestions" [value]="c">{{ c }}</mat-option>
            </mat-autocomplete>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>AWB</mat-label><input matInput [(ngModel)]="awbNo"></mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>From</mat-label>
            <input matInput [matDatepicker]="df" [(ngModel)]="dateFrom"><mat-datepicker-toggle matSuffix [for]="df"></mat-datepicker-toggle><mat-datepicker #df></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>To</mat-label>
            <input matInput [matDatepicker]="dt" [(ngModel)]="dateTo"><mat-datepicker-toggle matSuffix [for]="dt"></mat-datepicker-toggle><mat-datepicker #dt></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Amount status</mat-label>
            <mat-select [(ngModel)]="amountStatus"><mat-option value="">All</mat-option>
              <mat-option value="Paid">Paid</mat-option><mat-option value="Pending">Pending</mat-option>
              <mat-option value="UnPaid">UnPaid</mat-option><mat-option value="CashOnDelivery">CashOnDelivery</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Status</mat-label><input matInput [(ngModel)]="status"></mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Courier</mat-label><input matInput [(ngModel)]="courier"></mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Month</mat-label>
            <mat-select [(ngModel)]="entryMonth"><mat-option value="">All</mat-option>
              <mat-option *ngFor="let m of monthNames" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="ccr-f"><mat-label>Year</mat-label>
            <mat-select [(ngModel)]="entryYear"><mat-option [value]="null">All</mat-option>
              <mat-option *ngFor="let y of yearOptions" [value]="y">{{ y }}</mat-option>
            </mat-select>
          </mat-form-field>
        </div>
        <div class="ccr-actions">
          <button mat-stroked-button type="button" class="ccr-actions__btn ccr-actions__btn--reset" (click)="reset()">Reset</button>
          <button mat-raised-button color="primary" type="button" class="ccr-actions__btn ccr-actions__btn--search" (click)="load(0)"><mat-icon>search</mat-icon> Search</button>
          <button mat-stroked-button type="button" class="ccr-actions__btn ccr-actions__btn--export" (click)="exportExcel()"><mat-icon>grid_on</mat-icon> Export Excel</button>
        </div>
      </div>

      <!-- Data Table -->
      <div class="ccr-table-card">
        <div *ngIf="loading" class="ccr-loading"><mat-spinner diameter="40"></mat-spinner></div>
        <div class="ccr-table-scroll">
          <table class="ccr-table">
            <thead>
              <tr>
                <th>Date</th><th>Month</th><th>Year</th><th>Customer</th><th>AWB</th><th>Receiver</th><th>Pincode</th><th>Courier</th><th>Weight</th><th>Amount</th><th>Amount status</th><th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of rows" class="ccr-row" (click)="openEntry(r)">
                <td>{{ r.entryDate | date:'yyyy-MM-dd' }}</td>
                <td>{{ r.entryMonth || '—' }}</td>
                <td>{{ r.entryYear != null ? r.entryYear : '—' }}</td>
                <td>{{ r.customerName }}</td>
                <td>{{ r.awbNo || '—' }}</td>
                <td>{{ r.receiverName || '—' }}</td>
                <td>{{ r.pincode || '—' }}</td>
                <td>{{ r.courier || '—' }}</td>
                <td>{{ r.weight != null ? r.weight : '—' }}</td>
                <td class="ccr-td-amount">{{ r.amount != null ? (r.amount | number:'1.2-2') : '—' }}</td>
                <td>
                  <span class="ccr-badge"
                    [class.ccr-badge--paid]="r.amountStatus === 'Paid'"
                    [class.ccr-badge--pending]="r.amountStatus === 'Pending'"
                    [class.ccr-badge--unpaid]="r.amountStatus === 'UnPaid'"
                    [class.ccr-badge--cod]="r.amountStatus === 'CashOnDelivery'"
                  >{{ r.amountStatus || '—' }}</span>
                </td>
                <td>{{ r.status || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <mat-paginator [length]="total" [pageIndex]="page" [pageSize]="size" [pageSizeOptions]="[20,50,100]" (page)="onPage($event)"></mat-paginator>
      </div>

    </div>
  `,
  styles: [`
    /* Layout */
    .ccr {
      padding: 16px 0;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* ── Summary Cards ── */
    .ccr-summary {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 16px;
    }
    .ccr-summary-card {
      display: flex;
      flex-direction: column;
      gap: 4px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 18px 20px;
      transition: box-shadow 0.2s, transform 0.2s;
    }
    .ccr-summary-card:hover {
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      transform: translateY(-1px);
    }
    .ccr-summary-card__label {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: #64748b;
    }
    .ccr-summary-card__value {
      font-size: 22px;
      font-weight: 700;
      color: #1e293b;
    }
    .ccr-summary-card--accent {
      border-left: 3px solid #3b82f6;
      background: linear-gradient(135deg, #eff6ff 0%, #f8fafc 100%);
    }
    .ccr-summary-card--accent .ccr-summary-card__value {
      color: #1d4ed8;
    }

    /* ── Filter Card ── */
    .ccr-filter-card {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 20px;
    }
    .ccr-filter-card__grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 12px 16px;
    }
    .ccr-f {
      width: 100%;
      margin-bottom: -1em;
    }
    .ccr-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
      padding-top: 8px;
      border-top: 1px solid #f1f5f9;
      margin-top: 8px;
    }
    .ccr-actions__btn {
      min-width: 100px;
      border-radius: 6px !important;
      font-weight: 500;
      font-size: 13px;
      letter-spacing: 0.2px;
    }
    .ccr-actions__btn--search {
      padding-left: 16px;
      padding-right: 20px;
    }
    .ccr-actions__btn--export {
      margin-left: auto;
    }

    /* ── Table Card ── */
    .ccr-table-card {
      position: relative;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      overflow: hidden;
    }
    .ccr-table-scroll {
      overflow: auto;
      max-height: 65vh;
    }
    .ccr-loading {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(255,255,255,0.8);
      z-index: 2;
      border-radius: 10px;
    }

    /* ── Table ── */
    .ccr-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      color: #1f2937;
    }
    .ccr-table thead th {
      position: sticky;
      top: 0;
      z-index: 1;
      background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
      padding: 12px 10px;
      text-align: left;
      font-weight: 600;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      color: #475569;
      border-bottom: 2px solid #e2e8f0;
      white-space: nowrap;
    }
    .ccr-table tbody td {
      padding: 10px;
      border-bottom: 1px solid #e2e8f0;
    }
    .ccr-table tbody tr:nth-child(even) {
      background: #f8fafc;
    }
    .ccr-td-amount {
      font-variant-numeric: tabular-nums;
      text-align: right;
    }

    /* ── Row interaction ── */
    .ccr-row {
      cursor: pointer;
      transition: background 0.15s;
    }
    .ccr-row:hover {
      background: #eff6ff !important;
    }

    /* ── Status Badges ── */
    .ccr-badge {
      display: inline-block;
      font-size: 11px;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: 999px;
      white-space: nowrap;
      background: #f1f5f9;
      color: #475569;
    }
    .ccr-badge--paid {
      background: #dcfce7;
      color: #166534;
    }
    .ccr-badge--pending {
      background: #fef9c3;
      color: #854d0e;
    }
    .ccr-badge--unpaid {
      background: #fee2e2;
      color: #991b1b;
    }
    .ccr-badge--cod {
      background: #e0e7ff;
      color: #3730a3;
    }

    /* ── Responsive ── */
    @media (max-width: 768px) {
      .ccr-summary {
        grid-template-columns: 1fr 1fr;
      }
      .ccr-filter-card__grid {
        grid-template-columns: 1fr;
      }
      .ccr-filter-card {
        padding: 14px;
      }
      .ccr-actions__btn--export {
        margin-left: 0;
      }
    }
    @media (max-width: 480px) {
      .ccr-summary {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class CustomerCollectionReportComponent implements OnInit {
  customers: { id: string; customerName: string }[] = [];
  rows: any[] = [];
  total = 0;
  page = 0;
  size = 50;
  loading = false;
  totals: { totalRecords: number; totalAmount: number } | null = null;

  collectionCustomerId = '';
  customerSearch = '';
  customerSuggestions: string[] = [];
  filteredCustomerSuggestions: string[] = [];
  awbNo = '';
  dateFrom: Date | null = null;
  dateTo: Date | null = null;
  amountStatus = '';
  status = '';
  courier = '';
  monthNames = MONTH_NAMES;
  yearOptions: number[] = [];
  entryMonth = '';
  entryYear: number | null = null;

  constructor(
    private api: ApiService,
    private excel: ExcelExportService,
    private toast: ToastService,
    private uiConfig: UiConfigService,
    private router: Router
  ) {}

  ngOnInit() {
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

  private iso(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private params(): Record<string, string | number> {
    const p: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.collectionCustomerId) p['collectionCustomerId'] = this.collectionCustomerId;
    if (!this.collectionCustomerId && this.customerSearch.trim()) p['customerName'] = this.customerSearch.trim();
    if (this.awbNo.trim()) p['awbNo'] = this.awbNo.trim();
    const df = this.iso(this.dateFrom);
    const dt = this.iso(this.dateTo);
    if (df) p['dateFrom'] = df;
    if (dt) p['dateTo'] = dt;
    if (this.amountStatus) p['amountStatus'] = this.amountStatus;
    if (this.status.trim()) p['status'] = this.status.trim();
    if (this.courier.trim()) p['courier'] = this.courier.trim();
    if (this.entryMonth) p['entryMonth'] = this.entryMonth;
    if (this.entryYear != null) p['entryYear'] = this.entryYear;
    return p;
  }

  load(pageIdx: number) {
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
    this.api.get<{ totalRecords: number; totalAmount: number }>('/collection-center/entries/report-totals', this.params()).subscribe({
      next: (t) => { this.totals = t; },
      error: () => { this.totals = null; }
    });
  }

  onPage(e: any) {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  reset() {
    this.collectionCustomerId = '';
    this.customerSearch = '';
    this.filteredCustomerSuggestions = [...this.customerSuggestions];
    this.awbNo = '';
    this.dateFrom = null;
    this.dateTo = null;
    this.amountStatus = '';
    this.status = '';
    this.courier = '';
    this.entryMonth = '';
    this.entryYear = null;
    this.load(0);
  }

  onCustomerSearchChange(value: string): void {
    const q = (value || '').trim().toLowerCase();
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

  openEntry(r: { id?: string }): void {
    if (!r?.id) return;
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
        const headers = ['Date', 'Month', 'Year', 'Customer', 'AWB', 'Receiver', 'Pincode', 'Courier', 'Weight', 'Amount', 'Amount Status', 'Status'];
        const rows = data.map((r: any) => ({
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
          Status: r.status
        }));

        let party = 'CustomerCollection';
        if (this.collectionCustomerId) {
          const selectedCust = this.customers.find((c: any) => c.id === this.collectionCustomerId);
          if (selectedCust) {
            party = selectedCust.customerName;
          }
        } else if (this.customerSearch && this.customerSearch.trim()) {
          party = this.customerSearch.trim();
        }

        let monthStr = 'ALL';
        let yearStr = 'ALL';
        if (this.entryMonth) {
          monthStr = this.entryMonth.toUpperCase();
        }
        if (this.entryYear != null) {
          yearStr = String(this.entryYear);
        }
        if (monthStr === 'ALL' && yearStr === 'ALL' && this.dateFrom) {
          const dateObj = new Date(this.dateFrom);
          monthStr = dateObj.toLocaleString('en-US', { month: 'long' }).toUpperCase();
          yearStr = String(dateObj.getFullYear());
        }

        const cleanParty = party.replace(/[^a-zA-Z0-9]/g, '');
        const filename = `${cleanParty}_${monthStr}_${yearStr}`;

        await this.excel.exportToExcel(rows, filename, headers, { exactFilename: true });
        this.toast.success('Export', 'Downloaded');
      },
      error: () => this.toast.error('Error', 'Export failed')
    });
  }
}
