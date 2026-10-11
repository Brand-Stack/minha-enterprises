import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

interface PaymentOut {
  id: string;
  receiptNumber: string;
  date: string;
  partyName: string;
  paidAmount: number;
  paymentType: string;
}

@Component({
  selector: 'app-payment-out-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>Payment-Out</h1>
        </div>
        <div class="header-actions">
          <button mat-raised-button color="primary" (click)="createNew()" *appHasPermission="'PAYMENT_OUT:create'">
            <mat-icon>add</mat-icon>
            Add Payment-Out
          </button>
          <button mat-icon-button [matMenuTriggerFor]="settingsMenu">
            <mat-icon>settings</mat-icon>
          </button>
          <mat-menu #settingsMenu="matMenu">
            <button mat-menu-item>
              <mat-icon>tune</mat-icon>
              <span>Settings</span>
            </button>
          </mat-menu>
        </div>
      </div>

      <!-- Filters -->
      <mat-card class="filter-card">
        <div class="filter-row">
          <span>Filter by:</span>
          <mat-form-field appearance="outline">
            <mat-label>Start Date</mat-label>
            <input matInput [matDatepicker]="startPicker" [(ngModel)]="startDate">
            <mat-datepicker-toggle matSuffix [for]="startPicker"></mat-datepicker-toggle>
            <mat-datepicker #startPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>End Date</mat-label>
            <input matInput [matDatepicker]="endPicker" [(ngModel)]="endDate">
            <mat-datepicker-toggle matSuffix [for]="endPicker"></mat-datepicker-toggle>
            <mat-datepicker #endPicker></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Firm</mat-label>
            <mat-select [(ngModel)]="selectedFirm">
              <mat-option value="all">All Firms</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-raised-button (click)="applyFilters()">Apply</button>
        </div>
      </mat-card>

      <!-- Summary Card -->
      <mat-card class="summary-card">
        <div class="summary-row">
          <div>
            <div class="summary-label">Total Amount</div>
            <div class="summary-value">₹{{ totalAmount | number:'1.2-2' }}</div>
          </div>
          <div>
            <div class="summary-label">Paid</div>
            <div class="summary-value">₹{{ paidAmount | number:'1.2-2' }}</div>
          </div>
        </div>
      </mat-card>

      <!-- Transactions Table -->
      <mat-card>
        <div class="table-header">
          <h3>Transactions</h3>
          <div class="table-actions">
            <button mat-icon-button>
              <mat-icon>search</mat-icon>
            </button>
            <button mat-icon-button>
              <mat-icon>file_download</mat-icon>
            </button>
            <button mat-icon-button>
              <mat-icon>print</mat-icon>
            </button>
          </div>
        </div>

        <table mat-table [dataSource]="dataSource" class="w-full">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let row">{{ row.date | date:'dd/MM/yyyy' }}</td>
          </ng-container>

          <ng-container matColumnDef="receiptNumber">
            <th mat-header-cell *matHeaderCellDef>Ref. no.</th>
            <td mat-cell *matCellDef="let row">{{ row.receiptNumber }}</td>
          </ng-container>

          <ng-container matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef>Party Name</th>
            <td mat-cell *matCellDef="let row">{{ row.partyName }}</td>
          </ng-container>

          <ng-container matColumnDef="paidAmount">
            <th mat-header-cell *matHeaderCellDef>Paid</th>
            <td mat-cell *matCellDef="let row">₹{{ row.paidAmount | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="paymentType">
            <th mat-header-cell *matHeaderCellDef>Payment Type</th>
            <td mat-cell *matCellDef="let row">{{ row.paymentType }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button (click)="print(row.id)">
                <mat-icon>print</mat-icon>
              </button>
              <button mat-icon-button (click)="share(row.id)">
                <mat-icon>share</mat-icon>
              </button>
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="view(row.id)">
                  <mat-icon>visibility</mat-icon>
                  <span>View</span>
                </button>
                <button mat-menu-item (click)="edit(row.id)" [appDisableIfNoPermission]="'PAYMENT_OUT:edit'">
                  <mat-icon>edit</mat-icon>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="delete(row.id)" *appHasPermission="'PAYMENT_OUT:delete'">
                  <mat-icon>delete</mat-icon>
                  <span>Delete</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>

        <mat-paginator [length]="totalElements" [pageIndex]="page" [pageSize]="pageSize"
                       [pageSizeOptions]="[10, 25, 50, 100]" showFirstLastButtons
                       (page)="onPageChange($event)"></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    .header-actions { display: flex; gap: 8px; }
    .filter-card { margin-bottom: 16px; padding: 16px; }
    .filter-row { display: flex; gap: 16px; align-items: center; flex-wrap: wrap; }
    .summary-card { margin-bottom: 16px; padding: 16px; background: #F3F4F6; }
    .summary-row { display: flex; gap: 32px; }
    .summary-label { font-size: 14px; color: #6B7280; margin-bottom: 4px; }
    .summary-value { font-size: 24px; font-weight: 700; color: #1A1D2E; }
    .table-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .table-actions { display: flex; gap: 4px; }
    table { width: 100%; }
    input, textarea { caret-color: auto !important; }
  `]
})
export class PaymentOutListComponent implements OnInit {
  displayedColumns: string[] = ['date', 'receiptNumber', 'partyName', 'paidAmount', 'paymentType', 'actions'];
  dataSource = new MatTableDataSource<PaymentOut>([]);
  totalElements = 0;
  page = 0;
  pageSize = 10;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  startDate: Date | null = null;
  endDate: Date | null = null;
  selectedFirm = 'all';
  totalAmount = 0;
  paidAmount = 0;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    const today = new Date();
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    this.startDate = firstDay;
    this.endDate = lastDay;
    this.loadData();
  }

  ngAfterViewInit() {
    // Server-side pagination: do not assign dataSource.paginator
  }

  loadData() {
    if (this.startDate && this.endDate) {
      const params: any = {
        page: this.page,
        size: this.pageSize,
        startDate: this.startDate.toISOString().split('T')[0],
        endDate: this.endDate.toISOString().split('T')[0]
      };
      this.apiService.get<PageResponse<PaymentOut>>('/payment-out/filter', params).subscribe({
        next: (response) => {
          this.dataSource.data = response.content ?? [];
          this.totalElements = response.totalElements ?? 0;
          this.calculateTotals(response.content ?? []);
        },
        error: () => {
          this.toastService.error('Error', 'Failed to load payment-out records');
        }
      });
    } else {
      this.apiService.get<PageResponse<PaymentOut>>('/payment-out', {
        page: this.page,
        size: this.pageSize,
        sortBy: 'date',
        sortDir: 'desc'
      }).subscribe({
        next: (response) => {
          this.dataSource.data = response.content ?? [];
          this.totalElements = response.totalElements ?? 0;
          this.calculateTotals(response.content ?? []);
        },
        error: () => {
          this.toastService.error('Error', 'Failed to load payment-out records');
        }
      });
    }
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  calculateTotals(payments: PaymentOut[]) {
    this.totalAmount = payments.reduce((sum, p) => sum + (p.paidAmount || 0), 0);
    this.paidAmount = this.totalAmount;
  }

  applyFilters() {
    this.page = 0;
    this.loadData();
  }

  createNew() {
    this.router.navigate(['/purchase-expense/payment-out/create']);
  }

  view(id: string) {
    this.router.navigate(['/purchase-expense/payment-out/view', id]);
  }

  edit(id: string) {
    this.router.navigate(['/purchase-expense/payment-out/edit', id]);
  }

  print(id: string) {
    window.open(`${this.apiService.getBaseUrl()}/payment-out/${id}/print`, '_blank');
  }

  share(id: string) {
    // TODO: Implement share functionality
    this.toastService.info('Info', 'Share functionality coming soon');
  }

  delete(id: string) {
    if (confirm('Are you sure you want to delete this payment-out record?')) {
      this.apiService.delete('/payment-out', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Payment-out record deleted');
          this.loadData();
        },
        error: () => {
          this.toastService.error('Error', 'Failed to delete payment-out record');
        }
      });
    }
  }
}

