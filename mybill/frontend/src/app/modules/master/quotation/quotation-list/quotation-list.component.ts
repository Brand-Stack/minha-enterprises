import { Component, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

export interface Quotation {
  id: string;
  quotationNumber: string;
  quotationDate: string;
  partyName: string;
  totalAmount: number;
  paymentMode: string;
  deliveryDate: string;
  validTillDays: number;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-quotation-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="h2">Quotations</h1>
          <p class="body-small text-neutral-light">Manage quotations</p>
        </div>
        <div class="header-actions">
          <mat-form-field appearance="outline" class="search-field">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="searchTerm" (keyup.enter)="runSearch()" placeholder="Search...">
            <mat-icon matSuffix>search</mat-icon>
          </mat-form-field>
          <button mat-raised-button color="primary" (click)="createNew()" class="btn-primary">
            <mat-icon>add</mat-icon>
            <span>Create Quotation</span>
          </button>
        </div>
      </div>

      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-state">
          <app-loading-spinner></app-loading-spinner>
        </div>
        <div *ngIf="!loading && dataSource.data.length > 0">
          <table mat-table [dataSource]="dataSource" class="data-table">
            <ng-container matColumnDef="quotationNumber">
              <th mat-header-cell *matHeaderCellDef>Quotation No</th>
              <td mat-cell *matCellDef="let item">{{ item.quotationNumber }}</td>
            </ng-container>
            <ng-container matColumnDef="quotationDate">
              <th mat-header-cell *matHeaderCellDef>Date</th>
              <td mat-cell *matCellDef="let item">{{ item.quotationDate | date }}</td>
            </ng-container>
            <ng-container matColumnDef="partyName">
              <th mat-header-cell *matHeaderCellDef>Party</th>
              <td mat-cell *matCellDef="let item">{{ item.partyName }}</td>
            </ng-container>
            <ng-container matColumnDef="totalAmount">
              <th mat-header-cell *matHeaderCellDef>Total</th>
              <td mat-cell *matCellDef="let item">₹{{ item.totalAmount }}</td>
            </ng-container>
            <ng-container matColumnDef="paymentMode">
              <th mat-header-cell *matHeaderCellDef>Payment Mode</th>
              <td mat-cell *matCellDef="let item">{{ item.paymentMode }}</td>
            </ng-container>
            <ng-container matColumnDef="lastUpdatedBy">
              <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
              <td mat-cell *matCellDef="let item">{{ item.lastUpdatedBy || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let item">
                <button mat-icon-button (click)="view(item.id)" matTooltip="View">
                  <mat-icon>visibility</mat-icon>
                </button>
                <button mat-icon-button (click)="edit(item.id)" matTooltip="Edit">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button (click)="printQuotation(item.id)" matTooltip="Print">
                  <mat-icon>print</mat-icon>
                </button>
                <button mat-icon-button (click)="downloadQuotation(item.id)" matTooltip="Download PDF">
                  <mat-icon>download</mat-icon>
                </button>
                <button mat-icon-button (click)="delete(item.id)" matTooltip="Delete" color="warn">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
          </table>
          <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
                         [pageSizeOptions]="[10, 20, 50]" showFirstLastButtons
                         (page)="onPageChange($event)"></mat-paginator>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-actions { display: flex; gap: 16px; align-items: center; }
    .search-field { width: 300px; }
    .table-card { padding: 0; }
    .data-table { width: 100%; }
    .loading-state { padding: 40px; text-align: center; }
  `]
})
export class QuotationListComponent implements OnInit, AfterViewInit {
  displayedColumns: string[] = ['quotationNumber', 'quotationDate', 'partyName', 'totalAmount', 'paymentMode', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<Quotation>([]);
  loading = false;
  searchTerm = '';
  totalElements = 0;
  page = 0;
  pageSize = 10;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toastService: ToastService,
    private http: HttpClient
  ) {}

  ngOnInit() {
    this.loadData();
  }

  ngAfterViewInit() {
    // Server-side pagination: do not assign dataSource.paginator
  }

  loadData() {
    this.loading = true;
    this.apiService.getPaged<Quotation>('/quotations', this.page, this.pageSize).subscribe({
      next: (response) => {
        this.dataSource.data = response.content ?? [];
        this.totalElements = response.totalElements ?? 0;
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Failed to load quotations');
        this.loading = false;
      }
    });
  }

  runSearch() {
    this.page = 0;
    if (!this.searchTerm.trim()) {
      this.loadData();
      return;
    }
    this.search();
  }

  search() {
    if (!this.searchTerm.trim()) {
      this.loadData();
      return;
    }
    this.loading = true;
    this.apiService.search<Quotation>('/quotations', this.searchTerm.trim(), this.page, this.pageSize).subscribe({
      next: (response) => {
        this.dataSource.data = response.content ?? [];
        this.totalElements = response.totalElements ?? 0;
        this.loading = false;
      },
      error: (err) => {
        this.toastService.error('Error', 'Search failed');
        this.loading = false;
      }
    });
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    if (this.searchTerm.trim()) {
      this.search();
    } else {
      this.loadData();
    }
  }

  createNew() {
    this.router.navigate(['/quotations/create']);
  }

  view(id: string) {
    this.router.navigate(['/quotations/view', id]);
  }

  edit(id: string) {
    this.router.navigate(['/quotations/edit', id]);
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this quotation?')) {
      this.apiService.delete('/quotations', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Quotation deleted successfully');
          this.loadData();
        },
        error: (err) => {
          this.toastService.error('Error', 'Failed to delete quotation');
        }
      });
    }
  }

  printQuotation(id: string) {
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    this.http.get(`${apiUrl}/quotations/${id}/print`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        const url = window.URL.createObjectURL(blob);
        const printWindow = window.open(url, '_blank');
        if (printWindow) {
          printWindow.onload = () => {
            setTimeout(() => {
              printWindow.print();
              setTimeout(() => {
                window.URL.revokeObjectURL(url);
              }, 1000);
            }, 500);
          };
        } else {
          this.toastService.error('Error', 'Please allow popups to print');
        }
        this.toastService.success('Success', 'PDF generated successfully');
      },
      error: (err) => {
        this.toastService.error('Error', err.error?.message || 'Failed to generate PDF');
        console.error('PDF generation error:', err);
      }
    });
  }

  downloadQuotation(id: string) {
    this.toastService.info('Downloading', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    this.http.get(`${apiUrl}/quotations/${id}/pdf`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        let filename = `quotation-${id}.pdf`;
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

