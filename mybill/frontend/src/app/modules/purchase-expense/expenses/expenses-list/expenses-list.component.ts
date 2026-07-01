import { Component, OnInit, AfterViewInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { MatTableDataSource } from '@angular/material/table';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { ApiService, PageResponse } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

interface Expense {
  id: string;
  expenseNumber: string;
  expenseDate: string;
  category: string;
  partyName?: string;
  purchaseBillId?: string; // Link to Purchase Bill if auto-generated
  paymentType: string;
  paymentStatus?: string; // PAID | UNPAID | PARTIAL
  amount: number;
  paidAmount?: number;
  pendingAmount?: number;
  description?: string;
  expenseDetails?: string;
  lastUpdatedBy?: string;
  createdBy?: string;
  transactionType?: string; // CASH_IN | CASH_OUT
  runningBalance?: number;
}

interface CategoryTotal {
  category: string;
  amount: number;
}

@Component({
  selector: 'app-expenses-list',
  template: `
    <div class="exp-page">
      <!-- Header -->
      <header class="exp-header">
        <div class="exp-header__titles">
          <h1>Cash In/Out</h1>
          <p class="exp-header__sub">Manage Cash In/Out transactions and view consolidated ledger</p>
        </div>
        <div class="exp-header__actions">
          <button mat-stroked-button color="primary" type="button" (click)="exportCsv()" [disabled]="!dataSource.data.length" *appHasPermission="'EXPENSES:export'">
            <mat-icon>download</mat-icon> Export Ledger
          </button>
          <button mat-flat-button color="primary" type="button" (click)="createNew()" *appHasPermission="'EXPENSES:create'">
            <mat-icon>add</mat-icon> Add Transaction
          </button>
        </div>
      </header>

      <!-- Summary cards -->
      <section class="exp-summary">
        <article class="exp-stat exp-stat--total">
          <div class="exp-stat__icon exp-stat__icon--in"><mat-icon>arrow_downward</mat-icon></div>
          <div class="exp-stat__body">
            <span class="exp-stat__label">Total Cash In</span>
            <span class="exp-stat__value text-success">₹{{ totalCashInAmount | number:'1.2-2' }}</span>
            <span class="exp-stat__hint">{{ countCashIn }} entries</span>
          </div>
        </article>
        <article class="exp-stat exp-stat--total">
          <div class="exp-stat__icon exp-stat__icon--out"><mat-icon>arrow_upward</mat-icon></div>
          <div class="exp-stat__body">
            <span class="exp-stat__label">Total Cash Out</span>
            <span class="exp-stat__value text-danger">₹{{ totalCashOutAmount | number:'1.2-2' }}</span>
            <span class="exp-stat__hint">{{ countCashOut }} entries</span>
          </div>
        </article>
        <article class="exp-stat exp-stat--month">
          <div class="exp-stat__icon exp-stat__icon--net"><mat-icon>account_balance_wallet</mat-icon></div>
          <div class="exp-stat__body">
            <span class="exp-stat__label">Net Cash</span>
            <span class="exp-stat__value text-net">₹{{ netCashAmount | number:'1.2-2' }}</span>
            <span class="exp-stat__hint">Overall ledger balance</span>
          </div>
        </article>
      </section>

      <!-- Filter bar -->
      <mat-card class="exp-filter">
        <div class="exp-filter__grid">
          <mat-form-field appearance="outline" class="exp-f">
            <mat-label>Category</mat-label>
            <mat-select [(ngModel)]="selectedCategory" (selectionChange)="onCategoryChange()">
              <mat-option [value]="null">All Categories</mat-option>
              <mat-option *ngFor="let cat of categories" [value]="cat.category">
                {{ cat.category }} (₹{{ cat.amount | number:'1.0-0' }})
              </mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="exp-f">
            <mat-label>From Date</mat-label>
            <input matInput [matDatepicker]="expenseFromPicker" [(ngModel)]="dateFrom">
            <mat-datepicker-toggle matSuffix [for]="expenseFromPicker"></mat-datepicker-toggle>
            <mat-datepicker #expenseFromPicker></mat-datepicker>
          </mat-form-field>

          <mat-form-field appearance="outline" class="exp-f">
            <mat-label>To Date</mat-label>
            <input matInput [matDatepicker]="expenseToPicker" [(ngModel)]="dateTo">
            <mat-datepicker-toggle matSuffix [for]="expenseToPicker"></mat-datepicker-toggle>
            <mat-datepicker #expenseToPicker></mat-datepicker>
          </mat-form-field>

          <mat-form-field appearance="outline" class="exp-f exp-f--grow">
            <mat-label>Search</mat-label>
            <mat-icon matPrefix>search</mat-icon>
            <input matInput placeholder="Voucher No, Description, Party Name..."
                   [(ngModel)]="searchTerm" (input)="applySearch()">
          </mat-form-field>
        </div>
        <div class="exp-filter__actions">
          <button mat-stroked-button type="button" (click)="clearAllFilters()">
            <mat-icon>clear_all</mat-icon> Clear
          </button>
          <button mat-flat-button color="primary" type="button" (click)="applyDateSearch()">
            <mat-icon>filter_alt</mat-icon> Apply
          </button>
        </div>
      </mat-card>

      <!-- Side by Side split views for IN and OUT transactions -->
      <div class="exp-two-col">
        <!-- IN Transactions card -->
        <mat-card class="exp-split-card">
          <div class="exp-split-title">
            <mat-icon class="text-success">trending_up</mat-icon>
            <span>IN Transactions</span>
          </div>
          <div class="exp-table-wrap exp-split-table-wrap">
            <table mat-table [dataSource]="inDataSource" class="exp-table">
              <ng-container matColumnDef="date">
                <th mat-header-cell *matHeaderCellDef>DATE</th>
                <td mat-cell *matCellDef="let row">{{ row.expenseDate | date:'dd/MM/yyyy' }}</td>
              </ng-container>

              <ng-container matColumnDef="expenseNumber">
                <th mat-header-cell *matHeaderCellDef>VOUCHER NO</th>
                <td mat-cell *matCellDef="let row" class="exp-no">{{ row.expenseNumber }}</td>
              </ng-container>

              <ng-container matColumnDef="category">
                <th mat-header-cell *matHeaderCellDef>CATEGORY</th>
                <td mat-cell *matCellDef="let row">
                  <span class="exp-chip">{{ row.category }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef class="num-col">AMOUNT</th>
                <td mat-cell *matCellDef="let row" class="num-col text-success font-weight-bold">
                  ₹{{ row.amount | number:'1.2-2' }}
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef class="actions-col">ACTIONS</th>
                <td mat-cell *matCellDef="let row" class="actions-col">
                  <div class="exp-actions-row">
                    <button mat-icon-button color="primary" matTooltip="View" (click)="viewExpense(row.id)">
                      <mat-icon>visibility</mat-icon>
                    </button>
                    <button mat-icon-button matTooltip="Edit" (click)="editExpense(row.id)" [appDisableIfNoPermission]="'EXPENSES:edit'">
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button mat-icon-button color="warn" matTooltip="Delete" (click)="deleteExpense(row.id, row.expenseNumber)" *appHasPermission="'EXPENSES:delete'">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="splitColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: splitColumns;"></tr>
              <tr class="exp-no-data" *matNoDataRow>
                <td [attr.colspan]="splitColumns.length">
                  <div class="exp-empty">
                    <p>No IN transactions match filters.</p>
                  </div>
                </td>
              </tr>
            </table>
          </div>
        </mat-card>

        <!-- OUT Transactions card -->
        <mat-card class="exp-split-card">
          <div class="exp-split-title">
            <mat-icon class="text-danger">trending_down</mat-icon>
            <span>OUT Transactions</span>
          </div>
          <div class="exp-table-wrap exp-split-table-wrap">
            <table mat-table [dataSource]="outDataSource" class="exp-table">
              <ng-container matColumnDef="date">
                <th mat-header-cell *matHeaderCellDef>DATE</th>
                <td mat-cell *matCellDef="let row">{{ row.expenseDate | date:'dd/MM/yyyy' }}</td>
              </ng-container>

              <ng-container matColumnDef="expenseNumber">
                <th mat-header-cell *matHeaderCellDef>VOUCHER NO</th>
                <td mat-cell *matCellDef="let row" class="exp-no">{{ row.expenseNumber }}</td>
              </ng-container>

              <ng-container matColumnDef="category">
                <th mat-header-cell *matHeaderCellDef>CATEGORY</th>
                <td mat-cell *matCellDef="let row">
                  <span class="exp-chip exp-chip--out">{{ row.category }}</span>
                </td>
              </ng-container>

              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef class="num-col">AMOUNT</th>
                <td mat-cell *matCellDef="let row" class="num-col text-danger font-weight-bold">
                  ₹{{ row.amount | number:'1.2-2' }}
                </td>
              </ng-container>

              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef class="actions-col">ACTIONS</th>
                <td mat-cell *matCellDef="let row" class="actions-col">
                  <div class="exp-actions-row">
                    <button mat-icon-button color="primary" matTooltip="View" (click)="viewExpense(row.id)">
                      <mat-icon>visibility</mat-icon>
                    </button>
                    <button mat-icon-button matTooltip="Edit" (click)="editExpense(row.id)" [appDisableIfNoPermission]="'EXPENSES:edit'">
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button mat-icon-button color="warn" matTooltip="Delete" (click)="deleteExpense(row.id, row.expenseNumber)" *appHasPermission="'EXPENSES:delete'">
                      <mat-icon>delete</mat-icon>
                    </button>
                  </div>
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="splitColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: splitColumns;"></tr>
              <tr class="exp-no-data" *matNoDataRow>
                <td [attr.colspan]="splitColumns.length">
                  <div class="exp-empty">
                    <p>No OUT transactions match filters.</p>
                  </div>
                </td>
              </tr>
            </table>
          </div>
        </mat-card>
      </div>

      <!-- Ledger Table (Unified View) -->
      <mat-card class="exp-table-card">
        <div class="exp-split-title" style="padding: 18px 24px 8px 24px;">
          <mat-icon class="text-net">receipt_long</mat-icon>
          <span>Ledger</span>
        </div>
        <div class="exp-table-wrap">
          <table mat-table [dataSource]="dataSource" matSort class="exp-table">
            <ng-container matColumnDef="date">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>DATE</th>
              <td mat-cell *matCellDef="let row">{{ row.expenseDate | date:'dd/MM/yyyy' }}</td>
            </ng-container>

            <ng-container matColumnDef="expenseNumber">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>VOUCHER NO</th>
              <td mat-cell *matCellDef="let row">
                <span class="exp-no">{{ row.expenseNumber }}</span>
                <span *ngIf="row.purchaseBillId" class="auto-badge" matTooltip="Auto-generated from Purchase">
                  <mat-icon>auto_awesome</mat-icon>
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="description">
              <th mat-header-cell *matHeaderCellDef>DESCRIPTION</th>
              <td mat-cell *matCellDef="let row">
                <div class="exp-desc-cell">
                  <strong>{{ row.description || row.expenseDetails || '—' }}</strong>
                  <span class="exp-party" *ngIf="row.partyName">Party: {{ row.partyName }}</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="category">
              <th mat-header-cell *matHeaderCellDef mat-sort-header>CATEGORY</th>
              <td mat-cell *matCellDef="let row">
                <span class="exp-chip" [class.exp-chip--out]="row.transactionType === 'CASH_OUT'">{{ row.category }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="cashIn">
              <th mat-header-cell *matHeaderCellDef class="num-col">CASH IN</th>
              <td mat-cell *matCellDef="let row" class="num-col cell-cash-in">
                <span *ngIf="row.transactionType !== 'CASH_OUT'">₹{{ row.amount | number:'1.2-2' }}</span>
                <span *ngIf="row.transactionType === 'CASH_OUT'">—</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="cashOut">
              <th mat-header-cell *matHeaderCellDef class="num-col">CASH OUT</th>
              <td mat-cell *matCellDef="let row" class="num-col cell-cash-out">
                <span *ngIf="row.transactionType === 'CASH_OUT'">₹{{ row.amount | number:'1.2-2' }}</span>
                <span *ngIf="row.transactionType !== 'CASH_OUT'">—</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="runningBalance">
              <th mat-header-cell *matHeaderCellDef class="num-col">RUNNING BALANCE</th>
              <td mat-cell *matCellDef="let row" class="num-col cell-balance">
                ₹{{ row.runningBalance | number:'1.2-2' }}
              </td>
            </ng-container>

            <ng-container matColumnDef="createdBy">
              <th mat-header-cell *matHeaderCellDef>CREATED BY</th>
              <td mat-cell *matCellDef="let row">{{ row.createdBy || row.lastUpdatedBy || '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="actions-col">ACTIONS</th>
              <td mat-cell *matCellDef="let row" class="actions-col">
                <div class="exp-actions-row">
                  <button mat-icon-button color="primary" matTooltip="View" (click)="viewExpense(row.id)">
                    <mat-icon>visibility</mat-icon>
                  </button>
                  <button mat-icon-button matTooltip="Edit" (click)="editExpense(row.id)" [appDisableIfNoPermission]="'EXPENSES:edit'">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button color="warn" matTooltip="Delete" (click)="deleteExpense(row.id, row.expenseNumber)" *appHasPermission="'EXPENSES:delete'">
                    <mat-icon>delete</mat-icon>
                  </button>
                </div>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
            <tr class="exp-no-data" *matNoDataRow>
              <td [attr.colspan]="displayedColumns.length">
                <div class="exp-empty">
                  <mat-icon>receipt_long</mat-icon>
                  <p>No ledger entries match filters.</p>
                </div>
              </td>
            </tr>
          </table>
        </div>
        <mat-paginator [pageSizeOptions]="[10, 25, 50, 100]" showFirstLastButtons></mat-paginator>
      </mat-card>
    </div>
  `,
  styles: [`
    .exp-page { padding: 24px; max-width: 1400px; margin: 0 auto; }

    .exp-header {
      display: flex; align-items: flex-start; justify-content: space-between;
      gap: 16px; flex-wrap: wrap; margin-bottom: 20px;
    }
    .exp-header h1 { margin: 0; font-size: 1.6rem; font-weight: 700; color: #0f172a; letter-spacing: -0.02em; }
    .exp-header__sub { margin: 4px 0 0; color: #64748b; font-size: 0.9rem; }
    .exp-header__actions { display: flex; gap: 10px; flex-wrap: wrap; }
    .exp-header__actions mat-icon { font-size: 18px; width: 18px; height: 18px; margin-right: 4px; }

    .exp-summary {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 20px;
    }
    .exp-stat {
      display: flex; align-items: center; gap: 14px; padding: 18px;
      background: #fff; border: 1px solid #e2e8f0; border-radius: 14px;
      box-shadow: 0 1px 3px rgba(15,23,42,0.04); transition: transform .15s ease, box-shadow .15s ease;
    }
    .exp-stat:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(15,23,42,0.08); }
    .exp-stat__icon {
      display: flex; align-items: center; justify-content: center;
      width: 48px; height: 48px; border-radius: 12px; flex-shrink: 0;
    }
    .exp-stat__icon mat-icon { color: #fff; font-size: 24px; width: 24px; height: 24px; }
    
    .exp-stat__icon--in { background: linear-gradient(135deg, #10b981, #059669); }
    .exp-stat__icon--out { background: linear-gradient(135deg, #ef4444, #dc2626); }
    .exp-stat__icon--net { background: linear-gradient(135deg, #3b82f6, #2563eb); }
    
    .exp-stat__body { display: flex; flex-direction: column; min-width: 0; }
    .exp-stat__label { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: .04em; color: #64748b; }
    .exp-stat__value { font-size: 1.35rem; font-weight: 700; color: #0f172a; line-height: 1.2; }
    .exp-stat__hint { font-size: 12px; color: #94a3b8; }
    
    .text-success { color: #059669 !important; }
    .text-danger { color: #dc2626 !important; }
    .text-net { color: #2563eb !important; }

    .exp-filter { padding: 16px 18px !important; border-radius: 14px !important; border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15,23,42,0.04) !important; margin-bottom: 20px; }
    .exp-filter__grid { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
    .exp-f { width: 180px; margin-bottom: -1.25em; }
    .exp-f--grow { flex: 1; min-width: 220px; }
    .exp-filter__actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 12px; }
    .exp-filter__actions mat-icon { font-size: 18px; width: 18px; height: 18px; margin-right: 4px; }

    /* Split views */
    .exp-two-col {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
      margin-bottom: 20px;
    }
    .exp-split-card {
      padding: 0 !important;
      border-radius: 14px !important;
      border: 1px solid #e2e8f0;
      overflow: hidden;
      box-shadow: 0 4px 16px rgba(15,23,42,0.04) !important;
    }
    .exp-split-title {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px 20px 8px 20px;
      font-size: 14px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .05em;
      color: #334155;
    }
    .exp-split-title mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .exp-split-table-wrap {
      max-height: 380px;
      overflow-y: auto;
    }

    .exp-table-card { padding: 0 !important; border-radius: 14px !important; border: 1px solid #e2e8f0; overflow: hidden;
      box-shadow: 0 4px 16px rgba(15,23,42,0.06) !important; }
    .exp-table-wrap { overflow-x: auto; }
    .exp-table { width: 100%; min-width: 100%; }
    .exp-table th.mat-mdc-header-cell {
      background: linear-gradient(180deg,#f8fafc,#f1f5f9); font-size: 11px; font-weight: 700;
      letter-spacing: .05em; color: #64748b; border-bottom: 2px solid #e2e8f0;
    }
    .exp-table td.mat-mdc-cell { border-bottom: 1px solid #f1f5f9; color: #334155; font-size: 13px; }
    .exp-table tr.mat-mdc-row:hover { background: #f8fafc; }
    .num-col { text-align: right; font-weight: 600; }
    .font-weight-bold { font-weight: 700; }
    
    .cell-cash-in { color: #15803d !important; background-color: #f0fdf4; }
    .cell-cash-out { color: #b91c1c !important; background-color: #fef2f2; }
    .cell-balance { color: #1d4ed8 !important; background-color: #eff6ff; }
    
    .actions-col { text-align: center; width: 110px; min-width: 110px; white-space: nowrap; padding: 4px 8px !important; }
    .exp-actions-row {
      display: inline-flex; flex-direction: row; flex-wrap: nowrap; align-items: center;
      justify-content: center; gap: 0; white-space: nowrap;
    }
    .exp-actions-row .mat-mdc-icon-button {
      width: 32px; height: 32px; padding: 4px; flex-shrink: 0;
    }
    .exp-actions-row .mat-mdc-icon-button mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .exp-no { font-weight: 600; color: #1e293b; }
    
    .exp-chip { display: inline-block; padding: 3px 10px; border-radius: 999px; background: #e0f2fe; color: #0369a1; font-size: 11px; font-weight: 600; }
    .exp-chip--out { background: #fee2e2; color: #b91c1c; }
    .exp-desc-cell { display: flex; flex-direction: column; gap: 2px; }
    .exp-party { font-size: 11px; color: #64748b; }

    .auto-badge { display: inline-flex; align-items: center; margin-left: 8px; color: #059669; }
    .auto-badge mat-icon { font-size: 16px; width: 16px; height: 16px; }

    .exp-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 32px 16px; color: #94a3b8; }
    .exp-empty p { margin: 0; font-size: 13px; }

    input, textarea { caret-color: auto !important; }

    @media (max-width: 1024px) {
      .exp-summary { grid-template-columns: repeat(2, 1fr); }
      .exp-two-col { grid-template-columns: 1fr; }
    }
    @media (max-width: 640px) {
      .exp-page { padding: 16px; }
      .exp-summary { grid-template-columns: 1fr; }
      .exp-f, .exp-f--grow { width: 100%; flex: none; }
    }
  `]
})
export class ExpensesListComponent implements OnInit, AfterViewInit {
  splitColumns: string[] = ['date', 'expenseNumber', 'category', 'amount', 'actions'];
  displayedColumns: string[] = ['date', 'expenseNumber', 'description', 'category', 'cashIn', 'cashOut', 'runningBalance', 'createdBy', 'actions'];
  
  dataSource = new MatTableDataSource<Expense>([]);
  inDataSource = new MatTableDataSource<Expense>([]);
  outDataSource = new MatTableDataSource<Expense>([]);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  categories: CategoryTotal[] = [];
  selectedCategory: string | null = null;
  searchTerm = '';
  amountMin: number | null = null;
  amountMax: number | null = null;
  allExpenses: Expense[] = [];
  dateFrom: Date | null = null;
  dateTo: Date | null = null;

  totalCashInAmount = 0;
  countCashIn = 0;
  totalCashOutAmount = 0;
  countCashOut = 0;
  netCashAmount = 0;

  constructor(
    private apiService: ApiService,
    private router: Router,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    this.loadCategories();
    this.loadExpenses();
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.dataSource.sortingDataAccessor = (item, prop) => {
      switch (prop) {
        case 'date': return item.expenseDate ? new Date(item.expenseDate).getTime() : 0;
        case 'amount': return item.amount || 0;
        default: return (item as any)[prop];
      }
    };
  }

  loadCategories() {
    this.apiService.get<any>('/cash-in/categories/totals').subscribe({
      next: (totals) => {
        this.categories = Object.keys(totals).map(cat => ({ category: cat, amount: totals[cat] || 0 }));
      },
      error: () => {
        this.categories = [];
      }
    });
  }

  onCategoryChange() {
    this.loadExpenses(this.selectedCategory || undefined);
  }

  private iso(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  loadExpenses(category?: string) {
    const params: any = { page: 0, size: 1000 };

    const startDate = this.iso(this.dateFrom);
    const endDate = this.iso(this.dateTo);
    const hasDateRange = !!startDate && !!endDate;

    if (category && hasDateRange) {
      this.apiService.get<PageResponse<Expense>>(`/cash-in/category/${category}/filter`, { startDate, endDate, page: 0, size: 1000 }).subscribe({
        next: (response) => { this.allExpenses = response.content || []; this.applyFilters(); },
        error: () => this.toastService.error('Error', 'Failed to load ledger entries')
      });
    } else if (category) {
      this.apiService.get<Expense[]>(`/cash-in/category/${category}`).subscribe({
        next: (expenses) => { this.allExpenses = Array.isArray(expenses) ? expenses : []; this.applyFilters(); },
        error: () => this.toastService.error('Error', 'Failed to load ledger entries')
      });
    } else if (hasDateRange) {
      this.apiService.get<PageResponse<Expense>>('/cash-in/filter', { startDate, endDate, page: 0, size: 1000 }).subscribe({
        next: (response) => { this.allExpenses = response.content || []; this.applyFilters(); },
        error: () => this.toastService.error('Error', 'Failed to load ledger entries')
      });
    } else {
      this.apiService.get<PageResponse<Expense>>('/cash-in', params).subscribe({
        next: (response) => { this.allExpenses = response.content || []; this.applyFilters(); },
        error: () => this.toastService.error('Error', 'Failed to load ledger entries')
      });
    }
  }

  applySearch() {
    this.applyFilters();
  }

  applyDateSearch() {
    if ((this.dateFrom && !this.dateTo) || (!this.dateFrom && this.dateTo)) {
      this.toastService.warning('Date range', 'Please select both From Date and To Date');
      return;
    }
    this.loadExpenses(this.selectedCategory || undefined);
  }

  clearAllFilters() {
    this.dateFrom = null;
    this.dateTo = null;
    this.searchTerm = '';
    this.amountMin = null;
    this.amountMax = null;
    this.selectedCategory = null;
    this.loadExpenses();
  }

  applyFilters() {
    let filtered = [...this.allExpenses];

    if (this.searchTerm && this.searchTerm.trim() !== '') {
      const searchLower = this.searchTerm.toLowerCase().trim();
      filtered = filtered.filter(expense => {
        const dateStr = expense.expenseDate ? new Date(expense.expenseDate).toLocaleDateString('en-GB') : '';
        if (dateStr.toLowerCase().includes(searchLower)) return true;
        if (expense.expenseNumber && expense.expenseNumber.toLowerCase().includes(searchLower)) return true;
        if (expense.partyName && expense.partyName.toLowerCase().includes(searchLower)) return true;
        if (expense.description && expense.description.toLowerCase().includes(searchLower)) return true;
        if (expense.expenseDetails && expense.expenseDetails.toLowerCase().includes(searchLower)) return true;
        return false;
      });
    }

    // Dynamic running balance calculation chronologically (oldest to newest) on the filtered entries
    const sortedChronologically = [...filtered].sort((a, b) => {
      const timeA = a.expenseDate ? new Date(a.expenseDate).getTime() : 0;
      const timeB = b.expenseDate ? new Date(b.expenseDate).getTime() : 0;
      if (timeA !== timeB) return timeA - timeB;
      return (a.expenseNumber || '').localeCompare(b.expenseNumber || '');
    });

    let running = 0;
    sortedChronologically.forEach(e => {
      const isOut = e.transactionType === 'CASH_OUT';
      if (isOut) {
        running -= (e.amount || 0);
      } else {
        running += (e.amount || 0);
      }
      e.runningBalance = running;
    });

    this.dataSource.data = filtered;
    this.inDataSource.data = filtered.filter(e => e.transactionType !== 'CASH_OUT');
    this.outDataSource.data = filtered.filter(e => e.transactionType === 'CASH_OUT');
    this.computeSummaries(filtered);
  }

  private computeSummaries(expenses: Expense[]) {
    const cashIn = expenses.filter(e => e.transactionType !== 'CASH_OUT');
    const cashOut = expenses.filter(e => e.transactionType === 'CASH_OUT');

    this.totalCashInAmount = cashIn.reduce((sum, e) => sum + (e.amount || 0), 0);
    this.countCashIn = cashIn.length;

    this.totalCashOutAmount = cashOut.reduce((sum, e) => sum + (e.amount || 0), 0);
    this.countCashOut = cashOut.length;

    this.netCashAmount = this.totalCashInAmount - this.totalCashOutAmount;
  }

  createNew() {
    this.router.navigate(['/purchase-expense/cash-in/create']);
  }

  viewExpense(id: string) {
    this.router.navigate(['/purchase-expense/cash-in/edit', id]);
  }

  editExpense(id: string) {
    this.router.navigate(['/purchase-expense/cash-in/edit', id]);
  }

  deleteExpense(id: string, expenseNumber: string) {
    if (confirm(`Are you sure you want to delete transaction record ${expenseNumber}?`)) {
      this.apiService.delete('/cash-in', id).subscribe({
        next: () => {
          this.toastService.success('Success', 'Transaction record deleted successfully');
          this.loadExpenses(this.selectedCategory || undefined);
          this.loadCategories();
        },
        error: () => this.toastService.error('Error', 'Failed to delete transaction record')
      });
    }
  }

  exportCsv() {
    const rows = this.dataSource.data;
    if (!rows.length) {
      return;
    }
    const headers = ['Date', 'Voucher No', 'Type', 'Category', 'Description/Party', 'Payment Mode', 'Cash In', 'Cash Out', 'Running Balance', 'Created By'];
    const escape = (v: any) => {
      const s = v == null ? '' : String(v);
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const lines = rows.map(r => {
      const isOut = r.transactionType === 'CASH_OUT';
      return [
        r.expenseDate ? new Date(r.expenseDate).toLocaleDateString('en-GB') : '',
        r.expenseNumber,
        isOut ? 'Cash Out' : 'Cash In',
        r.category,
        r.description || r.expenseDetails || r.partyName || '',
        r.paymentType,
        isOut ? '0.00' : (r.amount || 0).toFixed(2),
        isOut ? (r.amount || 0).toFixed(2) : '0.00',
        (r.runningBalance || 0).toFixed(2),
        r.createdBy || r.lastUpdatedBy || ''
      ].map(escape).join(',');
    });
    const csv = [headers.join(','), ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cash_ledger_${this.iso(new Date()) || 'export'}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
