import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-stock-report',
  template: `
    <div class="report-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Stock Report</h1>
          <p class="body-small text-neutral-light">Current stock levels and status</p>
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
          <mat-form-field appearance="outline" class="filter-field filter-select">
            <mat-label>Category</mat-label>
            <mat-select [(value)]="categoryFilter">
              <mat-option value="">All Categories</mat-option>
              <mat-option *ngFor="let cat of categories" [value]="cat.name">{{ cat.name }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-text">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" placeholder="Search by name, code, EN Code">
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
          <div class="metric-label">In Stock</div>
          <div class="metric-value text-green-600">{{ summary.okCount }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Low Stock</div>
          <div class="metric-value text-orange-600">{{ summary.lowCount }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Out of Stock</div>
          <div class="metric-value text-red-600">{{ summary.outCount }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Value</div>
          <div class="metric-value">₹{{ summary.totalValue | number:'1.2-2' }}</div>
        </div>
      </div>

      <!-- Data Table -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state">
          <app-loading-spinner></app-loading-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" *ngIf="!loading" class="data-table">
          <ng-container matColumnDef="itemCode">
            <th mat-header-cell *matHeaderCellDef class="table-header">Code</th>
            <td mat-cell *matCellDef="let row" class="table-cell mono">{{ row.itemCode }}</td>
          </ng-container>

          <ng-container matColumnDef="itemName">
            <th mat-header-cell *matHeaderCellDef class="table-header">Item Name</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.itemName }}</td>
          </ng-container>

          <ng-container matColumnDef="currentStock">
            <th mat-header-cell *matHeaderCellDef class="table-header">Stock</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.currentStock }}</td>
          </ng-container>

          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef class="table-header">Status</th>
            <td mat-cell *matCellDef="let row" class="table-cell">
              <span class="badge-status" [ngClass]="getStatusClass(row.status)">
                {{ row.status }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="stockValue">
            <th mat-header-cell *matHeaderCellDef class="table-header">Value</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.stockValue | number:'1.2-2' }}</td>
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

    .header-actions { display: flex; gap: 12px; }
    .excel-btn { background-color: #217346; color: white; }
    .excel-btn:hover { background-color: #1a5c38; }

    .filter-card {
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

    .filter-select {
      width: 180px;
      min-width: 150px;
    }

    .filter-text {
      width: 250px;
      min-width: 200px;
    }

    .filter-btn {
      flex-shrink: 0;
      height: 48px;
    }

    @media (max-width: 768px) {
      .filter-row {
        gap: 12px;
      }
      .filter-select, .filter-text {
        width: calc(50% - 8px);
        min-width: 120px;
      }
    }

    @media (max-width: 576px) {
      .filter-select, .filter-text {
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

    .table-card {
      background: white;
      border-radius: 12px;
      padding: 16px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border: 1px solid #E5E7EB;
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

    .badge-status {
      display: inline-flex;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
    }

    .status-ok {
      background: #D1FAE5;
      color: #065F46;
    }

    .status-low {
      background: #FEF3C7;
      color: #92400E;
    }

    .status-out {
      background: #FEE2E2;
      color: #991B1B;
    }

    .mono {
      font-family: 'Roboto Mono', monospace;
    }

    .text-neutral-light {
      color: #6B7280;
    }

    .loading-state {
      display: flex;
      justify-content: center;
      padding: 64px;
    }
  `]
})
export class StockReportComponent implements OnInit {
  displayedColumns = ['itemCode', 'itemName', 'currentStock', 'status', 'stockValue'];
  dataSource = new MatTableDataSource<any>([]);
  loading = false;
  categoryFilter: string = '';
  searchTerm: string = '';
  categories: any[] = [];
  
  summary = {
    okCount: 0,
    lowCount: 0,
    outCount: 0,
    totalValue: 0
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
    this.apiService.get<any[]>('/reports/stock', {
      category: this.categoryFilter || undefined,
      search: this.searchTerm || undefined
    }).subscribe({
      next: (data) => {
        this.dataSource.data = data;
        this.calculateSummary(data);
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load stock data');
        this.loading = false;
      }
    });
  }

  calculateSummary(data: any[]) {
    this.summary.okCount = data.filter(d => d.status === 'OK').length;
    this.summary.lowCount = data.filter(d => d.status === 'LOW' || d.status === 'NEGATIVE').length;
    this.summary.outCount = data.filter(d => d.status === 'OUT_OF_STOCK').length;
    this.summary.totalValue = data.reduce((sum, row) => sum + (row.stockValue || 0), 0);
  }

  getStatusClass(status: string): string {
    if (status === 'OK') return 'status-ok';
    if (status === 'LOW') return 'status-low';
    if (status === 'NEGATIVE') return 'status-out';
    return 'status-out';
  }

  downloadExcel() {
    const apiUrl = this.apiService.getBaseUrl();
    const params = new URLSearchParams();
    if (this.categoryFilter) params.set('category', this.categoryFilter);
    if (this.searchTerm) params.set('search', this.searchTerm);
    const q = params.toString();
    this.toastService.info('Downloading', 'Generating Excel...');
    this.http.get(`${apiUrl}/reports/stock/excel${q ? '?' + q : ''}`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (r: any) => {
        if (!r.body) { this.toastService.error('Error', 'No data'); return; }
        const a = document.createElement('a');
        a.href = window.URL.createObjectURL(r.body);
        a.download = 'stock_report.xlsx';
        a.click();
        window.URL.revokeObjectURL(a.href);
        this.toastService.success('Success', 'Excel downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download Excel')
    });
  }

  downloadPdf() {
    const apiUrl = this.apiService.getBaseUrl();
    
    this.toastService.info('Downloading', 'Generating PDF...');
    
    this.http.get(`${apiUrl}/reports/stock/pdf`, { 
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
        let filename = `stock_report_${new Date().toISOString().split('T')[0]}.pdf`;
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

