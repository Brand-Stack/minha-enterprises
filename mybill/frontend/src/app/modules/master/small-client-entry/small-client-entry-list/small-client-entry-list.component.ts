import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { HttpClient } from '@angular/common/http';
import { SmallClientEntryQuotationService } from '../../../../core/services/small-client-entry.service';
import { SmallClientEntryQuotation } from '../../../../core/models/small-client-entry.model';
import { Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { MatDialog } from '@angular/material/dialog';
import {
  filenameFromContentDisposition,
  monthlyBreakupPdfFallback,
  monthlyInvoiceDownloadFilename
} from '../../../../core/utils/content-disposition-filename.util';

@Component({
  selector: 'app-pdf-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title class="pdf-dlg-title">Download PDF</h2>
    <mat-dialog-content class="pdf-dlg-content">
      <p class="pdf-dlg-lead">Include Shipment Breakup?</p>
      <p class="pdf-dlg-sub">Choose whether to add the shipment breakup annexure to this PDF. <strong>Yes</strong> includes it; <strong>No</strong> downloads the invoice only.</p>
    </mat-dialog-content>
    <mat-dialog-actions class="pdf-dlg-actions" align="center">
      <button mat-button type="button" class="pdf-dlg-btn" (click)="dialogRef.close(undefined)">Cancel</button>
      <button mat-stroked-button type="button" color="primary" class="pdf-dlg-btn" (click)="onChoice(false)">No, Invoice Only</button>
      <button mat-flat-button type="button" color="primary" class="pdf-dlg-btn pdf-dlg-btn-primary" (click)="onChoice(true)">Yes, Include Breakup</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .pdf-dlg-title {
      margin: 0;
      padding: 20px 24px 16px;
      font-size: 1.25rem;
      font-weight: 600;
      text-align: center;
      border-bottom: 1px solid #e5e7eb;
      box-sizing: border-box;
    }
    .pdf-dlg-content {
      margin: 0 !important;
      padding: 24px 28px !important;
      max-height: none !important;
      box-sizing: border-box;
    }
    .pdf-dlg-lead {
      margin: 0 0 10px;
      font-size: 1.0625rem;
      font-weight: 600;
      color: #111827;
      text-align: center;
      line-height: 1.45;
    }
    .pdf-dlg-sub {
      margin: 0;
      font-size: 0.9375rem;
      line-height: 1.55;
      color: #4b5563;
      text-align: center;
    }
    .pdf-dlg-actions {
      padding: 8px 20px 24px !important;
      margin: 0 !important;
      display: flex !important;
      flex-wrap: wrap;
      justify-content: center;
      align-items: center;
      gap: 10px 12px;
      box-sizing: border-box;
    }
    .pdf-dlg-btn { min-width: 132px; }
    .pdf-dlg-btn-primary { font-weight: 600; }
  `]
})
export class PdfConfirmDialogComponent {
  constructor(public dialogRef: MatDialogRef<PdfConfirmDialogComponent>) {}
  onChoice(includeBreakup: boolean): void {
    this.dialogRef.close(includeBreakup);
  }
}

@Component({
  selector: 'app-small-client-entry-list',
  template: `
    <div class="list-container">
      <div class="header-section">
        <h2>Small Client Entry</h2>
        <button *appHasPermission="'SMALL_CLIENT_ENTRY:create'" mat-raised-button color="primary" (click)="createNew()">
          <mat-icon>add</mat-icon> New Small Client Entry
        </button>
      </div>

      <mat-card class="search-card">
        <div class="search-filters search-row">
          <mat-form-field appearance="outline" class="filter-field filter-field-search-main">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="globalSearchText"
                   placeholder="Customer, month, year, zone, title, invoice, AWB, amount status…"
                   (keyup.enter)="onSearch()">
            <mat-hint>Matches customer, month/year, zone, title, invoice, amount status, note, totals, and shipment lines</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Customer</mat-label>
            <input matInput
                   [(ngModel)]="customerFilterText"
                   [matAutocomplete]="customerAuto"
                   (input)="onCustomerFilterInput()"
                   placeholder="Narrow by customer…">
            <mat-autocomplete #customerAuto="matAutocomplete"
                              [displayWith]="customerDisplay"
                              (optionSelected)="onCustomerOptionSelected($event)">
              <mat-option [value]="null">All customers</mat-option>
              <mat-option *ngFor="let c of filteredCustomersList" [value]="c">
                <span style="font-weight:600">{{ c.partyName }}</span>
                <span *ngIf="c.partyCode" style="color:#6B7280;font-size:12px;margin-left:8px">{{ c.partyCode }}</span>
              </mat-option>
              <mat-option *ngIf="filteredCustomersList.length === 0 && customerFilterText && (customerDisplay(customerFilterText) || '').trim().length > 0" disabled>
                No match
              </mat-option>
            </mat-autocomplete>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Zone</mat-label>
            <mat-select [(ngModel)]="searchZone" placeholder="All Zones">
              <mat-option [value]="null">All Zones</mat-option>
              <mat-option *ngFor="let z of zones" [value]="z.value">{{ z.label }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Month</mat-label>
            <mat-select [(ngModel)]="filterMonth" placeholder="All months">
              <mat-option [value]="null">All months</mat-option>
              <mat-option *ngFor="let m of monthOptions" [value]="m">{{ m }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Year</mat-label>
            <input matInput type="number" class="list-no-spinner" [(ngModel)]="filterYear" placeholder="e.g. 2025" (keyup.enter)="onSearch()">
          </mat-form-field>

          <div class="filter-actions">
            <button mat-raised-button color="primary" (click)="onSearch()">
              <mat-icon>search</mat-icon> Search
            </button>
            <button mat-stroked-button (click)="clearSearch()">
              <mat-icon>clear</mat-icon> Clear
            </button>
          </div>
        </div>
      </mat-card>

      <mat-card>
        <div class="table-container">
          <table mat-table [dataSource]="quotations">

            <!-- Title Column -->
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Title</th>
              <td mat-cell *matCellDef="let element"> {{element.title}} </td>
            </ng-container>

            <!-- Customer Column -->
            <ng-container matColumnDef="customer">
              <th mat-header-cell *matHeaderCellDef>Customer</th>
              <td mat-cell *matCellDef="let element"> {{element.customerName}} </td>
            </ng-container>

            <!-- Month / Year Column -->
            <ng-container matColumnDef="monthYear">
              <th mat-header-cell *matHeaderCellDef>Month / Year</th>
              <td mat-cell *matCellDef="let element"> {{element.month}} {{element.year}} </td>
            </ng-container>

            <!-- Shipments Column -->
            <ng-container matColumnDef="totalShipments">
              <th mat-header-cell *matHeaderCellDef>Total Shipments</th>
              <td mat-cell *matCellDef="let element"> {{element.totalShipments}} </td>
            </ng-container>

            <!-- Amount Column -->
            <ng-container matColumnDef="totalAmount">
              <th mat-header-cell *matHeaderCellDef>Total Amount</th>
              <td mat-cell *matCellDef="let element"> ₹ {{element.totalAmount | number:'1.2-2'}} </td>
            </ng-container>



            <!-- Actions Column -->
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="actions-header"> Actions </th>
              <td mat-cell *matCellDef="let element" class="actions-cell">
                <button *appHasPermission="'SMALL_CLIENT_ENTRY:download'" mat-icon-button (click)="downloadPdf(element.id)" matTooltip="Download PDF">
                  <mat-icon>picture_as_pdf</mat-icon>
                </button>
                <button mat-icon-button type="button" (click)="viewInvoice(element.id)" matTooltip="View Invoice">
                  <mat-icon>visibility</mat-icon>
                </button>
                <button *appHasPermission="'SMALL_CLIENT_ENTRY:print'" mat-icon-button type="button" (click)="printInvoice(element.id)" matTooltip="Print Invoice">
                  <mat-icon>print</mat-icon>
                </button>
                <button *appHasPermission="'SMALL_CLIENT_ENTRY:export'" mat-icon-button (click)="exportBreakupPdf(element)" matTooltip="Export Breakup PDF">
                  <mat-icon>description</mat-icon>
                </button>
                <button mat-icon-button color="primary" (click)="editData(element.id)" matTooltip="Edit/View" [appDisableIfNoPermission]="'SMALL_CLIENT_ENTRY:edit'">
                  <mat-icon>edit</mat-icon>
                </button>
                <button *appHasPermission="'SMALL_CLIENT_ENTRY:delete'" mat-icon-button color="warn" (click)="deleteData(element.id)" matTooltip="Delete">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
            
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty-cell" colspan="8">No quotations found.</td>
            </tr>
          </table>

          <mat-paginator [length]="totalElements"
                         [pageIndex]="pageIndex"
                         [pageSize]="pageSize"
                         [pageSizeOptions]="[5, 10, 25, 100]"
                         (page)="onPageChange($event)">
          </mat-paginator>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .list-container { padding: 24px; }
    .header-section { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    h2 { margin: 0; font-weight: 500; }
    .search-card { margin-bottom: 16px; }
    .search-filters { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; }
    .search-row { align-items: flex-end; }
    .search-row .filter-field { flex: 1 1 160px; min-width: 160px; }
    .search-row .filter-field-wide { flex: 2 1 220px; min-width: 220px; }
    .search-row .filter-field-search-main { flex: 1 1 100%; min-width: 240px; }
    .filter-field { min-width: 160px; }
    .filter-actions { display: flex; gap: 8px; }
    .table-container { overflow-x: auto; }
    table { width: 100%; }
    .empty-cell { text-align: center; padding: 24px !important; color: #6b7280; }
    .actions-header { text-align: right; }
    .actions-cell { text-align: right; }
    .status-badge {
      padding: 4px 8px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 500;
    }
    .draft { background-color: #f3f4f6; color: #374151; }
    .finalized { background-color: #d1fae5; color: #065f46; }
    .list-container input[type=number].list-no-spinner::-webkit-outer-spin-button,
    .list-container input[type=number].list-no-spinner::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
    .list-container input[type=number].list-no-spinner { -moz-appearance: textfield; appearance: textfield; }
  `]
})
export class SmallClientEntryListComponent implements OnInit {

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  displayedColumns: string[] = ['title', 'customer', 'monthYear', 'totalShipments', 'totalAmount', 'actions'];
  quotations: SmallClientEntryQuotation[] = [];
  customers: any[] = [];
  /** Subset of customers for autocomplete (filtered by typing). */
  filteredCustomersList: any[] = [];
  zones: any[] = [];

  searchCustomerId: string | null = null;
  /** Text in customer field (search / display). */
  customerFilterText = '';
  searchZone: string | null = null;
  filterMonth: string | null = null;
  filterYear: number | null = null;
  monthOptions = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];
  /** Backend global search: customer, month/year, zone, title, invoice, shipment lines, totals, etc. */
  globalSearchText = '';
  totalElements = 0;
  pageSize = 10;
  pageIndex = 0;

  constructor(
    private service: SmallClientEntryQuotationService,
    private apiService: ApiService,
    private zoneConfigService: ZoneConfigurationService,
    private http: HttpClient,
    private toastService: ToastService,
    private router: Router,
    private dialog: MatDialog
  ) { }

  ngOnInit(): void {
    this.loadCustomers();
    this.loadZones();
    this.loadData();
  }

  loadCustomers() {
    this.apiService.get<any>('/small-clients/type/CUSTOMER').subscribe({
      next: (list) => {
        this.customers = list || [];
        this.filteredCustomersList = [...this.customers];
      },
      error: () => {
        this.customers = [];
        this.filteredCustomersList = [];
      }
    });
  }

  customerDisplay = (c: any): string => {
    if (!c) return '';
    return typeof c === 'string' ? c : (c.partyName || c.clientName || c.name || c.customerName || '');
  };

  onCustomerFilterInput() {
    const raw = this.customerFilterText;
    const str = typeof raw === 'string' ? raw : (raw as any)?.partyName || (raw as any)?.clientName || (raw as any)?.name || '';
    const t = str.toLowerCase().trim();
    if (!t) {
      this.filteredCustomersList = [...this.customers];
      this.searchCustomerId = null;
      return;
    }
    this.filteredCustomersList = this.customers.filter((c: any) =>
      (c.partyName || '').toLowerCase().includes(t) ||
      (c.partyCode || '').toLowerCase().includes(t));
  }

  onCustomerOptionSelected(event: MatAutocompleteSelectedEvent) {
    const c = event.option.value as any;
    if (c == null) {
      this.searchCustomerId = null;
      this.customerFilterText = '';
      this.filteredCustomersList = [...this.customers];
      return;
    }
    this.searchCustomerId = c.id ?? null;
    this.customerFilterText = typeof c === 'string' ? c : (c.partyName || c.clientName || c.name || c.customerName || '');
  }

  loadZones() {
    this.zoneConfigService.getActiveZones().subscribe({
      next: (list) => this.zones = (list || []).map((z: any) => ({ value: z.id, label: z.zoneName })),
      error: () => this.zones = []
    });
  }

  loadData() {
    const filters: Record<string, string | number> = {};
    if (this.searchCustomerId) {
      filters['customerId'] = this.searchCustomerId;
    } else {
      const raw = this.customerFilterText;
      const ct = (typeof raw === 'string' ? raw : (raw as any)?.partyName || (raw as any)?.clientName || (raw as any)?.name || '').trim();
      if (ct) {
        filters['customerName'] = ct;
      }
    }
    if (this.searchZone) filters['zone'] = this.searchZone;
    if (this.filterMonth) filters['month'] = this.filterMonth;
    if (this.filterYear != null && !Number.isNaN(Number(this.filterYear))) {
      filters['year'] = Number(this.filterYear);
    }
    const g = (this.globalSearchText || '').trim();
    if (g) filters['search'] = g;
    this.service.getAll(this.pageIndex, this.pageSize, Object.keys(filters).length ? filters as any : undefined).subscribe({
      next: (res) => {
        this.quotations = res?.content ?? [];
        this.totalElements = res?.totalElements ?? 0;
      },
      error: () => {
        this.quotations = [];
        this.totalElements = 0;
      }
    });
  }

  onSearch() {
    this.pageIndex = 0;
    this.loadData();
  }

  clearSearch() {
    this.searchCustomerId = null;
    this.customerFilterText = '';
    this.filteredCustomersList = [...this.customers];
    this.searchZone = null;
    this.filterMonth = null;
    this.filterYear = null;
    this.globalSearchText = '';
    this.pageIndex = 0;
    if (this.paginator) this.paginator.pageIndex = 0;
    this.loadData();
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  downloadPdf(id: string) {
    const dialogRef = this.dialog.open(PdfConfirmDialogComponent, {
      width: '480px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
      disableClose: false,
      panelClass: 'pdf-download-dialog-panel'
    });

    dialogRef.afterClosed().subscribe(includeBreakup => {
      if (includeBreakup === undefined) return;

      this.toastService.info('Downloading', 'Generating PDF...');
      const apiUrl = this.apiService.getBaseUrl();
      const endpoint = `${apiUrl}/small-client-entries/${id}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=DOWNLOAD${!includeBreakup ? '&includeBreakup=false' : ''}`;
      
      this.http.get(endpoint, { responseType: 'blob', observe: 'response' }).subscribe({
        next: (res: any) => {
          const row = this.quotations.find(q => q.id === id);
          const fallback = monthlyInvoiceDownloadFilename(
            { customerName: row?.customerName, month: row?.month, year: row?.year },
            'pdf'
          );
          const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
          const url = window.URL.createObjectURL(res.body);
          const a = document.createElement('a');
          a.href = url;
          a.download = name;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          window.URL.revokeObjectURL(url);
          this.toastService.success('Success', 'PDF downloaded');
        },
        error: () => this.toastService.error('Error', 'Failed to download PDF')
      });
    });
  }

  viewInvoice(id: string): void {
    this.openInvoicePdf(id, 'VIEW');
  }

  printInvoice(id: string): void {
    this.openInvoicePdf(id, 'PRINT');
  }

  private openInvoicePdf(id: string, action: 'VIEW' | 'PRINT'): void {
    const endpoint = `${this.apiService.getBaseUrl()}/small-client-entries/${id}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=${action}`;
    this.http.get(endpoint, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        if (action === 'PRINT') {
          const frame = document.createElement('iframe');
          frame.style.position = 'fixed';
          frame.style.right = '0';
          frame.style.bottom = '0';
          frame.style.width = '0';
          frame.style.height = '0';
          frame.style.border = '0';
          frame.src = url;
          frame.onload = () => {
            frame.contentWindow?.focus();
            frame.contentWindow?.print();
          };
          document.body.appendChild(frame);
          setTimeout(() => {
            document.body.removeChild(frame);
            window.URL.revokeObjectURL(url);
          }, 60000);
          this.toastService.success('Print', 'Invoice print dialog opened');
        } else {
          window.open(url, '_blank', 'noopener');
          setTimeout(() => window.URL.revokeObjectURL(url), 60000);
          this.toastService.success('View', 'Invoice preview opened');
        }
      },
      error: () => this.toastService.error('Error', action === 'PRINT' ? 'Failed to print invoice' : 'Failed to view invoice')
    });
  }

  // Export shipment breakup as PDF (filtered data)
  exportBreakupPdf(row: SmallClientEntryQuotation) {
    const quotationId = row.id!;
    const endpoint = `${this.apiService.getBaseUrl()}/small-client-entries/${quotationId}/breakup-pdf`;
    this.http.get(endpoint, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        const fallback = monthlyBreakupPdfFallback({
          customerName: row.customerName,
          month: row.month,
          year: row.year
        });
        const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
        const url = window.URL.createObjectURL(res.body);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'Breakup PDF downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download breakup PDF')
    });
  }

  createNew() {
    this.router.navigate(['/small-client-entries/create']);
  }

  editData(id: string) {
    this.router.navigate(['/small-client-entries/edit', id]);
  }

  deleteData(id: string) {
    if (confirm('Are you sure you want to delete this quotation?')) {
      this.service.delete(id).subscribe(() => {
        this.loadData();
      });
    }
  }
}

