import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-purchase-report',
  template: `
    <div class="report-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Purchase Report</h1>
          <p class="body-small text-neutral-light">View purchase transactions and item-wise purchases</p>
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

      <!-- Filters -->
      <mat-card class="filter-card mb-6">
        <div class="filter-row">
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

          <mat-form-field appearance="outline" class="filter-field filter-select">
            <mat-label>Category</mat-label>
            <mat-select [(value)]="categoryFilter">
              <mat-option value="">All Categories</mat-option>
              <mat-option *ngFor="let cat of categories" [value]="cat.name">{{ cat.name }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-text">
            <mat-label>Item</mat-label>
            <input matInput [(ngModel)]="itemFilter" placeholder="Filter by item name or code">
          </mat-form-field>

          <button mat-raised-button (click)="loadData()" class="filter-btn">
            <mat-icon>search</mat-icon>
            <span>Search</span>
          </button>
        </div>
      </mat-card>

      <!-- Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-6">
        <div class="metric-card">
          <div class="metric-label">Total Purchases</div>
          <div class="metric-value">{{ summary.totalPurchases }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Items</div>
          <div class="metric-value">{{ summary.totalItems }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Quantity</div>
          <div class="metric-value">{{ summary.totalQuantity | number:'1.2-2' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Amount</div>
          <div class="metric-value">₹{{ summary.totalAmount | number:'1.2-2' }}</div>
        </div>
      </div>

      <!-- Data Table -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state">
          <app-loading-spinner></app-loading-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" *ngIf="!loading" class="data-table">
          <ng-container matColumnDef="billNumber">
            <th mat-header-cell *matHeaderCellDef class="table-header">Bill No</th>
            <td mat-cell *matCellDef="let row" class="table-cell mono">{{ row.billNumber || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="billDate">
            <th mat-header-cell *matHeaderCellDef class="table-header">Date</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.billDate | date:'dd/MM/yyyy' }}</td>
          </ng-container>

          <ng-container matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef class="table-header">Party</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.partyName }}</td>
          </ng-container>

          <ng-container matColumnDef="itemName">
            <th mat-header-cell *matHeaderCellDef class="table-header">Item</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.itemName }}</td>
          </ng-container>

          <ng-container matColumnDef="quantity">
            <th mat-header-cell *matHeaderCellDef class="table-header">Qty</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.quantity }}</td>
          </ng-container>

          <ng-container matColumnDef="unitPrice">
            <th mat-header-cell *matHeaderCellDef class="table-header">Unit Price</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.unitPrice | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="totalAmount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Total</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.totalAmount | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="paymentType">
            <th mat-header-cell *matHeaderCellDef class="table-header">Payment Type</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.paymentType || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="paymentStatus">
            <th mat-header-cell *matHeaderCellDef class="table-header">Status</th>
            <td mat-cell *matCellDef="let row" class="table-cell">
              <span [ngClass]="{
                'badge-paid': row.paymentStatus === 'PAID',
                'badge-unpaid': row.paymentStatus === 'UNPAID',
                'badge-partial': row.paymentStatus === 'PARTIAL'
              }">{{ row.paymentStatus || '—' }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="paidAmount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Paid</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.paidAmount | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="outstandingAmount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Outstanding</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.outstandingAmount | number:'1.2-2' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns" class="table-header-row"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns" class="table-row"></tr>
        </table>
      </mat-card>
    </div>
  `,
  styles: [`
    .report-container { padding: 0; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; gap: 16px; flex-wrap: wrap; }
    .header-actions { display: flex; gap: 12px; }
    .excel-btn { background-color: #217346; color: white; }
    .excel-btn:hover { background-color: #1a5c38; }
    .filter-card, .table-card { background: white; border-radius: 12px; padding: 16px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
    
    /* Horizontal filter layout */
    .filter-row { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; }
    .filter-field { flex: 0 0 auto; margin-bottom: 0; }
    .filter-date { width: 160px; min-width: 140px; }
    .filter-select { width: 170px; min-width: 150px; }
    .filter-text { width: 200px; min-width: 160px; }
    .filter-btn { flex-shrink: 0; height: 48px; }
    
    @media (max-width: 768px) {
      .filter-row { gap: 12px; }
      .filter-date, .filter-select, .filter-text { width: calc(50% - 8px); min-width: 120px; }
    }
    @media (max-width: 576px) {
      .filter-date, .filter-select, .filter-text { width: 100%; min-width: 100%; }
      .filter-btn { width: 100%; margin-top: 8px; }
    }
    
    .metric-card { background: white; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); border: 1px solid #E5E7EB; }
    .metric-label { font-size: 14px; color: #6B7280; margin-bottom: 8px; }
    .metric-value { font-size: 28px; font-weight: 700; color: #1A1D2E; }
    .data-table { width: 100%; }
    .table-header { font-weight: 600 !important; font-size: 12px !important; text-transform: uppercase !important; color: #6B7280 !important; padding: 12px 16px !important; background: #F9FAFB; }
    .table-row:hover { background: #F9FAFB; }
    .loading-state { display: flex; justify-content: center; padding: 64px; }
    .text-neutral-light { color: #6B7280; }
    .mono { font-family: 'Roboto Mono', monospace; font-size: 13px; }
    .badge-paid { background: #10B981; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
    .badge-unpaid { background: #EF4444; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
    .badge-partial { background: #F59E0B; color: white; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
  `]
})
export class PurchaseReportComponent implements OnInit {
  displayedColumns = ['billNumber', 'billDate', 'partyName', 'itemName', 'quantity', 'unitPrice', 'totalAmount', 'paymentType', 'paymentStatus', 'paidAmount', 'outstandingAmount'];
  dataSource = new MatTableDataSource<any>([]);
  loading = false;
  
  // Default to last 3 months to ensure we capture recent data
  startDate: Date = new Date(new Date().setMonth(new Date().getMonth() - 3));
  endDate: Date = new Date();
  categoryFilter: string = '';
  itemFilter: string = '';
  categories: any[] = [];
  
  summary = {
    totalPurchases: 0,
    totalItems: 0,
    totalQuantity: 0,
    totalAmount: 0
  };

  constructor(
    private apiService: ApiService,
    private http: HttpClient,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    this.loadCategories();
    this.loadData();
  }

  loadCategories() {
    this.apiService.get<any[]>('/master-data/type/ITEM_CATEGORY').subscribe({
      next: (categories) => {
        this.categories = categories;
      }
    });
  }

  loadData() {
    this.loading = true;
    const startDateStr = this.startDate.toISOString().split('T')[0];
    const endDateStr = this.endDate.toISOString().split('T')[0];
    
    // Use new backend endpoint with filters
    this.apiService.get<any[]>(`/reports/purchase`, {
      startDate: startDateStr,
      endDate: endDateStr,
      category: this.categoryFilter || undefined,
      item: this.itemFilter || undefined
    }).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.summary.totalPurchases = new Set(data.map(d => d.billNumber)).size;
        this.summary.totalItems = data.length;
        this.summary.totalQuantity = data.reduce((sum, row) => sum + (row.quantity || 0), 0);
        this.summary.totalAmount = data.reduce((sum, row) => sum + (row.totalAmount || 0), 0);
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load purchase data');
        this.loading = false;
      }
    });
  }

  formatLocalDate(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  downloadExcel() {
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    const apiUrl = this.apiService.getBaseUrl();
    let url = `${apiUrl}/reports/purchase/excel?startDate=${startDateStr}&endDate=${endDateStr}`;
    if (this.itemFilter) url += `&item=${encodeURIComponent(this.itemFilter)}`;
    if (this.categoryFilter) url += `&category=${encodeURIComponent(this.categoryFilter)}`;
    this.toastService.info('Downloading', 'Generating Excel...');
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (r: any) => {
        if (!r.body) { this.toastService.error('Error', 'No data'); return; }
        const a = document.createElement('a');
        a.href = window.URL.createObjectURL(r.body);
        a.download = 'purchase_report.xlsx';
        a.click();
        window.URL.revokeObjectURL(a.href);
        this.toastService.success('Success', 'Excel downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download Excel')
    });
  }

  downloadPdf() {
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    const apiUrl = this.apiService.getBaseUrl();
    
    this.toastService.info('Downloading', 'Generating PDF...');
    
    let url = `${apiUrl}/reports/purchase/pdf?startDate=${startDateStr}&endDate=${endDateStr}`;
    if (this.itemFilter && this.itemFilter.trim() !== '') {
      url += `&itemFilter=${encodeURIComponent(this.itemFilter.trim())}`;
    }
    
    this.http.get(url, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        // Extract filename from Content-Disposition header or use default
        let filename = `purchase_report_${startDateStr}_to_${endDateStr}.pdf`;
        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition) {
          const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1].replace(/['"]/g, '');
          }
        }
        
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'PDF downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download PDF');
        console.error('PDF download error:', err);
      }
    });
  }
}

