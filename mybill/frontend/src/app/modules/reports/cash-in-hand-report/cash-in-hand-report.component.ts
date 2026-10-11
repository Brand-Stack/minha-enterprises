import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { MatTableDataSource } from '@angular/material/table';

interface SalesEntry {
  id: string;
  date: string;
  moduleType: string;
  partyName: string;
  billNumber: string;
  itemsTotal: number;
  receivedAmount: number;
  receivedCashAmount?: number;
  receivedOnlineAmount?: number;
  pendingAmount: number;
  paymentStatus: string;
  paymentMode: string;
}

interface ExpenseEntry {
  id: string;
  date: string;
  moduleType: string;
  partyName: string;
  billNumber: string;
  itemsTotal: number;
  paidAmount: number;
  paidCashAmount?: number;
  paidOnlineAmount?: number;
  pendingAmount: number;
  paymentStatus: string;
  paymentType: string;
  category: string;
}

interface CashInHandData {
  startingLiquidCash?: number;
  startingOnlineBalance?: number;
  startingTotalBalance?: number;
  liquidCash?: number;
  onlineBalance?: number;
  totalAvailableBalance?: number;
  totalCashReceived?: number;
  totalOnlineReceived?: number;
  totalSalesReceived?: number;
  totalCashExpenses?: number;
  totalOnlineExpenses?: number;
  totalExpensesPaid?: number;
  salesEntries: SalesEntry[];
  expenseEntries: ExpenseEntry[];
  startDate: string;
  endDate: string;
  filterType: string;
}

@Component({
  selector: 'app-cash-in-hand-report',
  template: `
    <div class="report-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Cash In Hand Dashboard</h1>
          <p class="body-small text-neutral-light">Track received amounts vs paid Cash In entries</p>
        </div>
        <div class="header-actions">
          <button mat-raised-button (click)="downloadExcel()" [disabled]="loading" class="excel-btn">
            <mat-icon>table_chart</mat-icon>
            <span>Download Excel</span>
          </button>
          <button mat-raised-button color="primary" (click)="downloadPdf()" [disabled]="loading">
            <mat-icon>download</mat-icon>
            <span>Download PDF</span>
          </button>
        </div>
      </div>

      <!-- Quick Filters -->
      <mat-card class="filter-card mb-6">
        <div class="filter-row">
          <div class="quick-filters">
            <button mat-stroked-button [color]="filterType === 'DAY' ? 'primary' : ''" 
                    (click)="setFilter('DAY')" class="quick-filter-btn">
              Today
            </button>
            <button mat-stroked-button [color]="filterType === 'WEEK' ? 'primary' : ''" 
                    (click)="setFilter('WEEK')" class="quick-filter-btn">
              This Week
            </button>
            <button mat-stroked-button [color]="filterType === 'MONTH' ? 'primary' : ''" 
                    (click)="setFilter('MONTH')" class="quick-filter-btn">
              This Month
            </button>
            <button mat-stroked-button [color]="filterType === 'YEAR' ? 'primary' : ''" 
                    (click)="setFilter('YEAR')" class="quick-filter-btn">
              This Year
            </button>
            <button mat-stroked-button [color]="filterType === 'CUSTOM' ? 'primary' : ''" 
                    (click)="filterType = 'CUSTOM'" class="quick-filter-btn">
              Custom
            </button>
          </div>
          
          <div class="date-filters" *ngIf="filterType === 'CUSTOM'">
            <mat-form-field appearance="outline" class="filter-field filter-date">
              <mat-label>Start Date</mat-label>
              <input matInput [matDatepicker]="startPicker" [(ngModel)]="startDate">
              <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
              <mat-datepicker #startPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="filter-field filter-date">
              <mat-label>End Date</mat-label>
              <input matInput [matDatepicker]="endPicker" [(ngModel)]="endDate">
              <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
              <mat-datepicker #endPicker></mat-datepicker>
            </mat-form-field>

            <button mat-raised-button color="primary" (click)="loadData()" class="filter-btn">
              <mat-icon>search</mat-icon>
              <span>Search</span>
            </button>
          </div>
        </div>
        
        <div class="date-range-display" *ngIf="data.startDate && data.endDate">
          <mat-icon>date_range</mat-icon>
          <span>{{ data.startDate | date:'dd MMM yyyy' }} - {{ data.endDate | date:'dd MMM yyyy' }}</span>
        </div>
      </mat-card>

      <app-loading-spinner *ngIf="loading"></app-loading-spinner>

      <!-- Starting Balance (Yesterday's End / Day before period start) -->
      <div class="starting-balance-section mb-6" *ngIf="!loading">
        <h3 class="section-heading">Starting Balance (Yesterday's End / Day before period)</h3>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="metric-card starting-card">
            <div class="metric-label">Starting Liquid Cash</div>
            <div class="metric-value text-amber-600">₹{{ (data.startingLiquidCash ?? 0) | number:'1.2-2' }}</div>
          </div>
          <div class="metric-card starting-card">
            <div class="metric-label">Starting Online Balance</div>
            <div class="metric-value text-blue-600">₹{{ (data.startingOnlineBalance ?? 0) | number:'1.2-2' }}</div>
          </div>
          <div class="metric-card starting-card">
            <div class="metric-label">Starting Total</div>
            <div class="metric-value text-gray-700">₹{{ (data.startingTotalBalance ?? 0) | number:'1.2-2' }}</div>
          </div>
        </div>
        <p class="starting-hint text-sm text-gray-500 mt-2">Today's starting = Yesterday's closing. All balances below include this carry-over.</p>
      </div>

      <!-- Closing Balance Cards: Starting + period movement -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6" *ngIf="!loading">
        <div class="metric-card liquid-cash-card">
          <div class="metric-icon" [ngClass]="(data.liquidCash ?? 0) >= 0 ? 'cash-positive-icon' : 'cash-negative-icon'">
            <mat-icon>payments</mat-icon>
          </div>
          <div class="metric-content">
            <div class="metric-label">Liquid Cash (Closing)</div>
            <div class="metric-value" [ngClass]="(data.liquidCash ?? 0) >= 0 ? 'text-amber-600' : 'text-red-600'">
              ₹{{ (data.liquidCash ?? 0) | number:'1.2-2' }}
            </div>
            <div class="metric-hint">Starting + (Cash received − Cash In (Cash)) in period</div>
          </div>
        </div>
        
        <div class="metric-card online-balance-card">
          <div class="metric-icon" [ngClass]="(data.onlineBalance ?? 0) >= 0 ? 'cash-positive-icon' : 'cash-negative-icon'">
            <mat-icon>account_balance</mat-icon>
          </div>
          <div class="metric-content">
            <div class="metric-label">Online Balance (Closing)</div>
            <div class="metric-value" [ngClass]="(data.onlineBalance ?? 0) >= 0 ? 'text-blue-600' : 'text-red-600'">
              ₹{{ (data.onlineBalance ?? 0) | number:'1.2-2' }}
            </div>
            <div class="metric-hint">Starting + (Online received − Cash In (Online)) in period</div>
          </div>
        </div>
        
        <div class="metric-card total-balance-card">
          <div class="metric-icon" [ngClass]="(data.totalAvailableBalance ?? 0) >= 0 ? 'cash-positive-icon' : 'cash-negative-icon'">
            <mat-icon>account_balance_wallet</mat-icon>
          </div>
          <div class="metric-content">
            <div class="metric-label">Total Available Balance (Closing)</div>
            <div class="metric-value" [ngClass]="(data.totalAvailableBalance ?? 0) >= 0 ? 'text-green-600' : 'text-red-600'">
              ₹{{ (data.totalAvailableBalance ?? 0) | number:'1.2-2' }}
            </div>
            <div class="metric-hint">Liquid Cash + Online Balance</div>
          </div>
        </div>
      </div>

      <!-- Received / Paid breakdown (optional row) -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6" *ngIf="!loading">
        <div class="metric-card clickable sales-card" (click)="showSalesDrillDown()">
          <div class="metric-label text-sm">Cash Received</div>
          <div class="metric-value text-green-600">₹{{ (data.totalCashReceived ?? 0) | number:'1.2-2' }}</div>
        </div>
        <div class="metric-card clickable sales-card" (click)="showSalesDrillDown()">
          <div class="metric-label text-sm">Online Received</div>
          <div class="metric-value text-blue-600">₹{{ (data.totalOnlineReceived ?? 0) | number:'1.2-2' }}</div>
        </div>
        <div class="metric-card clickable expense-card" (click)="showExpensesDrillDown()">
          <div class="metric-label text-sm">Cash In (Cash)</div>
          <div class="metric-value text-red-600">₹{{ (data.totalCashExpenses ?? 0) | number:'1.2-2' }}</div>
        </div>
        <div class="metric-card clickable expense-card" (click)="showExpensesDrillDown()">
          <div class="metric-label text-sm">Cash In (Online)</div>
          <div class="metric-value text-red-600">₹{{ (data.totalOnlineExpenses ?? 0) | number:'1.2-2' }}</div>
        </div>
      </div>

      <!-- Sales Drill-Down Table -->
      <mat-card class="table-card mb-6" *ngIf="showSales && !loading">
        <div class="table-header">
          <h3>Sales Entries (Money IN)</h3>
          <button mat-icon-button (click)="showSales = false">
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <table mat-table [dataSource]="salesDataSource" class="data-table">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let row">{{ row.date | date:'dd/MM/yyyy' }}</td>
          </ng-container>
          <ng-container matColumnDef="moduleType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let row">{{ row.moduleType }}</td>
          </ng-container>
          <ng-container matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef>Party</th>
            <td mat-cell *matCellDef="let row">{{ row.partyName }}</td>
          </ng-container>
          <ng-container matColumnDef="billNumber">
            <th mat-header-cell *matHeaderCellDef>Bill No</th>
            <td mat-cell *matCellDef="let row">{{ row.billNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="itemsTotal">
            <th mat-header-cell *matHeaderCellDef>Items Total</th>
            <td mat-cell *matCellDef="let row">₹{{ row.itemsTotal | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="receivedAmount">
            <th mat-header-cell *matHeaderCellDef>Received</th>
            <td mat-cell *matCellDef="let row" class="text-green-600">₹{{ row.receivedAmount | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="pendingAmount">
            <th mat-header-cell *matHeaderCellDef>Pending</th>
            <td mat-cell *matCellDef="let row" class="text-orange-600">₹{{ row.pendingAmount | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="paymentStatus">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <span [ngClass]="{
                'badge-paid': row.paymentStatus === 'PAID',
                'badge-partial': row.paymentStatus === 'PARTIAL',
                'badge-pending': row.paymentStatus === 'PENDING'
              }">{{ row.paymentStatus }}</span>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="salesColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: salesColumns"></tr>
        </table>
        <div class="table-summary">
          <strong>Total Received: ₹{{ getTotalSalesReceived() | number:'1.2-2' }}</strong>
        </div>
      </mat-card>

      <!-- Expenses Drill-Down Table -->
      <mat-card class="table-card mb-6" *ngIf="showExpenses && !loading">
        <div class="table-header">
          <h3>Cash In Entries (Money OUT)</h3>
          <button mat-icon-button (click)="showExpenses = false">
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <table mat-table [dataSource]="expenseDataSource" class="data-table">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let row">{{ row.date | date:'dd/MM/yyyy' }}</td>
          </ng-container>
          <ng-container matColumnDef="moduleType">
            <th mat-header-cell *matHeaderCellDef>Type</th>
            <td mat-cell *matCellDef="let row">{{ row.moduleType }}</td>
          </ng-container>
          <ng-container matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef>Party/Vendor</th>
            <td mat-cell *matCellDef="let row">{{ row.partyName || '-' }}</td>
          </ng-container>
          <ng-container matColumnDef="billNumber">
            <th mat-header-cell *matHeaderCellDef>Bill No</th>
            <td mat-cell *matCellDef="let row">{{ row.billNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="itemsTotal">
            <th mat-header-cell *matHeaderCellDef>Total</th>
            <td mat-cell *matCellDef="let row">₹{{ row.itemsTotal | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="paidAmount">
            <th mat-header-cell *matHeaderCellDef>Paid</th>
            <td mat-cell *matCellDef="let row" class="text-red-600">₹{{ row.paidAmount | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="pendingAmount">
            <th mat-header-cell *matHeaderCellDef>Outstanding</th>
            <td mat-cell *matCellDef="let row" class="text-orange-600">₹{{ row.pendingAmount | number:'1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="paymentStatus">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let row">
              <span [ngClass]="{
                'badge-paid': row.paymentStatus === 'PAID',
                'badge-partial': row.paymentStatus === 'PARTIAL',
                'badge-unpaid': row.paymentStatus === 'UNPAID'
              }">{{ row.paymentStatus }}</span>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="expenseColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: expenseColumns"></tr>
        </table>
        <div class="table-summary">
          <strong>Total Paid: ₹{{ getTotalExpensesPaid() | number:'1.2-2' }}</strong>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .report-container { padding: 0; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; gap: 16px; flex-wrap: wrap; }
    .header-actions { display: flex; gap: 12px; }
    .excel-btn { background-color: #217346; color: white; }
    .excel-btn:hover { background-color: #1a5c38; }
    .filter-card { padding: 16px; background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
    
    .filter-row { display: flex; flex-direction: column; gap: 16px; }
    .quick-filters { display: flex; flex-wrap: wrap; gap: 8px; }
    .quick-filter-btn { min-width: 100px; }
    .date-filters { display: flex; flex-wrap: wrap; gap: 16px; align-items: center; }
    .filter-field { flex: 0 0 auto; margin-bottom: 0; }
    .filter-date { width: 160px; min-width: 140px; }
    .filter-btn { height: 48px; }
    
    .date-range-display { display: flex; align-items: center; gap: 8px; margin-top: 12px; color: #6B7280; font-size: 14px; }
    .date-range-display mat-icon { font-size: 18px; width: 18px; height: 18px; }
    
    @media (max-width: 576px) {
      .filter-date { width: 100%; min-width: 100%; }
      .filter-btn { width: 100%; }
      .quick-filter-btn { min-width: 80px; font-size: 12px; }
    }
    
    .metric-card { 
      padding: 24px; 
      background: white; 
      border-radius: 12px; 
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); 
      display: flex; 
      gap: 16px; 
      align-items: center;
      transition: all 0.2s ease;
    }
    .metric-card.clickable { cursor: pointer; }
    .metric-card.clickable:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
    
    .metric-icon { 
      width: 56px; 
      height: 56px; 
      border-radius: 12px; 
      display: flex; 
      align-items: center; 
      justify-content: center; 
    }
    .metric-icon mat-icon { font-size: 28px; width: 28px; height: 28px; }
    .sales-icon { background: #DCFCE7; color: #16A34A; }
    .expense-icon { background: #FEE2E2; color: #DC2626; }
    .cash-positive-icon { background: #DBEAFE; color: #2563EB; }
    .cash-negative-icon { background: #FEE2E2; color: #DC2626; }
    
    .metric-content { flex: 1; }
    .metric-label { font-size: 14px; color: #6B7280; margin-bottom: 4px; }
    .metric-value { font-size: 28px; font-weight: 700; }
    .metric-hint { font-size: 12px; color: #9CA3AF; margin-top: 4px; }
    
    .text-green-600 { color: #16A34A; }
    .text-red-600 { color: #DC2626; }
    .text-blue-600 { color: #2563EB; }
    .text-amber-600 { color: #D97706; }
    .text-orange-600 { color: #EA580C; }
    .text-gray-700 { color: #374151; }
    
    .starting-balance-section { padding: 16px; background: #F3F4F6; border-radius: 12px; border: 1px solid #E5E7EB; }
    .section-heading { margin: 0 0 12px 0; font-size: 15px; font-weight: 600; color: #374151; }
    .starting-card .metric-value { font-size: 20px; }
    
    .table-card { padding: 16px; background: white; border-radius: 12px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); }
    .table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .table-header h3 { margin: 0; font-size: 16px; font-weight: 600; }
    .data-table { width: 100%; }
    .table-summary { margin-top: 16px; padding: 12px; background: #F9FAFB; border-radius: 8px; text-align: right; }
    
    .badge-paid { background: #10B981; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
    .badge-partial { background: #F59E0B; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
    .badge-pending { background: #6B7280; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
    .badge-unpaid { background: #EF4444; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
  `]
})
export class CashInHandReportComponent implements OnInit {
  filterType: string = 'MONTH';
  startDate: Date = new Date();
  endDate: Date = new Date();
  loading = false;
  showSales = false;
  showExpenses = false;
  
  data: CashInHandData = {
    startingLiquidCash: 0,
    startingOnlineBalance: 0,
    startingTotalBalance: 0,
    liquidCash: 0,
    onlineBalance: 0,
    totalAvailableBalance: 0,
    totalCashReceived: 0,
    totalOnlineReceived: 0,
    totalSalesReceived: 0,
    totalCashExpenses: 0,
    totalOnlineExpenses: 0,
    totalExpensesPaid: 0,
    salesEntries: [],
    expenseEntries: [],
    startDate: '',
    endDate: '',
    filterType: ''
  };
  
  salesDataSource = new MatTableDataSource<SalesEntry>([]);
  expenseDataSource = new MatTableDataSource<ExpenseEntry>([]);
  
  salesColumns = ['date', 'moduleType', 'partyName', 'billNumber', 'itemsTotal', 'receivedAmount', 'pendingAmount', 'paymentStatus'];
  expenseColumns = ['date', 'moduleType', 'partyName', 'billNumber', 'itemsTotal', 'paidAmount', 'pendingAmount', 'paymentStatus'];

  constructor(
    private apiService: ApiService,
    private http: HttpClient,
    private toastService: ToastService
  ) {
    // Default to this month
    const today = new Date();
    this.startDate = new Date(today.getFullYear(), today.getMonth(), 1);
    this.endDate = today;
  }

  ngOnInit() {
    this.loadData();
  }
  
  setFilter(filter: string) {
    this.filterType = filter;
    this.loadData();
  }

  loadData() {
    this.loading = true;
    
    let params: any = { filterType: this.filterType };
    if (this.filterType === 'CUSTOM') {
      params.startDate = this.startDate.toISOString().split('T')[0];
      params.endDate = this.endDate.toISOString().split('T')[0];
    }
    
    this.apiService.get<CashInHandData>('/cash-in-hand', params).subscribe({
      next: (response) => {
        this.data = response;
        this.salesDataSource.data = response.salesEntries || [];
        this.expenseDataSource.data = response.expenseEntries || [];
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Failed to load cash in hand data');
        this.loading = false;
      }
    });
  }
  
  showSalesDrillDown() {
    this.showSales = !this.showSales;
    if (this.showSales) {
      this.showExpenses = false;
    }
  }
  
  showExpensesDrillDown() {
    this.showExpenses = !this.showExpenses;
    if (this.showExpenses) {
      this.showSales = false;
    }
  }
  
  getTotalSalesReceived(): number {
    return this.data.salesEntries?.reduce((sum, entry) => sum + (entry.receivedAmount || 0), 0) || 0;
  }
  
  getTotalExpensesPaid(): number {
    return this.data.expenseEntries?.reduce((sum, entry) => sum + (entry.paidAmount || 0), 0) || 0;
  }
  
  formatLocalDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  downloadExcel() {
    const apiUrl = this.apiService.getBaseUrl();
    const dateStr = this.formatLocalDate(this.endDate);
    const url = `${apiUrl}/reports/cash-in-hand/excel?date=${dateStr}`;
    this.toastService.info('Downloading', 'Generating Excel...');
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (r: any) => {
        if (!r.body) { this.toastService.error('Error', 'No data'); return; }
        const a = document.createElement('a');
        a.href = window.URL.createObjectURL(r.body);
        a.download = 'cash_in_hand.xlsx';
        a.click();
        window.URL.revokeObjectURL(a.href);
        this.toastService.success('Success', 'Excel downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download Excel')
    });
  }

  downloadPdf() {
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    let url = `${apiUrl}/cash-in-hand/pdf?filterType=${this.filterType}`;
    if (this.filterType === 'CUSTOM') {
      url += `&startDate=${this.formatLocalDate(this.startDate)}`;
      url += `&endDate=${this.formatLocalDate(this.endDate)}`;
    }
    
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        let filename = 'cash_in_hand_report.pdf';
        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition) {
          const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1].replace(/['"]/g, '');
          }
        }
        
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
        this.toastService.success('Success', 'PDF downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download PDF');
      }
    });
  }
}
