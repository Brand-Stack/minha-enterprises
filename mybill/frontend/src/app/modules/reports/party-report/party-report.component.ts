import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-party-report',
  template: `
    <div class="report-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Party-wise Report</h1>
          <p class="body-small text-neutral-light">View transaction summary by party</p>
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
            <mat-label>Party</mat-label>
            <mat-select [(value)]="selectedParty" (selectionChange)="onPartySelected()">
              <mat-option value="">All Parties</mat-option>
              <mat-option *ngFor="let party of parties" [value]="party.id">{{ party.partyName }}</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field filter-select">
            <mat-label>Bill Type</mat-label>
            <mat-select [(value)]="billType">
              <mat-option value="">All Types</mat-option>
              <mat-option value="GST">Invoice</mat-option>
              <mat-option value="ESTIMATE">Estimate</mat-option>
            </mat-select>
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
          <div class="metric-label">Total Parties</div>
          <div class="metric-value">{{ summary.totalParties }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Bills</div>
          <div class="metric-value">{{ summary.totalBills }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Total Amount</div>
          <div class="metric-value">₹{{ summary.totalAmount | number:'1.2-2' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Avg per Party</div>
          <div class="metric-value">₹{{ summary.avgAmount | number:'1.2-2' }}</div>
        </div>
      </div>

      <!-- Data Table -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state">
          <app-loading-spinner></app-loading-spinner>
        </div>

        <table mat-table [dataSource]="dataSource" *ngIf="!loading" class="data-table">
          <ng-container *ngIf="!selectedParty" matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef class="table-header">Party Name</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.partyName }}</td>
          </ng-container>

          <ng-container *ngIf="selectedParty" matColumnDef="invoiceNumber">
            <th mat-header-cell *matHeaderCellDef class="table-header">Invoice No</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.invoiceNumber }}</td>
          </ng-container>

          <ng-container *ngIf="selectedParty" matColumnDef="invoiceDate">
            <th mat-header-cell *matHeaderCellDef class="table-header">Date</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.invoiceDate | date:'dd/MM/yyyy' }}</td>
          </ng-container>

          <ng-container *ngIf="selectedParty" matColumnDef="billType">
            <th mat-header-cell *matHeaderCellDef class="table-header">Type</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.billType }}</td>
          </ng-container>

          <ng-container *ngIf="!selectedParty" matColumnDef="billCount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Bills</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.billCount }}</td>
          </ng-container>

          <ng-container *ngIf="!selectedParty" matColumnDef="gstBills">
            <th mat-header-cell *matHeaderCellDef class="table-header">GST Bills</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.gstBills }}</td>
          </ng-container>

          <ng-container *ngIf="!selectedParty" matColumnDef="estimateBills">
            <th mat-header-cell *matHeaderCellDef class="table-header">Estimates</th>
            <td mat-cell *matCellDef="let row" class="table-cell">{{ row.estimateBills }}</td>
          </ng-container>

          <ng-container matColumnDef="totalAmount">
            <th mat-header-cell *matHeaderCellDef class="table-header">Total Amount</th>
            <td mat-cell *matCellDef="let row" class="table-cell">₹{{ row.totalAmount | number:'1.2-2' }}</td>
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
    .filter-btn { flex-shrink: 0; height: 48px; }
    
    @media (max-width: 768px) {
      .filter-row { gap: 12px; }
      .filter-date, .filter-select { width: calc(50% - 8px); min-width: 120px; }
    }
    @media (max-width: 576px) {
      .filter-date, .filter-select { width: 100%; min-width: 100%; }
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
  `]
})
export class PartyReportComponent implements OnInit {
  displayedColumns: string[] = ['partyName', 'billCount', 'gstBills', 'estimateBills', 'totalAmount'];
  dataSource = new MatTableDataSource<any>([]);
  loading = false;
  
  startDate: Date = new Date(new Date().setMonth(new Date().getMonth() - 1));
  endDate: Date = new Date();
  billType: string = '';
  selectedParty: string = '';
  parties: any[] = [];
  
  summary = {
    totalParties: 0,
    totalBills: 0,
    totalAmount: 0,
    avgAmount: 0
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
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load parties');
      }
    });
  }
  
  onPartySelected() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.apiService.getPaged<any>('/invoices', 0, 10000).subscribe({
      next: (response) => {
        let invoices = response.content;
        
        // Filter by date range
        invoices = invoices.filter((inv: any) => {
          const invDate = new Date(inv.invoiceDate);
          return invDate >= this.startDate && invDate <= this.endDate;
        });
        
        // Filter by bill type if selected
        if (this.billType) {
          invoices = invoices.filter((inv: any) => inv.billType === this.billType);
        }
        
        // Filter by party if selected
        if (this.selectedParty) {
          invoices = invoices.filter((inv: any) => inv.partyId === this.selectedParty);
          // Show complete bill details when party is selected
          this.displayedColumns = ['invoiceNumber', 'invoiceDate', 'billType', 'totalAmount'];
          this.dataSource.data = invoices.map((inv: any) => ({
            invoiceNumber: inv.invoiceNumber,
            invoiceDate: inv.invoiceDate,
            billType: inv.billType,
            totalAmount: inv.totalAmount || 0
          }));
        } else {
          // Group by party when no party selected
          this.displayedColumns = ['partyName', 'billCount', 'gstBills', 'estimateBills', 'totalAmount'];
          const partyMap = new Map<string, any>();
          invoices.forEach((inv: any) => {
            const partyId = inv.partyId;
            if (!partyMap.has(partyId)) {
              partyMap.set(partyId, {
                partyId: partyId,
                partyName: inv.partyName || 'Unknown',
                billCount: 0,
                gstBills: 0,
                estimateBills: 0,
                totalAmount: 0
              });
            }
            
            const data = partyMap.get(partyId);
            data.billCount++;
            data.totalAmount += inv.totalAmount || 0;
            
            if (inv.billType === 'GST') {
              data.gstBills++;
            } else if (inv.billType === 'ESTIMATE') {
              data.estimateBills++;
            }
          });
          
          const partyData = Array.from(partyMap.values()).sort((a, b) => b.totalAmount - a.totalAmount);
          this.dataSource.data = partyData;
        }
        
        if (this.selectedParty) {
          this.summary.totalParties = 1;
        } else {
          this.summary.totalParties = this.dataSource.data.length;
        }
        this.summary.totalBills = invoices.length;
        this.summary.totalAmount = invoices.reduce((sum, inv) => sum + (inv.totalAmount || 0), 0);
        this.summary.avgAmount = this.summary.totalParties > 0 ? this.summary.totalAmount / this.summary.totalParties : 0;
        
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load party data');
        this.loading = false;
      }
    });
  }

  formatLocalDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  downloadExcel() {
    const startDateStr = this.formatLocalDate(this.startDate);
    const endDateStr = this.formatLocalDate(this.endDate);
    const apiUrl = this.apiService.getBaseUrl();
    let url = `${apiUrl}/reports/party/excel?startDate=${startDateStr}&endDate=${endDateStr}`;
    if (this.billType) url += `&billType=${this.billType}`;
    this.toastService.info('Downloading', 'Generating Excel...');
    this.http.get(url, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (r: any) => {
        if (!r.body) { this.toastService.error('Error', 'No data'); return; }
        const a = document.createElement('a');
        a.href = window.URL.createObjectURL(r.body);
        a.download = 'party_report.xlsx';
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
    
    let url = `${apiUrl}/reports/party/pdf?startDate=${startDateStr}&endDate=${endDateStr}`;
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
        let filename = `party_report_${startDateStr}_to_${endDateStr}.pdf`;
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

