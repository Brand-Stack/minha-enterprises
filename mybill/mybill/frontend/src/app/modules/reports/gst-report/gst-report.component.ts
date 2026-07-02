import { Component, OnInit, ViewChild, AfterViewInit } from '@angular/core';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ExcelExportService } from '../../../core/services/excel-export.service';
import { PermissionService } from '../../../core/services/permission.service';
import { FormBuilder, FormGroup, FormGroupDirective, Validators } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';

interface GstInRecord {
  serialNo: number;
  clientName: string;
  billingAmount: number;
  cgst: number;
  sgst: number;
  gstTotal: number;
}

@Component({
  selector: 'app-gst-report',
  template: `
    <div class="gst-page-container">
      <!-- Header -->
      <header class="gst-app-header mat-elevation-z1">
        <div class="header-titles">
          <h1>GST Report</h1>
          <p class="subtitle">Tax compliance, ledger declarations and manual invoice registration</p>
        </div>
        
        <div class="header-toolbar">
          <!-- Month Selector -->
          <div class="month-picker">
            <mat-form-field appearance="outline" class="picker-field">
              <mat-label>Month</mat-label>
              <mat-select [(ngModel)]="selectedMonth" (selectionChange)="onMonthYearChange()">
                <mat-option *ngFor="let m of months" [value]="m">{{ m }}</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="picker-field picker-field--sm">
              <mat-label>Year</mat-label>
              <input matInput type="number" [(ngModel)]="selectedYear" (change)="onMonthYearChange()">
            </mat-form-field>
          </div>

          <!-- Actions -->
          <div class="header-actions-row">
            <button mat-flat-button color="primary" (click)="generateGstIn()" *appHasPermission="'GST_REPORT:generate_gst_in'" [disabled]="loadingIn">
              <mat-icon>analytics</mat-icon> Generate GST IN
            </button>
            <ng-container *ngIf="generated && gstInList.length > 0">
              <button mat-stroked-button color="accent" (click)="regenerateGstIn()" *appHasPermission="'GST_REPORT:regenerate_gst_in'" [disabled]="loadingIn">
                <mat-icon>autorenew</mat-icon> Regenerate
              </button>
            </ng-container>
            <button mat-stroked-button (click)="exportPdf()" *appHasPermission="'GST_REPORT:export_pdf'" [disabled]="gstInList.length === 0 && gstOutList.length === 0">
              <mat-icon>picture_as_pdf</mat-icon> Export PDF
            </button>
            <button mat-stroked-button (click)="exportExcel()" *appHasPermission="'GST_REPORT:export_excel'" [disabled]="gstInList.length === 0 && gstOutList.length === 0">
              <mat-icon>table_chart</mat-icon> Export Excel
            </button>
            <button mat-stroked-button (click)="printReport()" *appHasPermission="'GST_REPORT:print'" [disabled]="gstInList.length === 0 && gstOutList.length === 0">
              <mat-icon>print</mat-icon> Print
            </button>
          </div>
        </div>
      </header>

      <!-- KPI Summary Cards -->
      <section class="gst-summary-grid">
        <div class="kpi-card kpi-card--in">
          <div class="kpi-icon"><mat-icon>trending_up</mat-icon></div>
          <div class="kpi-details">
            <span class="kpi-label">GST IN (Auto)</span>
            <h2 class="kpi-val">₹ {{ gstInTotal | number:'1.2-2' }}</h2>
          </div>
        </div>
        <div class="kpi-card kpi-card--out">
          <div class="kpi-icon"><mat-icon>trending_down</mat-icon></div>
          <div class="kpi-details">
            <span class="kpi-label">GST OUT (Manual)</span>
            <h2 class="kpi-val">₹ {{ gstOutTotal | number:'1.2-2' }}</h2>
          </div>
        </div>
        <div class="kpi-card kpi-card--payable" [ngClass]="{'kpi-card--active': gstPayable > 0}">
          <div class="kpi-icon"><mat-icon>account_balance</mat-icon></div>
          <div class="kpi-details">
            <span class="kpi-label">GST Payable</span>
            <h2 class="kpi-val">₹ {{ gstPayable | number:'1.2-2' }}</h2>
          </div>
        </div>
        <div class="kpi-card kpi-card--receivable" [ngClass]="{'kpi-card--active': gstReceivable > 0}">
          <div class="kpi-icon"><mat-icon>price_check</mat-icon></div>
          <div class="kpi-details">
            <span class="kpi-label">GST Receivable</span>
            <h2 class="kpi-val">₹ {{ gstReceivable | number:'1.2-2' }}</h2>
          </div>
        </div>
        <div class="kpi-card kpi-card--period">
          <div class="kpi-icon"><mat-icon>calendar_today</mat-icon></div>
          <div class="kpi-details">
            <span class="kpi-label">Active Period</span>
            <h2 class="kpi-val kpi-val--period">{{ selectedMonth }} {{ selectedYear }}</h2>
          </div>
        </div>
      </section>

      <!-- Main Tabs Section -->
      <mat-tab-group animationDuration="250ms" class="gst-tab-group mat-elevation-z1">
        <!-- GST IN Tab -->
        <mat-tab *ngIf="permission.hasPermission('GST_REPORT', 'view_gst_in')">
          <ng-template matTabLabel>
            <mat-icon class="tab-icon">assignment_returned</mat-icon>
            <span>GST IN (Auto Generated)</span>
          </ng-template>
          
          <div class="tab-content-wrapper">
            <div class="section-actions-bar">
              <div class="section-title">
                <h3>Auto Generated GST IN Records</h3>
                <p>Derived from client invoices generated during this month</p>
              </div>
              <mat-form-field appearance="outline" class="search-filter-field">
                <mat-label>Search Clients</mat-label>
                <mat-icon matPrefix>search</mat-icon>
                <input matInput (keyup)="applyGstInFilter($event)" placeholder="Search client name...">
              </mat-form-field>
            </div>

            <!-- Loader -->
            <div class="loading-overlay" *ngIf="loadingIn">
              <mat-spinner diameter="40"></mat-spinner>
              <span>Fetching and compiling invoice tax records...</span>
            </div>

            <!-- Table -->
            <div class="table-scroll-container" *ngIf="!loadingIn && gstInList.length > 0">
              <table mat-table [dataSource]="gstInDataSource" matSort #sortIn="matSort" class="gst-custom-table">
                <!-- S.No Column -->
                <ng-container matColumnDef="serialNo">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>S.No</th>
                  <td mat-cell *matCellDef="let row">{{ row.serialNo }}</td>
                </ng-container>

                <!-- Client Name Column -->
                <ng-container matColumnDef="clientName">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Client Name</th>
                  <td mat-cell *matCellDef="let row" class="client-name-cell">{{ row.clientName }}</td>
                </ng-container>

                <!-- Billing Amount Column -->
                <ng-container matColumnDef="billingAmount">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">Billing Amount</th>
                  <td mat-cell *matCellDef="let row" class="num-column">₹ {{ row.billingAmount | number:'1.2-2' }}</td>
                </ng-container>

                <!-- CGST Column -->
                <ng-container matColumnDef="cgst">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">CGST</th>
                  <td mat-cell *matCellDef="let row" class="num-column">₹ {{ row.cgst | number:'1.2-2' }}</td>
                </ng-container>

                <!-- SGST Column -->
                <ng-container matColumnDef="sgst">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">SGST</th>
                  <td mat-cell *matCellDef="let row" class="num-column">₹ {{ row.sgst | number:'1.2-2' }}</td>
                </ng-container>

                <!-- GST Total Column -->
                <ng-container matColumnDef="gstTotal">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">GST Total</th>
                  <td mat-cell *matCellDef="let row" class="num-column total-cell">₹ {{ row.gstTotal | number:'1.2-2' }}</td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="gstInDisplayedColumns; sticky: true"></tr>
                <tr mat-row *matRowDef="let row; columns: gstInDisplayedColumns;" class="table-row-hover"></tr>
              </table>
              
              <mat-paginator #paginatorIn="matPaginator" [pageSizeOptions]="[10, 25, 50]" showFirstLastButtons class="table-paginator"></mat-paginator>
            </div>

            <!-- Empty State -->
            <div class="empty-state-box" *ngIf="!loadingIn && gstInList.length === 0">
              <mat-icon>find_in_page</mat-icon>
              <h4>No GST IN records available</h4>
              <p>Choose a compliance month and click "Generate GST IN" in the header to import client entries.</p>
            </div>
          </div>
        </mat-tab>

        <!-- GST OUT Tab -->
        <mat-tab *ngIf="permission.hasPermission('GST_REPORT', 'view_gst_out')">
          <ng-template matTabLabel>
            <mat-icon class="tab-icon">assignment_returned</mat-icon>
            <span>GST OUT (Manual Input)</span>
          </ng-template>

          <div class="tab-content-wrapper">
            <!-- Add Card Form -->
            <div class="form-card-container mat-elevation-z1" *ngIf="permission.hasPermission('GST_REPORT', 'add_gst_out')">
              <div class="form-card-header">
                <mat-icon>add_chart</mat-icon>
                <span>Register Outward Taxable Invoice</span>
              </div>
              <form [formGroup]="gstOutForm" #gstOutFormDirective="ngForm" (ngSubmit)="addGstOut()" class="gst-out-grid-form">
                <mat-form-field appearance="outline">
                  <mat-label>Invoice Number</mat-label>
                  <input matInput formControlName="invoiceNumber" placeholder="e.g. OUT-1002">
                  <mat-error *ngIf="gstOutForm.get('invoiceNumber')?.hasError('required')">Invoice number required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Month</mat-label>
                  <mat-select formControlName="month">
                    <mat-option *ngFor="let m of months" [value]="m">{{ m }}</mat-option>
                  </mat-select>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Year</mat-label>
                  <input matInput type="number" formControlName="year">
                  <mat-error *ngIf="gstOutForm.get('year')?.hasError('required')">Year is required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Courier Type</mat-label>
                  <mat-select formControlName="courierType">
                    <mat-option *ngFor="let c of courierOptions" [value]="c">{{ c }}</mat-option>
                  </mat-select>
                  <mat-error *ngIf="gstOutForm.get('courierType')?.hasError('required')">Select courier type</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>Billing Amount</mat-label>
                  <span matPrefix>₹&nbsp;</span>
                  <input matInput type="number" formControlName="billingAmount" placeholder="0.00">
                  <mat-error *ngIf="gstOutForm.get('billingAmount')?.hasError('required')">Billing amount required</mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>CGST (9%)</mat-label>
                  <span matPrefix>₹&nbsp;</span>
                  <input matInput type="number" formControlName="cgst" placeholder="0.00">
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>SGST (9%)</mat-label>
                  <span matPrefix>₹&nbsp;</span>
                  <input matInput type="number" formControlName="sgst" placeholder="0.00">
                </mat-form-field>

                <mat-form-field appearance="outline">
                  <mat-label>GST Total</mat-label>
                  <span matPrefix>₹&nbsp;</span>
                  <input matInput type="number" formControlName="gstTotal" placeholder="0.00">
                </mat-form-field>

                <button mat-flat-button color="primary" type="submit" [disabled]="gstOutForm.invalid" class="submit-form-btn">
                  <mat-icon>add</mat-icon> Save Record
                </button>
              </form>
            </div>

            <!-- Table Actions -->
            <div class="section-actions-bar">
              <div class="section-title">
                <h3>Manual Outward Declarations</h3>
                <p>Manage and review custom outward tax invoices</p>
              </div>
              <div class="out-filters-row">
                <mat-checkbox [(ngModel)]="showAllOutMonths" (change)="onToggleShowAllOut()" class="all-months-cb">
                  Show entries from all months
                </mat-checkbox>
                <mat-form-field appearance="outline" class="search-filter-field">
                  <mat-label>Search Invoice No</mat-label>
                  <mat-icon matPrefix>search</mat-icon>
                  <input matInput (keyup)="applyGstOutFilter($event)" placeholder="Search invoice...">
                </mat-form-field>
              </div>
            </div>

            <!-- Loader -->
            <div class="loading-overlay" *ngIf="loadingOut">
              <mat-spinner diameter="40"></mat-spinner>
            </div>

            <!-- Grid Table -->
            <div class="table-scroll-container" *ngIf="!loadingOut && filteredGstOutList.length > 0">
              <table mat-table [dataSource]="gstOutDataSource" matSort #sortOut="matSort" class="gst-custom-table">
                <!-- S.No Column -->
                <ng-container matColumnDef="serialNo">
                  <th mat-header-cell *matHeaderCellDef>S.No</th>
                  <td mat-cell *matCellDef="let row; let idx = index">{{ idx + 1 }}</td>
                </ng-container>

                <!-- Invoice Number -->
                <ng-container matColumnDef="invoiceNumber">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Invoice No</th>
                  <td mat-cell *matCellDef="let row">
                    <span *ngIf="editingId !== row.id">{{ row.invoiceNumber }}</span>
                    <input *ngIf="editingId === row.id" class="gst-inline-input" [(ngModel)]="editDraft.invoiceNumber">
                  </td>
                </ng-container>

                <!-- Month -->
                <ng-container matColumnDef="month">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Month</th>
                  <td mat-cell *matCellDef="let row">
                    <span *ngIf="editingId !== row.id">{{ row.month }}</span>
                    <select *ngIf="editingId === row.id" class="gst-inline-select" [(ngModel)]="editDraft.month">
                      <option *ngFor="let m of months" [value]="m">{{ m }}</option>
                    </select>
                  </td>
                </ng-container>

                <!-- Year -->
                <ng-container matColumnDef="year">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Year</th>
                  <td mat-cell *matCellDef="let row">
                    <span *ngIf="editingId !== row.id">{{ row.year }}</span>
                    <input *ngIf="editingId === row.id" type="number" class="gst-inline-input gst-inline-input--year" [(ngModel)]="editDraft.year">
                  </td>
                </ng-container>

                <!-- Courier Type -->
                <ng-container matColumnDef="courierType">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header>Courier Type</th>
                  <td mat-cell *matCellDef="let row">
                    <span *ngIf="editingId !== row.id">{{ row.courierType }}</span>
                    <select *ngIf="editingId === row.id" class="gst-inline-select" [(ngModel)]="editDraft.courierType">
                      <option *ngFor="let c of courierOptions" [value]="c">{{ c }}</option>
                    </select>
                  </td>
                </ng-container>

                <!-- Billing Amount -->
                <ng-container matColumnDef="billingAmount">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">Billing Amount</th>
                  <td mat-cell *matCellDef="let row" class="num-column">
                    <span *ngIf="editingId !== row.id">₹ {{ row.billingAmount | number:'1.2-2' }}</span>
                    <input *ngIf="editingId === row.id" type="number" class="gst-inline-input gst-inline-input--num" [(ngModel)]="editDraft.billingAmount" (input)="onEditAmountChange()">
                  </td>
                </ng-container>

                <!-- CGST -->
                <ng-container matColumnDef="cgst">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">CGST</th>
                  <td mat-cell *matCellDef="let row" class="num-column">
                    <span *ngIf="editingId !== row.id">₹ {{ row.cgst | number:'1.2-2' }}</span>
                    <input *ngIf="editingId === row.id" type="number" class="gst-inline-input gst-inline-input--num" [(ngModel)]="editDraft.cgst" (input)="onEditTaxChange()">
                  </td>
                </ng-container>

                <!-- SGST -->
                <ng-container matColumnDef="sgst">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">SGST</th>
                  <td mat-cell *matCellDef="let row" class="num-column">
                    <span *ngIf="editingId !== row.id">₹ {{ row.sgst | number:'1.2-2' }}</span>
                    <input *ngIf="editingId === row.id" type="number" class="gst-inline-input gst-inline-input--num" [(ngModel)]="editDraft.sgst" (input)="onEditTaxChange()">
                  </td>
                </ng-container>

                <!-- GST Total -->
                <ng-container matColumnDef="gstTotal">
                  <th mat-header-cell *matHeaderCellDef mat-sort-header class="num-column">GST Total</th>
                  <td mat-cell *matCellDef="let row" class="num-column total-cell">
                    <span *ngIf="editingId !== row.id">₹ {{ row.gstTotal | number:'1.2-2' }}</span>
                    <input *ngIf="editingId === row.id" type="number" class="gst-inline-input gst-inline-input--num" [(ngModel)]="editDraft.gstTotal">
                  </td>
                </ng-container>

                <!-- Actions Column -->
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef class="actions-column">Actions</th>
                  <td mat-cell *matCellDef="let row" class="actions-column">
                    <div class="row-actions-flex">
                      <!-- Regular view actions -->
                      <ng-container *ngIf="editingId !== row.id">
                        <button mat-icon-button color="primary" type="button" (click)="startEdit(row)" matTooltip="Edit invoice" *appHasPermission="'GST_REPORT:edit_gst_out'">
                          <mat-icon>edit</mat-icon>
                        </button>
                        <button mat-icon-button color="warn" type="button" (click)="deleteGstOut(row.id)" matTooltip="Delete invoice" *appHasPermission="'GST_REPORT:delete_gst_out'">
                          <mat-icon>delete</mat-icon>
                        </button>
                      </ng-container>
                      <!-- Editing actions -->
                      <ng-container *ngIf="editingId === row.id">
                        <button mat-icon-button color="primary" type="button" (click)="saveEdit()" matTooltip="Save changes">
                          <mat-icon>check</mat-icon>
                        </button>
                        <button mat-icon-button type="button" (click)="cancelEdit()" matTooltip="Cancel editing">
                          <mat-icon>close</mat-icon>
                        </button>
                      </ng-container>
                    </div>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="gstOutDisplayedColumns; sticky: true"></tr>
                <tr mat-row *matRowDef="let row; columns: gstOutDisplayedColumns;" class="table-row-hover"></tr>
              </table>
              <mat-paginator #paginatorOut="matPaginator" [pageSizeOptions]="[10, 25, 50]" showFirstLastButtons class="table-paginator"></mat-paginator>
            </div>

            <!-- Empty State -->
            <div class="empty-state-box" *ngIf="!loadingOut && filteredGstOutList.length === 0">
              <mat-icon>assignment_late</mat-icon>
              <h4>No outward entries registered</h4>
              <p>All manual invoices recorded for this period will appear in this grid.</p>
            </div>
          </div>
        </mat-tab>

        <!-- Consolidated Ledger Tab -->
        <mat-tab *ngIf="permission.hasPermission('GST_REPORT', 'view_ledger')">
          <ng-template matTabLabel>
            <mat-icon class="tab-icon">account_balance_wallet</mat-icon>
            <span>Consolidated GST Ledger</span>
          </ng-template>

          <div class="tab-content-wrapper">
            <div class="section-actions-bar">
              <div class="section-title">
                <h3>Compliance & Tax Ledger Summary</h3>
                <p>Comparative summary of monthly inward and outward liability differences</p>
              </div>
              <div class="out-filters-row">
                <mat-form-field appearance="outline" class="search-filter-field">
                  <mat-label>Search Month/Year</mat-label>
                  <mat-icon matPrefix>search</mat-icon>
                  <input matInput [(ngModel)]="ledgerSearch" (input)="filterLedger()" placeholder="e.g. JUNE">
                </mat-form-field>
                <button mat-stroked-button type="button" (click)="toggleLedgerSort()" class="sort-action-btn">
                  <mat-icon>sort</mat-icon> Sort Order
                </button>
              </div>
            </div>

            <!-- Loader -->
            <div class="loading-overlay" *ngIf="loadingLedger">
              <mat-spinner diameter="40"></mat-spinner>
            </div>

            <!-- Ledger Grid -->
            <div class="ledger-cards-container" *ngIf="!loadingLedger && paginatedLedgerList.length > 0">
              <mat-card class="ledger-card-modern" *ngFor="let item of paginatedLedgerList">
                <div class="card-top-header">
                  <span class="period-title">{{ item.month }} {{ item.year }}</span>
                  <span class="badge-status-pill" [ngClass]="{
                    'pill-payable': item.status === 'GST Payable',
                    'pill-receivable': item.status === 'GST Receivable',
                    'pill-balanced': item.difference === 0
                  }">
                    {{ item.difference === 0 ? 'Balanced' : item.status }}
                  </span>
                </div>
                
                <div class="card-details-body">
                  <div class="detail-row">
                    <span class="lbl">GST IN (Total Purchases)</span>
                    <span class="val">₹ {{ item.gstInTotal | number:'1.2-2' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="lbl">GST OUT (Total Sales)</span>
                    <span class="val">₹ {{ item.gstOutTotal | number:'1.2-2' }}</span>
                  </div>
                  <div class="row-divider"></div>
                  <div class="detail-row net-difference-row">
                    <span class="lbl">Liability Difference</span>
                    <span class="val" [ngClass]="item.gstInTotal > item.gstOutTotal ? 'text-danger' : 'text-success'">
                      ₹ {{ item.difference | number:'1.2-2' }}
                    </span>
                  </div>
                </div>
              </mat-card>
            </div>

            <mat-paginator *ngIf="!loadingLedger && filteredLedgerList.length > 0"
                           #paginatorLedger
                           [length]="filteredLedgerList.length"
                           [pageSize]="6"
                           [pageSizeOptions]="[6, 12, 24]"
                           (page)="onLedgerPageChange($event)"
                           class="ledger-paginator">
            </mat-paginator>

            <!-- Empty State -->
            <div class="empty-state-box" *ngIf="!loadingLedger && filteredLedgerList.length === 0">
              <mat-icon>assessment</mat-icon>
              <h4>No ledger records compiled</h4>
              <p>Generated records and manually registered items will create ledger summaries here.</p>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .gst-page-container {
      padding: 24px;
      max-width: 1400px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 24px;
      background: #f8fafc;
      min-height: 100vh;
      box-sizing: border-box;
    }

    /* Page Header styling */
    .gst-app-header {
      background: #ffffff;
      padding: 20px 24px;
      border-radius: 14px;
      border: 1px solid #e2e8f0;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 20px;
    }
    .header-titles h1 {
      margin: 0;
      font-size: 1.7rem;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
    }
    .header-titles .subtitle {
      margin: 4px 0 0 0;
      font-size: 0.88rem;
      color: #64748b;
    }
    .header-toolbar {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .month-picker {
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .picker-field {
      width: 150px;
      margin-bottom: -1.25em;
    }
    .picker-field--sm {
      width: 100px;
    }
    .header-actions-row {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
    }
    .header-actions-row button {
      height: 48px;
      border-radius: 8px;
      font-weight: 500;
    }
    .header-actions-row mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      margin-right: 6px;
    }

    /* Summary Grid */
    .gst-summary-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
    }
    .kpi-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      transition: all 0.2s ease;
    }
    .kpi-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
    }
    .kpi-icon {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .kpi-icon mat-icon {
      font-size: 22px;
      width: 22px;
      height: 22px;
    }
    .kpi-details {
      display: flex;
      flex-direction: column;
    }
    .kpi-label {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
    }
    .kpi-val {
      margin: 2px 0 0 0;
      font-size: 1.25rem;
      font-weight: 700;
      color: #0f172a;
    }
    .kpi-val--period {
      font-size: 1.15rem;
    }

    /* Card coloring variants */
    .kpi-card--in .kpi-icon { background: #eff6ff; color: #2563eb; }
    .kpi-card--out .kpi-icon { background: #faf5ff; color: #8b5cf6; }
    .kpi-card--payable .kpi-icon { background: #f1f5f9; color: #64748b; }
    .kpi-card--receivable .kpi-icon { background: #f1f5f9; color: #64748b; }
    .kpi-card--period .kpi-icon { background: #f0fdf4; color: #16a34a; }

    .kpi-card--payable.kpi-card--active { background: #fef2f2; border-color: #fca5a5; }
    .kpi-card--payable.kpi-card--active .kpi-icon { background: #fee2e2; color: #dc2626; }
    .kpi-card--payable.kpi-card--active .kpi-val { color: #b91c1c; }

    .kpi-card--receivable.kpi-card--active { background: #f0fdf4; border-color: #bbf7d0; }
    .kpi-card--receivable.kpi-card--active .kpi-icon { background: #dcfce7; color: #16a34a; }
    .kpi-card--receivable.kpi-card--active .kpi-val { color: #15803d; }

    /* Tabs Container */
    .gst-tab-group {
      background: #ffffff;
      border-radius: 14px;
      border: 1px solid #e2e8f0;
      overflow: hidden;
    }
    .tab-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      margin-right: 8px;
    }
    .tab-content-wrapper {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* Actions row */
    .section-actions-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
      border-bottom: 1px solid #f1f5f9;
      padding-bottom: 16px;
    }
    .section-title h3 {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
    }
    .section-title p {
      margin: 2px 0 0 0;
      font-size: 12px;
      color: #64748b;
    }
    .search-filter-field {
      width: 280px;
      margin-bottom: -1.25em;
    }
    .out-filters-row {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .all-months-cb {
      font-size: 13px;
      color: #475569;
    }

    /* Table styles */
    .table-scroll-container {
      width: 100%;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      background: #ffffff;
      overflow: hidden;
    }
    .gst-custom-table {
      width: 100%;
    }
    .gst-custom-table th.mat-mdc-header-cell {
      background: #f8fafc;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: #475569;
      text-transform: uppercase;
      border-bottom: 2px solid #e2e8f0;
      padding: 14px 16px;
    }
    .gst-custom-table td.mat-mdc-cell {
      padding: 12px 16px;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
      font-size: 13.5px;
    }
    .table-row-hover:hover {
      background: #f8fafc;
    }
    .client-name-cell {
      font-weight: 500;
      color: #0f172a;
    }
    .num-column {
      text-align: right !important;
      font-family: 'JetBrains Mono', monospace;
      font-size: 12.5px !important;
    }
    .total-cell {
      font-weight: 600;
      color: #0f172a;
    }
    .actions-column {
      text-align: center !important;
      width: 100px;
      white-space: nowrap;
    }
    .row-actions-flex {
      display: flex;
      justify-content: center;
      gap: 4px;
    }
    .row-actions-flex button {
      width: 32px;
      height: 32px;
      line-height: 32px;
    }
    .table-paginator {
      border-top: 1px solid #e2e8f0;
    }

    /* Outward Grid Form */
    .form-card-container {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      overflow: hidden;
      margin-bottom: 12px;
    }
    .form-card-header {
      background: #f8fafc;
      padding: 12px 20px;
      border-bottom: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12.5px;
      font-weight: 600;
      color: #475569;
    }
    .form-card-header mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #6366f1;
    }
    .gst-out-grid-form {
      padding: 20px;
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px 20px;
      align-items: start;
    }
    .gst-out-grid-form mat-form-field {
      width: 100%;
      margin-bottom: -1.25em;
    }
    .submit-form-btn {
      height: 52px;
      border-radius: 8px;
      font-weight: 500;
    }

    /* Inline Controls */
    .gst-inline-input {
      width: 90%;
      padding: 6px 10px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 13px;
      background: white;
    }
    .gst-inline-select {
      width: 90%;
      padding: 5px 8px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 13px;
      background: white;
    }
    .gst-inline-input:focus, .gst-inline-select:focus {
      outline: none;
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15);
    }
    .gst-inline-input--num {
      text-align: right;
      font-family: 'JetBrains Mono', monospace;
    }
    .gst-inline-input--year {
      width: 70px;
    }

    /* Ledger Layout */
    .ledger-cards-container {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 20px;
    }
    .ledger-card-modern {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      transition: all 0.2s ease;
    }
    .ledger-card-modern:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
    }
    .card-top-header {
      padding: 16px 20px;
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .period-title {
      font-weight: 700;
      color: #0f172a;
      font-size: 14.5px;
    }
    .badge-status-pill {
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .pill-payable { background: #fef2f2; color: #dc2626; }
    .pill-receivable { background: #f0fdf4; color: #16a34a; }
    .pill-balanced { background: #f1f5f9; color: #475569; }
    
    .card-details-body {
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .detail-row {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      color: #64748b;
    }
    .detail-row .val {
      font-family: 'JetBrains Mono', monospace;
      font-weight: 600;
      color: #1e293b;
    }
    .row-divider {
      height: 1px;
      background: #f1f5f9;
      margin: 4px 0;
    }
    .net-difference-row {
      font-weight: 700;
      color: #0f172a;
    }
    .net-difference-row .val {
      font-size: 14px;
    }
    .text-danger { color: #dc2626 !important; }
    .text-success { color: #16a34a !important; }
    .ledger-paginator {
      border-top: 1px solid #e2e8f0;
      background: transparent;
      margin-top: 8px;
    }
    .sort-action-btn {
      height: 48px;
    }

    /* Loader / Overlay styles */
    .loading-overlay {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      gap: 16px;
      color: #64748b;
      font-size: 13.5px;
    }

    /* Empty states styling */
    .empty-state-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 64px 20px;
      border: 2px dashed #e2e8f0;
      border-radius: 12px;
      text-align: center;
      background: #ffffff;
    }
    .empty-state-box mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #cbd5e1;
      margin-bottom: 16px;
    }
    .empty-state-box h4 {
      margin: 0 0 6px 0;
      font-size: 15px;
      font-weight: 600;
      color: #334155;
    }
    .empty-state-box p {
      margin: 0;
      font-size: 12.5px;
      color: #64748b;
      max-width: 400px;
      line-height: 1.5;
    }

    /* Text inputs caret color */
    input, select {
      caret-color: auto !important;
    }

    @media (max-width: 992px) {
      .gst-app-header {
        flex-direction: column;
        align-items: flex-start;
      }
      .header-toolbar {
        width: 100%;
        justify-content: space-between;
      }
    }
    @media (max-width: 600px) {
      .gst-page-container {
        padding: 16px;
      }
      .header-toolbar {
        flex-direction: column;
        align-items: stretch;
      }
      .month-picker {
        width: 100%;
      }
      .picker-field, .picker-field--sm {
        flex: 1;
      }
      .header-actions-row {
        width: 100%;
      }
      .header-actions-row button {
        flex: 1;
      }
    }
  `]
})
export class GstReportComponent implements OnInit, AfterViewInit {
  months = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];
  
  selectedMonth = '';
  selectedYear = 0;

  gstInForm!: FormGroup;
  gstOutForm!: FormGroup;

  gstInList: any[] = [];
  gstOutList: any[] = [];
  filteredGstOutList: any[] = [];
  ledgerList: any[] = [];
  filteredLedgerList: any[] = [];
  paginatedLedgerList: any[] = [];
  courierOptions: string[] = [];

  // Summary Metrics
  gstInTotal = 0;
  gstOutTotal = 0;
  gstPayable = 0;
  gstReceivable = 0;

  loadingIn = false;
  loadingOut = false;
  loadingLedger = false;
  generated = false;
  showAllOutMonths = false;

  ledgerSearch = '';
  ledgerSortAsc = false; // default descending

  // Pagination and sorting fields
  gstInDisplayedColumns: string[] = ['serialNo', 'clientName', 'billingAmount', 'cgst', 'sgst', 'gstTotal'];
  gstOutDisplayedColumns: string[] = ['serialNo', 'invoiceNumber', 'month', 'year', 'courierType', 'billingAmount', 'cgst', 'sgst', 'gstTotal', 'actions'];

  gstInDataSource = new MatTableDataSource<GstInRecord>([]);
  gstOutDataSource = new MatTableDataSource<any>([]);

  // Ledger local pagination details
  ledgerPageSize = 6;
  ledgerPageIndex = 0;

  @ViewChild('sortIn') sortIn!: MatSort;
  @ViewChild('sortOut') sortOut!: MatSort;
  @ViewChild('paginatorIn') paginatorIn!: MatPaginator;
  @ViewChild('paginatorOut') paginatorOut!: MatPaginator;

  // Editing state for manual grid
  editingId: string | null = null;
  editDraft: any = {};

  @ViewChild('gstOutFormDirective') gstOutFormDirective!: FormGroupDirective;

  constructor(
    private api: ApiService,
    private toast: ToastService,
    private excel: ExcelExportService,
    private fb: FormBuilder,
    public permission: PermissionService
  ) {}

  ngOnInit(): void {
    this.selectedYear = new Date().getFullYear();
    this.selectedMonth = this.months[new Date().getMonth()];

    // Form configurations
    this.gstInForm = this.fb.group({
      month: [this.selectedMonth, Validators.required],
      year: [this.selectedYear, [Validators.required, Validators.min(1900)]]
    });

    this.gstOutForm = this.fb.group({
      invoiceNumber: ['', Validators.required],
      month: [this.selectedMonth, Validators.required],
      year: [this.selectedYear, [Validators.required, Validators.min(1900)]],
      courierType: ['', Validators.required],
      billingAmount: [null, [Validators.required, Validators.min(0)]],
      cgst: [null, [Validators.required, Validators.min(0)]],
      sgst: [null, [Validators.required, Validators.min(0)]],
      gstTotal: [null, [Validators.required, Validators.min(0)]]
    });

    // Automatically calculate tax on billingAmount change
    this.gstOutForm.get('billingAmount')?.valueChanges.subscribe(val => {
      if (val != null && val !== '') {
        const amt = Number(val);
        const cgst = Math.round((amt * 0.09) * 100.0) / 100.0;
        const sgst = Math.round((amt * 0.09) * 100.0) / 100.0;
        const total = Math.round((cgst + sgst) * 100.0) / 100.0;
        this.gstOutForm.patchValue({
          cgst: cgst,
          sgst: sgst,
          gstTotal: total
        }, { emitEvent: false });
      }
    });

    // Automatically update total when cgst/sgst are manually edited
    this.gstOutForm.get('cgst')?.valueChanges.subscribe(() => this.updateFormTotal());
    this.gstOutForm.get('sgst')?.valueChanges.subscribe(() => this.updateFormTotal());

    this.loadCompanySettings();
    this.loadGstOut();
    this.loadLedger();
    
    // Auto load GST IN if user has view permission
    if (this.permission.hasPermission('GST_REPORT', 'view_gst_in')) {
      this.fetchExistingGstIn();
    }
  }

  ngAfterViewInit() {
    this.gstInDataSource.sort = this.sortIn;
    this.gstInDataSource.paginator = this.paginatorIn;

    this.gstOutDataSource.sort = this.sortOut;
    this.gstOutDataSource.paginator = this.paginatorOut;
  }

  onMonthYearChange() {
    // Keep forms in sync with the global header selector
    this.gstInForm.patchValue({ month: this.selectedMonth, year: this.selectedYear });
    this.gstOutForm.patchValue({ month: this.selectedMonth, year: this.selectedYear });
    
    // Refresh data
    this.fetchExistingGstIn();
    this.applyGstOutFiltering();
    this.recomputePeriodMetrics();
  }

  loadCompanySettings() {
    this.api.get<any>('/company-settings').subscribe({
      next: (settings) => {
        this.courierOptions = settings?.couriers || [];
        if (this.courierOptions.length > 0) {
          this.gstOutForm.patchValue({
            courierType: this.courierOptions[0]
          });
        }
      },
      error: (err) => {
        console.error('Failed to load company settings', err);
      }
    });
  }

  updateFormTotal() {
    const cgst = Number(this.gstOutForm.get('cgst')?.value || 0);
    const sgst = Number(this.gstOutForm.get('sgst')?.value || 0);
    this.gstOutForm.patchValue({
      gstTotal: Math.round((cgst + sgst) * 100.0) / 100.0
    }, { emitEvent: false });
  }

  fetchExistingGstIn() {
    this.loadingIn = true;
    this.generated = false;
    const params = { month: this.selectedMonth, year: this.selectedYear, regenerate: false };

    // Fetch existing report or clear list if not generated
    this.api.get<any>('/gst-report/gst-in', params).subscribe({
      next: (report) => {
        this.gstInList = report?.records || [];
        this.gstInDataSource.data = this.gstInList;
        this.generated = true;
        this.loadingIn = false;
        this.recomputePeriodMetrics();
      },
      error: () => {
        this.gstInList = [];
        this.gstInDataSource.data = [];
        this.loadingIn = false;
        this.recomputePeriodMetrics();
      }
    });
  }

  generateGstIn(): void {
    this.loadingIn = true;
    this.generated = true;
    const params = { month: this.selectedMonth, year: this.selectedYear, regenerate: false };

    this.api.get<any>('/gst-report/gst-in', params).subscribe({
      next: (report) => {
        this.gstInList = report?.records || [];
        this.gstInDataSource.data = this.gstInList;
        this.loadingIn = false;
        this.toast.success('Success', 'GST IN report compiled successfully');
        this.loadLedger();
        this.recomputePeriodMetrics();
      },
      error: (err) => {
        console.error('Failed to load GST IN report', err);
        this.toast.error('Error', 'Failed to generate report');
        this.loadingIn = false;
      }
    });
  }

  regenerateGstIn(): void {
    this.loadingIn = true;
    const params = { month: this.selectedMonth, year: this.selectedYear, regenerate: true };

    this.api.get<any>('/gst-report/gst-in', params).subscribe({
      next: (report) => {
        this.gstInList = report?.records || [];
        this.gstInDataSource.data = this.gstInList;
        this.loadingIn = false;
        this.toast.success('Success', 'Report regenerated successfully');
        this.loadLedger();
        this.recomputePeriodMetrics();
      },
      error: (err) => {
        console.error('Failed to regenerate report', err);
        this.toast.error('Error', 'Failed to regenerate report');
        this.loadingIn = false;
      }
    });
  }

  loadGstOut(): void {
    this.loadingOut = true;
    this.api.get<any[]>('/gst-report/gst-out').subscribe({
      next: (data) => {
        this.gstOutList = data || [];
        this.applyGstOutFiltering();
        this.loadingOut = false;
      },
      error: (err) => {
        console.error('Failed to load GST OUT records', err);
        this.toast.error('Error', 'Failed to load entries');
        this.loadingOut = false;
      }
    });
  }

  applyGstOutFiltering() {
    if (this.showAllOutMonths) {
      this.filteredGstOutList = [...this.gstOutList];
    } else {
      this.filteredGstOutList = this.gstOutList.filter(item => 
        item.month?.toUpperCase() === this.selectedMonth.toUpperCase() && 
        Number(item.year) === Number(this.selectedYear)
      );
    }
    this.gstOutDataSource.data = this.filteredGstOutList;
    this.recomputePeriodMetrics();
  }

  onToggleShowAllOut() {
    this.applyGstOutFiltering();
  }

  applyGstInFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.gstInDataSource.filter = filterValue.trim().toLowerCase();
  }

  applyGstOutFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.gstOutDataSource.filter = filterValue.trim().toLowerCase();
  }

  recomputePeriodMetrics() {
    // Recalculate KPI metric card totals for the active month/year selection
    this.gstInTotal = this.gstInList.reduce((sum, item) => sum + (item.gstTotal || 0), 0);
    
    const activeOutRecords = this.gstOutList.filter(item => 
      item.month?.toUpperCase() === this.selectedMonth.toUpperCase() && 
      Number(item.year) === Number(this.selectedYear)
    );
    this.gstOutTotal = activeOutRecords.reduce((sum, item) => sum + (item.gstTotal || 0), 0);

    if (this.gstInTotal >= this.gstOutTotal) {
      this.gstPayable = this.gstInTotal - this.gstOutTotal;
      this.gstReceivable = 0;
    } else {
      this.gstReceivable = this.gstOutTotal - this.gstInTotal;
      this.gstPayable = 0;
    }

    this.gstInTotal = Math.round(this.gstInTotal * 100.0) / 100.0;
    this.gstOutTotal = Math.round(this.gstOutTotal * 100.0) / 100.0;
    this.gstPayable = Math.round(this.gstPayable * 100.0) / 100.0;
    this.gstReceivable = Math.round(this.gstReceivable * 100.0) / 100.0;
  }

  loadLedger(): void {
    this.loadingLedger = true;
    this.api.get<any[]>('/gst-report/ledger').subscribe({
      next: (data) => {
        this.ledgerList = data || [];
        this.filterLedger();
        this.loadingLedger = false;
      },
      error: (err) => {
        console.error('Failed to load ledger', err);
        this.loadingLedger = false;
      }
    });
  }

  filterLedger(): void {
    let result = [...this.ledgerList];
    if (this.ledgerSearch && this.ledgerSearch.trim() !== '') {
      const q = this.ledgerSearch.trim().toLowerCase();
      result = result.filter(item => 
        item.month.toLowerCase().includes(q) || 
        String(item.year).includes(q)
      );
    }

    // Sort sorting
    const monthOrder = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER'];
    result.sort((a, b) => {
      let yrCompare = b.year - a.year;
      if (yrCompare === 0) {
        yrCompare = monthOrder.indexOf(b.month.toUpperCase()) - monthOrder.indexOf(a.month.toUpperCase());
      }
      return this.ledgerSortAsc ? -yrCompare : yrCompare;
    });

    this.filteredLedgerList = result;
    this.ledgerPageIndex = 0;
    this.paginateLedger();
  }

  paginateLedger() {
    const start = this.ledgerPageIndex * this.ledgerPageSize;
    const end = start + this.ledgerPageSize;
    this.paginatedLedgerList = this.filteredLedgerList.slice(start, end);
  }

  onLedgerPageChange(event: any) {
    this.ledgerPageIndex = event.pageIndex;
    this.ledgerPageSize = event.pageSize;
    this.paginateLedger();
  }

  toggleLedgerSort(): void {
    this.ledgerSortAsc = !this.ledgerSortAsc;
    this.filterLedger();
  }

  addGstOut(): void {
    if (this.gstOutForm.invalid) return;

    const payload = this.gstOutForm.value;

    this.api.post<any>('/gst-report/gst-out', payload).subscribe({
      next: () => {
        this.toast.success('Success', 'Outward GST record added successfully');
        
        // Reset form cleanly to prevent border highlights
        if (this.gstOutFormDirective) {
          this.gstOutFormDirective.resetForm();
        }
        this.gstOutForm.reset({
          invoiceNumber: '',
          month: this.selectedMonth,
          year: this.selectedYear,
          courierType: this.courierOptions.length > 0 ? this.courierOptions[0] : '',
          billingAmount: null,
          cgst: null,
          sgst: null,
          gstTotal: null
        });

        this.loadGstOut();
        this.loadLedger();
      },
      error: (err) => {
        console.error('Failed to add outward GST record', err);
        this.toast.error('Error', err?.error?.message || 'Failed to save entry');
      }
    });
  }

  startEdit(item: any): void {
    this.editingId = item.id;
    this.editDraft = { ...item };
  }

  onEditAmountChange() {
    const amt = Number(this.editDraft.billingAmount || 0);
    this.editDraft.cgst = Math.round((amt * 0.09) * 100.0) / 100.0;
    this.editDraft.sgst = Math.round((amt * 0.09) * 100.0) / 100.0;
    this.editDraft.gstTotal = Math.round((this.editDraft.cgst + this.editDraft.sgst) * 100.0) / 100.0;
  }

  onEditTaxChange() {
    const cgst = Number(this.editDraft.cgst || 0);
    const sgst = Number(this.editDraft.sgst || 0);
    this.editDraft.gstTotal = Math.round((cgst + sgst) * 100.0) / 100.0;
  }

  cancelEdit(): void {
    this.editingId = null;
    this.editDraft = {};
  }

  saveEdit(): void {
    if (!this.editDraft.invoiceNumber || !this.editDraft.month || !this.editDraft.year || !this.editDraft.courierType || this.editDraft.billingAmount == null || this.editDraft.gstTotal == null) {
      this.toast.warning('Warning', 'All fields are required');
      return;
    }

    this.api.post<any>('/gst-report/gst-out', this.editDraft).subscribe({
      next: () => {
        this.toast.success('Success', 'Outward GST record updated');
        this.cancelEdit();
        this.loadGstOut();
        this.loadLedger();
      },
      error: (err) => {
        console.error('Failed to update outward GST record', err);
        this.toast.error('Error', err?.error?.message || 'Failed to update entry');
      }
    });
  }

  deleteGstOut(id: string): void {
    if (!confirm('Are you sure you want to delete this GST record?')) return;

    this.api.delete<void>('/gst-report/gst-out', id).subscribe({
      next: () => {
        this.toast.success('Success', 'Record deleted');
        this.loadGstOut();
        this.loadLedger();
      },
      error: (err) => {
        console.error('Failed to delete GST record', err);
        this.toast.error('Error', 'Failed to delete record');
      }
    });
  }

  async exportExcel() {
    // Export active lists dynamically via browser excel exporter
    const headers = ['S.No', 'Type', 'Record Name / Client', 'Invoice No', 'Month', 'Year', 'Courier Type', 'Billing Amount', 'CGST', 'SGST', 'GST Total'];
    const rows: any[] = [];
    
    // GST IN row mapping
    this.gstInList.forEach((r, idx) => {
      rows.push({
        'S.No': idx + 1,
        'Type': 'GST IN (Auto)',
        'Record Name / Client': r.clientName,
        'Invoice No': 'Auto generated',
        'Month': this.selectedMonth,
        'Year': this.selectedYear,
        'Courier Type': '—',
        'Billing Amount': r.billingAmount,
        'CGST': r.cgst,
        'SGST': r.sgst,
        'GST Total': r.gstTotal
      });
    });

    // GST OUT row mapping
    this.filteredGstOutList.forEach((r, idx) => {
      rows.push({
        'S.No': idx + 1,
        'Type': 'GST OUT (Manual)',
        'Record Name / Client': '—',
        'Invoice No': r.invoiceNumber,
        'Month': r.month,
        'Year': r.year,
        'Courier Type': r.courierType,
        'Billing Amount': r.billingAmount,
        'CGST': r.cgst,
        'SGST': r.sgst,
        'GST Total': r.gstTotal
      });
    });

    await this.excel.exportToExcel(rows, `GST_Report_${this.selectedMonth}_${this.selectedYear}`, headers, { exactFilename: true });
    this.toast.success('Export', 'Excel report downloaded successfully');
  }

  exportPdf() {
    this.toast.info('Info', 'Preparing document content for download...');
    this.printReport(); // trigger print layout, which can save as PDF
  }

  printReport() {
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      this.toast.error('Error', 'Popup blocked. Enable popups to print/export PDF.');
      return;
    }

    const inTableRows = this.gstInList.map((r, i) => `
      <tr>
        <td>${i + 1}</td>
        <td>${r.clientName}</td>
        <td class="num">₹ ${r.billingAmount.toFixed(2)}</td>
        <td class="num">₹ ${r.cgst.toFixed(2)}</td>
        <td class="num">₹ ${r.sgst.toFixed(2)}</td>
        <td class="num" style="font-weight:bold;">₹ ${r.gstTotal.toFixed(2)}</td>
      </tr>
    `).join('');

    const outTableRows = this.filteredGstOutList.map((r, i) => `
      <tr>
        <td>${i + 1}</td>
        <td>${r.invoiceNumber}</td>
        <td>${r.month} ${r.year}</td>
        <td>${r.courierType}</td>
        <td class="num">₹ ${r.billingAmount.toFixed(2)}</td>
        <td class="num">₹ ${r.cgst.toFixed(2)}</td>
        <td class="num">₹ ${r.sgst.toFixed(2)}</td>
        <td class="num" style="font-weight:bold;">₹ ${r.gstTotal.toFixed(2)}</td>
      </tr>
    `).join('');

    printWindow.document.write(`
      <html>
        <head>
          <title>GST Summary Report - ${this.selectedMonth} ${this.selectedYear}</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; padding: 30px; color: #1e293b; }
            h1 { font-size: 24px; margin: 0 0 4px 0; color: #0f172a; }
            .subtitle { font-size: 13px; color: #64748b; margin: 0 0 20px 0; }
            .metrics-flex { display: flex; gap: 15px; margin-bottom: 25px; }
            .m-card { flex: 1; border: 1px solid #e2e8f0; padding: 12px; border-radius: 8px; background: #f8fafc; }
            .m-lbl { font-size: 10px; font-weight: 700; text-transform: uppercase; color: #64748b; letter-spacing: 0.5px; }
            .m-val { font-size: 16px; font-weight: 700; color: #0f172a; margin-top: 4px; }
            h3 { font-size: 15px; font-weight: 600; color: #0f172a; margin-top: 25px; margin-bottom: 10px; border-left: 3px solid #6366f1; padding-left: 8px; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 30px; font-size: 12.5px; }
            th, td { border: 1px solid #e2e8f0; padding: 10px 12px; text-align: left; }
            th { background-color: #f8fafc; color: #475569; font-weight: 600; font-size: 11px; text-transform: uppercase; }
            .num { text-align: right; font-family: monospace; }
          </style>
        </head>
        <body>
          <h1>GST Compliance Report</h1>
          <p class="subtitle">Tax Summary Period: ${this.selectedMonth} ${this.selectedYear}</p>
          
          <div class="metrics-flex">
            <div class="m-card"><div class="m-lbl">GST IN Total</div><div class="m-val">₹ ${this.gstInTotal.toFixed(2)}</div></div>
            <div class="m-card"><div class="m-lbl">GST OUT Total</div><div class="m-val">₹ ${this.gstOutTotal.toFixed(2)}</div></div>
            <div class="m-card"><div class="m-lbl">GST Payable</div><div class="m-val">₹ ${this.gstPayable.toFixed(2)}</div></div>
            <div class="m-card"><div class="m-lbl">GST Receivable</div><div class="m-val">₹ ${this.gstReceivable.toFixed(2)}</div></div>
          </div>

          <h3>1. Inward Tax Transactions (GST IN - Auto)</h3>
          ${this.gstInList.length === 0 ? '<p style="color:#94a3b8;font-size:12.5px;">No inward transactions recorded.</p>' : `
          <table>
            <thead>
              <tr>
                <th>S.No</th>
                <th>Client Name</th>
                <th class="num">Billing Amount</th>
                <th class="num">CGST (9%)</th>
                <th class="num">SGST (9%)</th>
                <th class="num">GST Total</th>
              </tr>
            </thead>
            <tbody>
              ${inTableRows}
            </tbody>
          </table>
          `}

          <h3>2. Outward Tax Declarations (GST OUT - Manual)</h3>
          ${this.filteredGstOutList.length === 0 ? '<p style="color:#94a3b8;font-size:12.5px;">No outward transactions recorded.</p>' : `
          <table>
            <thead>
              <tr>
                <th>S.No</th>
                <th>Invoice No</th>
                <th>Period</th>
                <th>Courier Type</th>
                <th class="num">Billing Amount</th>
                <th class="num">CGST (9%)</th>
                <th class="num">SGST (9%)</th>
                <th class="num">GST Total</th>
              </tr>
            </thead>
            <tbody>
              ${outTableRows}
            </tbody>
          </table>
          `}
        </body>
      </html>
    `);
    
    printWindow.document.close();
    setTimeout(() => {
      printWindow.print();
    }, 400);
  }
}
