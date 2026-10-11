import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-expenses-form',
  template: `
    <div class="expf-page">
      <app-loading-spinner *ngIf="loading"></app-loading-spinner>

      <div class="expf-shell">
        <header class="expf-header">
          <button mat-stroked-button type="button" class="expf-back" (click)="cancel()">
            <mat-icon>arrow_back</mat-icon> Back
          </button>
          <div class="expf-header__titles">
            <h1>{{ isView ? 'View' : (isEdit ? 'Edit' : 'Add') }} {{ isCashOut() ? 'Cash Out' : 'Cash In' }}</h1>
            <p class="expf-header__sub">{{ isView ? 'Read-only view of the transaction record' : 'Capture transaction details below' }}</p>
          </div>
        </header>

        <form [formGroup]="expenseForm" (ngSubmit)="onSubmit()">
          <mat-card class="expf-card">
            <div class="expf-section-title">
              <mat-icon>category</mat-icon>
              <span>Transaction Type & Number</span>
            </div>
            <div class="expf-grid">
              <mat-form-field appearance="outline">
                <mat-label>Transaction Type *</mat-label>
                <mat-select formControlName="transactionType" required [disabled]="isView || isEdit">
                  <mat-option value="CASH_IN">Cash In</mat-option>
                  <mat-option value="CASH_OUT">Cash Out</mat-option>
                </mat-select>
                <mat-error *ngIf="expenseForm.get('transactionType')?.hasError('required')">Transaction Type is required</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Voucher Number</mat-label>
                <input matInput formControlName="expenseNumber" [disabled]="true">
                <mat-hint>Auto-generated</mat-hint>
              </mat-form-field>
            </div>
          </mat-card>

          <mat-card class="expf-card">
            <div class="expf-section-title">
              <mat-icon>layers</mat-icon>
              <span>Details & Category</span>
            </div>
            <div class="expf-grid">
              <mat-form-field appearance="outline">
                <mat-label>{{ isCashOut() ? 'Cash Out Category *' : 'Cash In Category *' }}</mat-label>
                <mat-select formControlName="category" required [disabled]="isView">
                  <mat-option *ngFor="let cat of categories" [value]="cat.name">{{ cat.name }}</mat-option>
                </mat-select>
                <mat-hint>Select from Master Data (Expense/Cash In Categories)</mat-hint>
                <mat-error *ngIf="expenseForm.get('category')?.hasError('required')">Category is required</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Summary / Heading</mat-label>
                <input matInput formControlName="expenseDetails" [disabled]="isView" placeholder="Enter brief details">
              </mat-form-field>
            </div>
          </mat-card>

          <mat-card class="expf-card">
            <div class="expf-section-title">
              <mat-icon>payments</mat-icon>
              <span>Payment & Amount</span>
            </div>
            <div class="expf-grid">
              <mat-form-field appearance="outline">
                <mat-label>Date *</mat-label>
                <input matInput [matDatepicker]="picker" formControlName="expenseDate" required [disabled]="isView">
                <mat-datepicker-toggle matSuffix [for]="picker"></mat-datepicker-toggle>
                <mat-datepicker #picker></mat-datepicker>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Payment Mode</mat-label>
                <mat-select formControlName="paymentType" [disabled]="isView">
                  <mat-option value="CASH">Cash</mat-option>
                  <mat-option value="ONLINE">Online</mat-option>
                  <mat-option value="CHEQUE">Cheque</mat-option>
                </mat-select>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Amount *</mat-label>
                <span matPrefix class="expf-rupee">₹&nbsp;</span>
                <input matInput type="number" formControlName="amount" step="0.01" min="0.01" required [disabled]="isView">
                <mat-error *ngIf="expenseForm.get('amount')?.hasError('required')">Amount is required</mat-error>
                <mat-error *ngIf="expenseForm.get('amount')?.hasError('min')">Amount must be greater than 0</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline">
                <mat-label>Party Name (Optional)</mat-label>
                <input matInput formControlName="partyName" [disabled]="isView">
              </mat-form-field>

              <mat-form-field appearance="outline" class="expf-col-span">
                <mat-label>Description</mat-label>
                <textarea matInput formControlName="description" rows="3" [disabled]="isView"></textarea>
              </mat-form-field>
            </div>
          </mat-card>

          <app-last-updated-by-field *ngIf="isEdit || isView" [value]="lastUpdatedBy"></app-last-updated-by-field>

          <div class="expf-actions">
            <button mat-stroked-button type="button" (click)="cancel()">{{ isView ? 'Back' : 'Cancel' }}</button>
            <button *ngIf="!isView" mat-flat-button color="primary" type="submit"
                    [disabled]="expenseForm.invalid || loading">
              <mat-icon>save</mat-icon>
              <span *ngIf="!loading">Save {{ isCashOut() ? 'Cash Out' : 'Cash In' }}</span>
              <span *ngIf="loading">Processing...</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .expf-page { padding: 24px; animation: fadeSlideUp 0.3s ease-out; }
    .expf-shell { max-width: 920px; margin: 0 auto; }

    .expf-header { display: flex; align-items: center; gap: 16px; margin-bottom: 20px; }
    .expf-back mat-icon { font-size: 18px; width: 18px; height: 18px; margin-right: 4px; }
    .expf-header__titles h1 { margin: 0; font-size: 1.5rem; font-weight: 700; color: #0f172a; letter-spacing: -0.02em; }
    .expf-header__sub { margin: 2px 0 0; color: #64748b; font-size: 0.875rem; }

    .expf-card {
      padding: 22px !important; border-radius: 14px !important; border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15,23,42,0.04) !important; margin-bottom: 18px;
    }
    .expf-section-title {
      display: flex; align-items: center; gap: 8px; margin-bottom: 18px;
      font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: #475569;
    }
    .expf-section-title mat-icon { color: #6366f1; font-size: 20px; width: 20px; height: 20px; }

    .expf-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 20px; }
    .expf-grid mat-form-field { width: 100%; }
    .expf-col-span { grid-column: 1 / -1; }
    .expf-rupee { color: #64748b; }

    .expf-actions { display: flex; justify-content: flex-end; gap: 12px; padding-top: 8px; }
    .expf-actions mat-icon { font-size: 18px; width: 18px; height: 18px; margin-right: 4px; }

    input, textarea { caret-color: auto !important; }

    @media (max-width: 768px) {
      .expf-page { padding: 16px; }
      .expf-grid { grid-template-columns: 1fr; }
      .expf-header { flex-wrap: wrap; }
    }
  `]
})
export class ExpensesFormComponent implements OnInit {
  expenseForm!: FormGroup;
  loading = false;
  expenseId: string | null = null;
  isEdit = false;
  lastUpdatedBy = '';
  isView = false;
  categories: any[] = [];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private toastService: ToastService
  ) {
    this.expenseForm = this.fb.group({
      expenseNumber: [''],
      expenseDetails: [''],
      category: ['', Validators.required],
      expenseDate: [new Date(), Validators.required],
      paymentType: ['CASH'],
      amount: [0, [Validators.required, Validators.min(0.01)]],
      partyName: [''],
      description: [''],
      transactionType: ['CASH_IN', Validators.required]
    });
  }

  ngOnInit() {
    this.loadCategories();
    
    this.route.params.subscribe(params => {
      this.expenseId = params['id'] || null;
      this.isEdit = this.route.snapshot.url.some(segment => segment.path === 'edit');
      this.isView = this.route.snapshot.url.some(segment => segment.path === 'view');
      
      if (this.expenseId) {
        this.loadExpense(this.expenseId);
      }
    });
  }

  isCashOut(): boolean {
    return this.expenseForm.get('transactionType')?.value === 'CASH_OUT';
  }

  loadCategories() {
    // Load categories from Master Data
    this.apiService.get<any[]>('/master-data/type/EXPENSE_CATEGORY').subscribe({
      next: (categories) => {
        this.categories = categories;
      },
      error: () => {
        // Fallback to expense service categories
        this.apiService.get<string[]>('/cash-in/categories').subscribe({
          next: (categories) => {
            this.categories = categories.map(cat => ({ name: cat }));
          },
          error: () => {
            // Default categories as fallback
            this.categories = ['Petrol', 'Rent', 'Salary', 'Tea', 'Transport'].map(cat => ({ name: cat }));
          }
        });
      }
    });
  }

  loadExpense(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/cash-in/${id}`).subscribe({
      next: (expense) => {
        this.loading = false;
        this.lastUpdatedBy = expense.lastUpdatedBy ?? '';
        this.expenseForm.patchValue({
          expenseNumber: expense.expenseNumber,
          expenseDetails: expense.expenseDetails || '',
          category: expense.category,
          expenseDate: this.parseLocalDate(expense.expenseDate),
          paymentType: expense.paymentType || 'CASH',
          amount: expense.amount,
          partyName: expense.partyName || '',
          description: expense.description || '',
          transactionType: expense.transactionType || 'CASH_IN'
        });
        if (this.isView) {
          this.expenseForm.disable();
        }
      },
      error: () => {
        this.loading = false;
        this.toastService.error('Error', 'Failed to load transaction record');
      }
    });
  }

  onSubmit() {
    if (this.expenseForm.valid) {
      this.loading = true;
      const formValue = this.expenseForm.value;
      
      const expenseData = {
        expenseDetails: formValue.expenseDetails || '',
        category: formValue.category,
        expenseDate: this.toLocalIsoDate(formValue.expenseDate),
        paymentType: formValue.paymentType,
        amount: formValue.amount,
        partyName: formValue.partyName || null,
        description: formValue.description || null,
        transactionType: formValue.transactionType
      };

      const request = this.isEdit && this.expenseId
        ? this.apiService.put<any>('/cash-in', this.expenseId, expenseData)
        : this.apiService.post<any>('/cash-in', expenseData);

      request.subscribe({
        next: () => {
          this.loading = false;
          this.toastService.success('Success', `Transaction ${this.isEdit ? 'updated' : 'created'} successfully`);
          setTimeout(() => {
            this.router.navigate(['/purchase-expense/cash-in']);
          }, 500);
        },
        error: () => {
          this.loading = false;
          this.toastService.error('Error', `Failed to ${this.isEdit ? 'update' : 'create'} transaction`);
        }
      });
    }
  }

  cancel() {
    this.router.navigate(['/purchase-expense/cash-in']);
  }

  private parseLocalDate(raw: unknown): Date {
    const s = String(raw || '').trim();
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
    if (m) {
      return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    }
    const d = new Date(s);
    if (Number.isNaN(d.getTime())) {
      return new Date();
    }
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  private toLocalIsoDate(value: unknown): string {
    const d = value instanceof Date ? value : new Date(String(value || ''));
    const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
  }
}
