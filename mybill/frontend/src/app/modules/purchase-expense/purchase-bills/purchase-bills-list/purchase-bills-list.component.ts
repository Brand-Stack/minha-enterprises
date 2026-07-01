import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { HttpClient } from '@angular/common/http';

interface PurchaseBill {
  id: string;
  purchaseEntryNo?: string;
  billNumber: string; // Purchase Invoice Number
  billDate: string;
  partyName: string;
  totalAmount: number;
  paymentType: string;
  paymentStatus: string;
  lastUpdatedBy?: string;
}

@Component({
  selector: 'app-purchase-bills-list',
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>Purchase</h1>
          <p class="page-subtitle">Manage purchase bills from suppliers</p>
        </div>
        <button mat-raised-button color="primary" (click)="createNew()" *appHasPermission="'PURCHASE_BILLS:create'">
          <mat-icon>add</mat-icon>
          Add Purchase Bill
        </button>
      </div>

      <mat-card>
        <table mat-table [dataSource]="dataSource" class="w-full">
          <ng-container matColumnDef="purchaseEntryNo">
            <th mat-header-cell *matHeaderCellDef>Entry No</th>
            <td mat-cell *matCellDef="let row">{{ row.purchaseEntryNo || '-' }}</td>
          </ng-container>

          <ng-container matColumnDef="billNumber">
            <th mat-header-cell *matHeaderCellDef>Invoice No</th>
            <td mat-cell *matCellDef="let row">{{ row.billNumber }}</td>
          </ng-container>

          <ng-container matColumnDef="billDate">
            <th mat-header-cell *matHeaderCellDef>Date</th>
            <td mat-cell *matCellDef="let row">{{ row.billDate | date:'dd/MM/yyyy' }}</td>
          </ng-container>

          <ng-container matColumnDef="partyName">
            <th mat-header-cell *matHeaderCellDef>Supplier</th>
            <td mat-cell *matCellDef="let row">{{ row.partyName }}</td>
          </ng-container>

          <ng-container matColumnDef="totalAmount">
            <th mat-header-cell *matHeaderCellDef>Amount</th>
            <td mat-cell *matCellDef="let row">₹{{ row.totalAmount | number:'1.2-2' }}</td>
          </ng-container>

          <ng-container matColumnDef="paymentStatus">
            <th mat-header-cell *matHeaderCellDef>Payment Status</th>
            <td mat-cell *matCellDef="let row">
              <span [class]="row.paymentStatus === 'PAID' ? 'badge-paid' : 'badge-unpaid'">
                {{ row.paymentStatus }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="lastUpdatedBy">
            <th mat-header-cell *matHeaderCellDef>Last Updated By</th>
            <td mat-cell *matCellDef="let row">{{ row.lastUpdatedBy || '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Actions</th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button [matMenuTriggerFor]="menu">
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="view(row.id)">
                  <mat-icon>visibility</mat-icon>
                  <span>View</span>
                </button>
                <button mat-menu-item (click)="edit(row.id)" [appDisableIfNoPermission]="'PURCHASE_BILLS:edit'">
                  <mat-icon>edit</mat-icon>
                  <span>Edit</span>
                </button>
                <button mat-menu-item (click)="print(row.id)" *appHasPermission="'PURCHASE_BILLS:print'">
                  <mat-icon>print</mat-icon>
                  <span>Print</span>
                </button>
                <button mat-menu-item (click)="delete(row.id)" *appHasPermission="'PURCHASE_BILLS:delete'">
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
    .page-subtitle { color: #6B7280; font-size: 14px; margin-top: 4px; }
    table { width: 100%; }
    .badge-paid { 
      padding: 4px 8px; 
      background: #D1FAE5; 
      color: #065F46; 
      border-radius: 12px; 
      font-size: 12px; 
      font-weight: 500; 
    }
    .badge-unpaid { 
      padding: 4px 8px; 
      background: #FEE2E2; 
      color: #991B1B; 
      border-radius: 12px; 
      font-size: 12px; 
      font-weight: 500; 
    }
  `]
})
export class PurchaseBillsListComponent implements OnInit {
  displayedColumns: string[] = ['purchaseEntryNo', 'billNumber', 'billDate', 'partyName', 'totalAmount', 'paymentStatus', 'lastUpdatedBy', 'actions'];
  dataSource = new MatTableDataSource<PurchaseBill>([]);
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
    this.apiService.get<PageResponse<PurchaseBill>>('/purchase-bills', {
      page: this.page,
      size: this.pageSize,
      sortBy: 'createdAt',
      sortDir: 'desc'
    }).subscribe({
      next: (response) => {
        this.dataSource.data = response.content ?? [];
        this.totalElements = response.totalElements ?? 0;
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load purchase bills');
      }
    });
  }

  onPageChange(event: any) {
    this.page = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  createNew() {
    this.router.navigate(['/purchase-expense/purchase-bills/create']);
  }

  view(id: string) {
    this.router.navigate(['/purchase-expense/purchase-bills/view', id]);
  }

  edit(id: string) {
    this.router.navigate(['/purchase-expense/purchase-bills/edit', id]);
  }

  print(id: string) {
    this.toastService.info('Printing', 'Generating PDF...');
    const apiUrl = this.apiService.getBaseUrl();
    
    // Use /print endpoint and fetch with authentication headers
    this.http.get(`${apiUrl}/purchase-bills/${id}/print`, { 
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (response: any) => {
        const blob = response.body;
        if (!blob) {
          this.toastService.error('Error', 'No PDF data received');
          return;
        }
        
        // Create blob URL and open in new window for printing
        const url = window.URL.createObjectURL(blob);
        const printWindow = window.open(url, '_blank');
        if (printWindow) {
          printWindow.onload = () => {
            printWindow.print();
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

  delete(id: string) {
    if (confirm('Are you sure you want to delete this purchase bill?')) {
      this.apiService.delete('/purchase-bills', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Purchase bill deleted');
          this.loadData();
        },
        error: () => {
          this.toastService.error('Error', 'Failed to delete purchase bill');
        }
      });
    }
  }
}

