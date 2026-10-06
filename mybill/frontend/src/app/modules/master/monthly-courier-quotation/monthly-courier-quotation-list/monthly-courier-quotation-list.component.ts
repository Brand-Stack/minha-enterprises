import { Component, OnInit, ViewChild, Optional, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { HttpClient } from '@angular/common/http';
import { MonthlyCourierQuotationService } from '../../../../core/services/monthly-courier-quotation.service';
import { MonthlyCourierQuotation } from '../../../../core/models/monthly-courier-quotation.model';
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
import {
  canEditClientEntryRecord,
  CLIENT_ENTRY_LOCK_MESSAGE,
  isClientEntryRecordLocked
} from '../../../../core/util/client-entry-edit-lock.util';
import {
  clearListFilterState,
  loadListFilterState,
  saveListFilterState
} from '../../../../core/utils/list-filter-state.util';
import { PermissionService } from '../../../../core/services/permission.service';

const CLIENT_ENTRY_LIST_FILTER_KEY = 'client_entry_list_filters';

export interface PdfDialogResult {
  includeBreakup: boolean;
  includedFields: string[];
}

@Component({
  selector: 'app-pdf-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title class="pdf-dlg-title">
      <span>{{ title }}</span>
      <button mat-button class="pdf-dlg-toggle-all" type="button" (click)="toggleAll()">
        {{ isAllSelected() ? 'Unselect All' : 'Select All' }}
      </button>
    </h2>
    <mat-dialog-content class="pdf-dlg-content">
      <div class="pdf-field-section-title">Invoice Fields & Sections</div>
      <div class="pdf-field-grid">
        <label *ngFor="let field of availableFields" class="pdf-field-chip" [class.selected]="selectedFields[field.id]">
          <input type="checkbox" [checked]="selectedFields[field.id]" (change)="toggleField(field.id)">
          <span>{{ field.label }}</span>
        </label>
      </div>

      <div class="pdf-field-section-title" style="margin-top: 16px;">Shipment Breakup</div>
      <label class="pdf-breakup-toggle" [class.selected]="includeBreakup">
        <input type="checkbox" [checked]="includeBreakup" (change)="includeBreakup = !includeBreakup">
        <div>
          <div style="font-weight: 600; color: #1e293b;">Include Shipment Breakup Annexure</div>
          <div style="font-size: 12px; color: #64748b;">Append full list of shipment details with tracking and amounts</div>
        </div>
      </label>
    </mat-dialog-content>
    <mat-dialog-actions class="pdf-dlg-actions" align="end">
      <button mat-button type="button" class="pdf-dlg-btn" (click)="dialogRef.close(undefined)">Cancel</button>
      <button mat-flat-button color="primary" type="button" class="pdf-dlg-btn pdf-dlg-btn-primary" (click)="onDownload()">
        {{ confirmButtonText }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .pdf-dlg-title {
      margin: 0;
      padding: 16px 20px 14px;
      font-size: 1.15rem;
      font-weight: 600;
      color: #0f172a;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid #e2e8f0;
      box-sizing: border-box;
    }
    .pdf-dlg-toggle-all {
      font-size: 12px;
      font-weight: 600;
      color: #2563eb;
      min-width: auto;
      padding: 0 8px;
      height: 28px;
      line-height: 28px;
    }
    .pdf-dlg-content {
      margin: 0 !important;
      padding: 16px 20px !important;
      max-height: 68vh !important;
      box-sizing: border-box;
    }
    .pdf-field-section-title {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
      margin-bottom: 10px;
    }
    .pdf-field-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 8px 12px;
    }
    @media (max-width: 480px) {
      .pdf-field-grid { grid-template-columns: 1fr; }
    }
    .pdf-field-chip {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 10px;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      background: #f8fafc;
      font-size: 13px;
      color: #334155;
      cursor: pointer;
      user-select: none;
      transition: all 0.15s ease;
    }
    .pdf-field-chip.selected {
      background: #eff6ff;
      border-color: #93c5fd;
      color: #1d4ed8;
      font-weight: 500;
    }
    .pdf-field-chip input[type="checkbox"] {
      cursor: pointer;
      accent-color: #2563eb;
      margin: 0;
    }
    .pdf-breakup-toggle {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 14px;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      background: #ffffff;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .pdf-breakup-toggle.selected {
      background: #f0fdf4;
      border-color: #86efac;
    }
    .pdf-breakup-toggle input[type="checkbox"] {
      width: 18px;
      height: 18px;
      cursor: pointer;
      accent-color: #16a34a;
      margin: 0;
    }
    .pdf-dlg-actions {
      padding: 12px 20px 16px !important;
      margin: 0 !important;
      border-top: 1px solid #e2e8f0;
      gap: 8px;
    }
    .pdf-dlg-btn { min-width: 110px; font-weight: 500; border-radius: 6px !important; }
    .pdf-dlg-btn-primary { font-weight: 600; }
  `]
})
export class PdfConfirmDialogComponent {
  availableFields: { id: string; label: string }[] = [
    { id: 'customer', label: 'Customer Particulars' },
    { id: 'invoiceNo', label: 'Invoice Number' },
    { id: 'invoiceDate', label: 'Invoice Date' },
    { id: 'period', label: 'Billing Period' },
    { id: 'hsnSac', label: 'HSN / SAC Code' },
    { id: 'courierCharges', label: 'Courier Charges' },
    { id: 'fuelCharges', label: 'Fuel Charges' },
    { id: 'fovCharges', label: 'FOV Charges' },
    { id: 'subTotal', label: 'Sub Total' },
    { id: 'discount', label: 'Discount (Credit)' },
    { id: 'additionalCharges', label: 'Additional Charges' },
    { id: 'taxableAmount', label: 'Taxable Amount' },
    { id: 'gst', label: 'GST (CGST / SGST)' },
    { id: 'roundOff', label: 'Round Off' },
    { id: 'bankDetails', label: 'Bank & RTGS Details' },
    { id: 'receiverPhoneNo', label: 'Receiver Phone No' }
  ];

  selectedFields: Record<string, boolean> = {
    customer: true,
    invoiceNo: true,
    invoiceDate: true,
    period: true,
    hsnSac: true,
    courierCharges: true,
    fuelCharges: true,
    fovCharges: true,
    subTotal: true,
    discount: true,
    additionalCharges: true,
    taxableAmount: true,
    gst: true,
    roundOff: true,
    bankDetails: true,
    receiverPhoneNo: true
  };

  includeBreakup: boolean = true;
  title: string = 'Configure PDF Download';
  confirmButtonText: string = 'Download PDF';

  constructor(
    public dialogRef: MatDialogRef<PdfConfirmDialogComponent, PdfDialogResult>,
    @Optional() @Inject(MAT_DIALOG_DATA) public data?: { title?: string; confirmButtonText?: string; currentFields?: string[]; includeBreakup?: boolean }
  ) {
    if (this.data) {
      if (this.data.title) this.title = this.data.title;
      if (this.data.confirmButtonText) this.confirmButtonText = this.data.confirmButtonText;
      if (this.data.includeBreakup !== undefined) this.includeBreakup = this.data.includeBreakup;
      if (this.data.currentFields && Array.isArray(this.data.currentFields) && this.data.currentFields.length > 0) {
        const fieldSet = new Set(this.data.currentFields);
        this.availableFields.forEach(f => {
          this.selectedFields[f.id] = fieldSet.has(f.id);
        });
      }
    }
  }

  toggleField(fieldId: string): void {
    this.selectedFields[fieldId] = !this.selectedFields[fieldId];
  }

  isAllSelected(): boolean {
    return this.availableFields.every(f => this.selectedFields[f.id]);
  }

  toggleAll(): void {
    const newState = !this.isAllSelected();
    this.availableFields.forEach(f => this.selectedFields[f.id] = newState);
  }

  onDownload(): void {
    const includedFields = this.availableFields
      .filter(f => this.selectedFields[f.id])
      .map(f => f.id);
    this.dialogRef.close({
      includeBreakup: this.includeBreakup,
      includedFields
    });
  }
}

@Component({
  selector: 'app-monthly-courier-quotation-list',
  template: `
    <div class="list-container">
      <div class="header-section">
        <h2>Client Entry</h2>
        <button mat-raised-button color="primary" (click)="createNew()"
                *appHasPermission="'CLIENT_ENTRY:create'">
          <mat-icon>add</mat-icon> New client entry
        </button>
      </div>

      <mat-card class="search-card">
        <div class="search-filters search-row">
          <mat-form-field appearance="outline" class="filter-field filter-field-search-main">
            <mat-label>Search</mat-label>
            <input matInput [(ngModel)]="globalSearchText"
                   placeholder="Customer, month, year, zone, title, invoice, AWB, destination, courier, amount…"
                   (keyup.enter)="onSearch()">
            <mat-hint>Matches customer, month/year, zone, title, invoice, note, status, totals, and shipment lines</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Customer</mat-label>
            <input matInput
                   [(ngModel)]="customerFilterText"
                   [matAutocomplete]="customerAuto"
                   (input)="onCustomerFilterInput()"
                   placeholder="Narrow by customer…">
            <mat-autocomplete #customerAuto="matAutocomplete"
                              (optionSelected)="onCustomerOptionSelected($event)">
              <mat-option [value]="null">All customers</mat-option>
              <mat-option *ngFor="let c of filteredCustomersList" [value]="c">
                <span style="font-weight:600">{{ c.partyName }}</span>
                <span *ngIf="c.partyCode" style="color:#6B7280;font-size:12px;margin-left:8px">{{ c.partyCode }}</span>
              </mat-option>
              <mat-option *ngIf="filteredCustomersList.length === 0 && customerFilterText.trim().length > 0" disabled>
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
                <button mat-icon-button (click)="viewData(element)" matTooltip="View Entry">
                  <mat-icon>visibility</mat-icon>
                </button>
                <button mat-icon-button (click)="downloadPdf(element.id)" matTooltip="Download PDF"
                        *appHasPermission="'CLIENT_ENTRY:download'">
                  <mat-icon>picture_as_pdf</mat-icon>
                </button>
                <button mat-icon-button type="button" (click)="printInvoice(element.id)" matTooltip="Print Invoice"
                        *appHasPermission="'CLIENT_ENTRY:print'">
                  <mat-icon>print</mat-icon>
                </button>
                <button mat-icon-button (click)="exportBreakupPdf(element)" matTooltip="Export Breakup PDF"
                        *appHasPermission="'CLIENT_ENTRY:export'">
                  <mat-icon>description</mat-icon>
                </button>
                <button mat-icon-button color="primary" (click)="editData(element)"
                        [matTooltip]="getEditTooltip(element)"
                        [disabled]="!canEdit(element)">
                  <mat-icon>{{ canEdit(element) ? 'edit' : 'lock' }}</mat-icon>
                </button>
                <button mat-icon-button color="warn" (click)="deleteData(element.id)" matTooltip="Delete"
                        *appHasPermission="'CLIENT_ENTRY:delete'">
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
            
            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell empty-cell" colspan="7">No quotations found.</td>
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
export class MonthlyCourierQuotationListComponent implements OnInit {

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  displayedColumns: string[] = ['title', 'customer', 'monthYear', 'totalShipments', 'totalAmount', 'actions'];
  quotations: MonthlyCourierQuotation[] = [];
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
    private service: MonthlyCourierQuotationService,
    private apiService: ApiService,
    private zoneConfigService: ZoneConfigurationService,
    private http: HttpClient,
    private toastService: ToastService,
    private router: Router,
    private dialog: MatDialog,
    private permissionService: PermissionService
  ) { }

  canEdit(row: MonthlyCourierQuotation): boolean {
    if (!this.permissionService.hasPermission('CLIENT_ENTRY', 'edit')) return false;
    return canEditClientEntryRecord(row.month, row.year, this.permissionService.isAdmin());
  }

  getEditTooltip(row: MonthlyCourierQuotation): string {
    if (!this.permissionService.hasPermission('CLIENT_ENTRY', 'edit')) {
      return 'Edit access is not assigned to your role.';
    }
    if (!canEditClientEntryRecord(row.month, row.year, this.permissionService.isAdmin())) {
      return 'Past month entries are locked after the 5th. Please contact an administrator.';
    }
    return 'Edit';
  }

  ngOnInit(): void {
    this.loadCustomers();
    this.loadZones();
    this.restoreFilters();
    this.loadData();
  }

  private persistFilters(): void {
    saveListFilterState(CLIENT_ENTRY_LIST_FILTER_KEY, {
      globalSearchText: this.globalSearchText,
      searchCustomerId: this.searchCustomerId,
      customerFilterText: this.customerFilterText,
      searchZone: this.searchZone,
      filterMonth: this.filterMonth,
      filterYear: this.filterYear,
      pageIndex: this.pageIndex,
      pageSize: this.pageSize
    });
  }

  private restoreFilters(): void {
    const saved = loadListFilterState<Record<string, unknown>>(CLIENT_ENTRY_LIST_FILTER_KEY);
    if (!saved) return;
    if (saved['globalSearchText'] != null) this.globalSearchText = String(saved['globalSearchText']);
    if (saved['searchCustomerId'] != null) this.searchCustomerId = String(saved['searchCustomerId']);
    if (saved['customerFilterText'] != null) this.customerFilterText = String(saved['customerFilterText']);
    if (saved['searchZone'] != null) this.searchZone = String(saved['searchZone']);
    if (saved['filterMonth'] != null) this.filterMonth = String(saved['filterMonth']);
    if (saved['filterYear'] != null && saved['filterYear'] !== '') {
      this.filterYear = Number(saved['filterYear']);
    }
    if (typeof saved['pageIndex'] === 'number') this.pageIndex = saved['pageIndex'];
    if (typeof saved['pageSize'] === 'number') this.pageSize = saved['pageSize'];
  }

  loadCustomers() {
    this.apiService.get<any>('/clients/type/CUSTOMER').subscribe({
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

  onCustomerFilterInput() {
    const t = (this.customerFilterText || '').toLowerCase().trim();
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
    this.customerFilterText = c.partyName ?? '';
  }

  loadZones() {
    this.zoneConfigService.getActiveZones().subscribe({
      next: (list) => this.zones = (list || []).map((z: any) => ({ value: z.id, label: z.zoneName })),
      error: () => this.zones = []
    });
  }

  loadData() {
    this.persistFilters();
    const filters: Record<string, string | number> = {};
    if (this.searchCustomerId) {
      filters['customerId'] = this.searchCustomerId;
    } else {
      const ct = (this.customerFilterText || '').trim();
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
    clearListFilterState(CLIENT_ENTRY_LIST_FILTER_KEY);
    this.loadData();
  }

  onPageChange(event: PageEvent) {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadData();
  }

  downloadPdf(id: string) {
    const dialogRef = this.dialog.open(PdfConfirmDialogComponent, {
      width: '540px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
      disableClose: false,
      panelClass: 'pdf-download-dialog-panel'
    });

    dialogRef.afterClosed().subscribe((res: any) => {
      if (res === undefined) return;
      const includeBreakup = typeof res === 'boolean' ? res : (res?.includeBreakup ?? true);
      const includedFields: string[] | undefined = res?.includedFields;

      this.toastService.info('Downloading', 'Generating PDF...');
      const apiUrl = this.apiService.getBaseUrl();
      let endpoint = `${apiUrl}/monthly-courier-quotations/${id}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=DOWNLOAD${!includeBreakup ? '&includeBreakup=false' : ''}`;
      if (includedFields && includedFields.length > 0) {
        endpoint += `&includedFields=${encodeURIComponent(includedFields.join(','))}`;
      }
      
      this.http.get(endpoint, { responseType: 'blob', observe: 'response' }).subscribe({
        next: (response: any) => {
          const row = this.quotations.find(q => q.id === id);
          const fallback = monthlyInvoiceDownloadFilename(
            { customerName: row?.customerName, month: row?.month, year: row?.year },
            'pdf'
          );
          const name = filenameFromContentDisposition(response.headers?.get('content-disposition'), fallback);
          const url = window.URL.createObjectURL(response.body);
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
    const endpoint = `${this.apiService.getBaseUrl()}/monthly-courier-quotations/${id}/pdf?includeAmount=true&includeGstAndFuel=true&includeWeight=true&invoiceAction=${action}`;
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
  exportBreakupPdf(row: MonthlyCourierQuotation) {
    const quotationId = row.id!;
    const endpoint = `${this.apiService.getBaseUrl()}/monthly-courier-quotations/${quotationId}/breakup-pdf`;
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
    this.persistFilters();
    this.router.navigate(['/client-entries/create']);
  }

  viewData(row: MonthlyCourierQuotation) {
    this.persistFilters();
    this.router.navigate(['/client-entries/edit', row.id]);
  }

  editData(row: MonthlyCourierQuotation) {
    if (!this.canEdit(row)) {
      return;
    }
    this.persistFilters();
    this.router.navigate(['/client-entries/edit', row.id]);
  }

  deleteData(id: string) {
    if (confirm('Are you sure you want to delete this quotation?')) {
      this.service.delete(id).subscribe(() => {
        this.loadData();
      });
    }
  }
}
