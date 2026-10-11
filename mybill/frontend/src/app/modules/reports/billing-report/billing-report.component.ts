import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-billing-report',
  template: `
    <div class="report-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Transaction Report</h1>
          <p class="body-small text-neutral-light">View and export transaction statistics</p>
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
            <mat-label>Bill Type</mat-label>
            <mat-select [(value)]="billType">
              <mat-option value="">All Types</mat-option>
              <mat-option value="GST">Invoice</mat-option>
              <mat-option value="ESTIMATE">Estimate</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-select">
            <mat-label>Party</mat-label>
            <mat-select [(value)]="partyId">
              <mat-option value="">All Parties</mat-option>
              <mat-option *ngFor="let party of parties" [value]="party.id">{{ party.partyName }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-text">
            <mat-label>Invoice Number</mat-label>
            <input matInput [(ngModel)]="invoiceNumber" placeholder="Search invoice number">
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
          <div class="metric-label">Total Transactions</div>
          <div class="metric-value">{{ summary.totalInvoices }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Estimates</div>
          <div class="metric-value">{{ summary.estimateCount }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">GST Bills</div>
          <div class="metric-value">{{ summary.gstCount }}</div>
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
          <ng-container matColumnDef="period">
            <th mat-header-cell *matHeaderCellDef class="table-header">Period</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.period }}</td>
          </ng-container>

          <ng-container matColumnDef="totalInvoices">
            <th mat-header-cell *matHeaderCellDef class="table-header">Total Transactions</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.totalInvoices }}</td>
          </ng-container>

          <ng-container matColumnDef="estimateCount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Estimates</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.estimateCount }}</td>
          </ng-container>

          <ng-container matColumnDef="gstBillCount">
            <th mat-header-cell *matHeaderCellDef class="table-header">GST Bills</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.gstBillCount }}</td>
          </ng-container>

          <ng-container matColumnDef="totalAmount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Amount</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.totalAmount | number:'1.2-2' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns" class="table-header-row"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns" class="table-row"></tr>
        </table>
      </mat-card>
    </div>
  `,
  styles: [`
    .report-container {
      padding: 0;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 24px;
      gap: 16px;
      flex-wrap: wrap;
    }

    .header-actions {
      display: flex;
      gap: 12px;
    }

    .excel-btn {
      background-color: #217346;
      color: white;
    }
    .excel-btn:hover {
      background-color: #1a5c38;
    }

    .filter-card, .table-card {
      background: white;
      border-radius: 12px;
      padding: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #E5E7EB;
    }

    /* Horizontal filter layout */
    .filter-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px;
    }

    .filter-field {
      flex: 0 0 auto;
      margin-bottom: 0;
    }

    .filter-date {
      width: 160px;
      min-width: 140px;
    }

    .filter-select {
      width: 160px;
      min-width: 140px;
    }

    .filter-text {
      width: 200px;
      min-width: 160px;
    }

    .filter-btn {
      flex-shrink: 0;
      height: 48px;
    }

    @media (max-width: 768px) {
      .filter-row {
        gap: 12px;
      }
      .filter-date, .filter-select, .filter-text {
        width: calc(50% - 8px);
        min-width: 120px;
      }
    }

    @media (max-width: 576px) {
      .filter-date, .filter-select, .filter-text {
        width: 100%;
        min-width: 100%;
      }
      .filter-btn {
        width: 100%;
        margin-top: 8px;
      }
    }

    .metric-card {
      background: white;
      border-radius: 12px;
      padding: 24px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #E5E7EB;
    }

    .metric-label {
      font-size: 14px;
      color: #6B7280;
      margin-bottom: 8px;
    }

    .metric-value {
      font-size: 28px;
      font-weight: 700;
      color: #1A1D2E;
    }

    .data-table {
      width: 100%;
    }

    .table-header {
      font-weight: 600 !important;
      font-size: 12px !important;
      text-transform: uppercase !important;
      color: #6B7280 !important;
      padding: 12px 16px !important;
      background: #F9FAFB;
    }

    .table-row:hover {
      background: #F9FAFB;
    }

    .loading-state {
      display: flex;
      justify-content: center;
      padding: 64px;
    }

    .text-neutral-light {
      color: #6B7280;
    }
  `]
})
export class BillingReportComponent implements OnInit {
  displayedColumns = ['period', 'totalInvoices', 'estimateCount', 'gstBillCount', 'totalAmount'];
  dataSource = new MatTableDataSource<any>([]);
  loading = false;
  
  startDate: Date = new Date(new Date().setMonth(new Date().getMonth() - 1));
  endDate: Date = new Date();
  groupBy: string = 'DAY';
  billType: string = ''; // Filter by bill type
  partyId: string = '';
  invoiceNumber: string = '';
  parties: any[] = [];
  
  summary = {
    totalInvoices: 0,
    estimateCount: 0,
    gstCount: 0,
    totalAmount: 0
  };

  constructor(
    private apiService: ApiService,
    private http: HttpClient,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    this.loadParties();
    this.loadData();
  }

  loadParties() {
    this.apiService.getPaged<any>('/clients', 0, 1000).subscribe({
      next: (response) => {
        this.parties = response.content;
      }
    });
  }

  loadData() {
    this.loading = true;
    // Use local date to avoid UTC timezone shift (e.g. India UTC+5:30: Jan 31 00:00 local -> Jan 30 in UTC)
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    
    // Use new backend endpoint with filters
    this.apiService.get<any[]>(`/reports/billing`, {
      startDate: startDateStr,
      endDate: endDateStr,
      billType: this.billType || undefined,
      partyId: this.partyId || undefined,
      invoiceNumber: this.invoiceNumber || undefined
    }).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.calculateSummaryFromData(data);
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load transaction data');
        this.loading = false;
      }
    });
  }

  calculateSummaryFromData(data: any[]) {
    this.summary = {
      totalInvoices: data.reduce((sum, d) => sum + (d.totalInvoices || 0), 0),
      estimateCount: data.reduce((sum, d) => sum + (d.estimateCount || 0), 0),
      gstCount: data.reduce((sum, d) => sum + (d.gstBillCount || 0), 0),
      totalAmount: data.reduce((sum, d) => sum + (d.totalAmount || 0), 0)
    };
  }
  
  groupInvoices(invoices: any[]): any[] {
    const grouped = new Map<string, any>();
    
    invoices.forEach(inv => {
      const period = this.getPeriodKey(new Date(inv.invoiceDate), this.groupBy);
      if (!grouped.has(period)) {
        grouped.set(period, {
          period: period,
          totalInvoices: 0,
          estimateCount: 0,
          gstBillCount: 0,
          totalAmount: 0,
          estimateAmount: 0,
          gstAmount: 0
        });
      }
      
      const data = grouped.get(period);
      data.totalInvoices++;
      data.totalAmount += inv.totalAmount || 0;
      
      if (inv.billType === 'ESTIMATE') {
        data.estimateCount++;
        data.estimateAmount += inv.totalAmount || 0;
      } else if (inv.billType === 'GST') {
        data.gstBillCount++;
        data.gstAmount += inv.totalAmount || 0;
      }
    });
    
    return Array.from(grouped.values()).sort((a, b) => a.period.localeCompare(b.period));
  }
  
  getPeriodKey(date: Date, groupBy: string): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    
    switch (groupBy) {
      case 'WEEK':
        const week = this.getWeekNumber(date);
        return `${year}-W${week}`;
      case 'MONTH':
        return `${year}-${month}`;
      case 'YEAR':
        return String(year);
      default: // DAY
        return `${year}-${month}-${day}`;
    }
  }
  
  getWeekNumber(date: Date): number {
    const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    const dayNum = d.getUTCDay() || 7;
    d.setUTCDate(d.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    return Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
  }

  calculateSummary(invoices: any[]) {
    this.summary.totalInvoices = invoices.length;
    this.summary.estimateCount = invoices.filter((inv: any) => inv.billType === 'ESTIMATE').length;
    this.summary.gstCount = invoices.filter((inv: any) => inv.billType === 'GST').length;
    this.summary.totalAmount = invoices.reduce((sum, inv) => sum + (inv.totalAmount || 0), 0);
  }

  downloadExcel() {
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    const apiUrl = this.apiService.getBaseUrl();
    
    this.toastService.info('Downloading', 'Generating Excel...');
    
    let url = `${apiUrl}/reports/billing/excel?startDate=${startDateStr}&endDate=${endDateStr}&groupBy=${this.groupBy}`;
    if (this.billType && this.billType !== '') {
      url += `&billType=${this.billType}`;
    }
    if (this.partyId && this.partyId !== '') {
      url += `&partyId=${this.partyId}`;
    }
    if (this.invoiceNumber && this.invoiceNumber !== '') {
      url += `&invoiceNumber=${encodeURIComponent(this.invoiceNumber)}`;
    }
    
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No Excel data received');
          return;
        }
        let filename = `transaction_report_${startDateStr}_to_${endDateStr}.xlsx`;
        const contentDisposition = response.headers.get('Content-Disposition');
        if (contentDisposition) {
          const match = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
          if (match && match[1]) {
            filename = match[1].replace(/['"]/g, '');
          }
        }
        const urlObj = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = urlObj;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(urlObj);
        this.toastService.success('Success', 'Excel downloaded successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download Excel');
      }
    });
  }

  downloadPdf() {
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    const apiUrl = this.apiService.getBaseUrl();
    
    this.toastService.info('Downloading', 'Generating PDF...');
    
    let url = `${apiUrl}/reports/billing/pdf?startDate=${startDateStr}&endDate=${endDateStr}&groupBy=${this.groupBy}`;
    if (this.billType && this.billType !== '') {
      url += `&billType=${this.billType}`;
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
        let filename = `transaction_report_${startDateStr}_to_${endDateStr}.pdf`;
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
        
        // Provide feedback with filename - helps EXE users know where file was saved
        const isExeMode = this.isRunningInExeMode();
        if (isExeMode) {
          this.toastService.success('Download Complete', `Report saved as "${filename}" in your Downloads folder`);
        } else {
          this.toastService.success('Success', 'PDF downloaded successfully');
        }
        
        window.URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to download PDF');
        console.error('PDF download error:', err);
      }
    });
  }
  
  /**
   * Detect if running in EXE/Desktop mode (JavaFX WebView)
   * In EXE mode, certain browser features may behave differently
   */
  /** Format date as YYYY-MM-DD using local timezone (avoids UTC shift) */
  private formatLocalDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private isRunningInExeMode(): boolean {
    // Check if running in JavaFX WebView or similar environment
    // JavaFX WebView user agent typically contains "JavaFX"
    const userAgent = navigator.userAgent.toLowerCase();
    const isJavaFX = userAgent.includes('javafx');
    
    // Also check if window.showSaveFilePicker is unavailable (desktop app indicator)
    const hasFilePicker = typeof (window as any).showSaveFilePicker === 'function';
    
    // In EXE mode: running in JavaFX or no file system API available
    return isJavaFX || (!hasFilePicker && window.location.protocol === 'file:');
  }
}

