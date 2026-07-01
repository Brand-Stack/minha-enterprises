import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { PageEvent } from '@angular/material/paginator';
import { ApiService, PageResponse } from '../../core/services/api.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { UiConfigService } from '../../core/services/ui-config.service';
import { buildYearOptions, MONTH_NAMES } from '../../core/utils/month-year.util';

@Component({
  selector: 'app-accounting-page',
  templateUrl: './accounting-page.component.html',
  styleUrls: ['./accounting-page.component.scss']
})
export class AccountingPageComponent implements OnInit {
  @ViewChild('accDateField') accDateField?: ElementRef<HTMLInputElement>;

  form: FormGroup;
  editRowForm: FormGroup;
  editingId: string | null = null;
  editingLastUpdatedBy = '';
  saving = false;
  savingEdit = false;
  exporting = false;
  summary: any = null;
  rows: any[] = [];
  monthNames = MONTH_NAMES;
  yearOptions: number[] = [];
  fltFrom: Date | null = null;
  fltTo: Date | null = null;
  fltType = '';
  fltSearch = '';
  fltMonth = '';
  fltYear: number | null = null;
  sortBy = 'entryDate';
  sortDir = 'asc';

  reportPage = 0;
  reportSize = 25;
  reportTotal = 0;
  reportTotalPages = 0;

  inRows: any[] = [];
  outRows: any[] = [];
  inPage = 0;
  outPage = 0;
  inTotal = 0;
  outTotal = 0;

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private toast: ToastService,
    private uiConfig: UiConfigService
  ) {
    this.form = this.fb.group({
      entryDate: [new Date(), Validators.required],
      entryType: ['IN', Validators.required],
      amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
      description: ['']
    });
    this.editRowForm = this.fb.group({
      entryDate: [new Date(), Validators.required],
      entryType: ['IN', Validators.required],
      amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
      description: ['']
    });
  }

  ngOnInit(): void {
    this.uiConfig.getUi().subscribe({
      next: (u) => (this.yearOptions = buildYearOptions(u.collectionYearRangePast, u.collectionYearRangeFuture)),
      error: () => (this.yearOptions = buildYearOptions(5, 5))
    });
    this.refreshSummary();
    this.loadAllViews(0);
  }

  get displayRows(): any[] {
    const q = (this.fltSearch || '').trim().toLowerCase();
    if (!q) {
      return this.rows;
    }
    return this.rows.filter((r) => String(r.description ?? '').toLowerCase().includes(q));
  }

  loadAllViews(pageIdx: number): void {
    this.loadReport(pageIdx);
    this.loadIn(pageIdx);
    this.loadOut(pageIdx);
  }

  refreshSummary(): void {
    this.api.get<any>('/accounting/summary').subscribe({
      next: (s) => (this.summary = s),
      error: () => {}
    });
  }

  private filterParams(): Record<string, string | number> {
    const p: Record<string, string | number> = {};
    if (this.fltFrom) {
      p['dateFrom'] = this.iso(this.fltFrom);
    }
    if (this.fltTo) {
      p['dateTo'] = this.iso(this.fltTo);
    }
    if (this.fltType) {
      p['type'] = this.fltType;
    }
    if (this.fltYear != null) {
      p['calendarYear'] = this.fltYear;
    }
    if (this.fltMonth) {
      const i = MONTH_NAMES.indexOf(this.fltMonth);
      if (i >= 0) {
        p['calendarMonth'] = i + 1;
      }
    }
    return p;
  }

  loadReport(pageIdx: number): void {
    this.reportPage = pageIdx;
    const p = this.filterParams();
    p['page'] = this.reportPage;
    p['size'] = this.reportSize;
    p['sortBy'] = this.sortBy;
    p['sortDir'] = this.sortDir;
    this.api.get<PageResponse<any>>('/accounting/entries/paged', p).subscribe({
      next: (res) => {
        this.rows = res?.content ?? [];
        this.reportTotal = Number(res?.totalElements ?? 0);
        this.reportTotalPages = res?.totalPages ?? 0;
      },
      error: () => this.toast.error('Error', 'Failed to load report')
    });
  }

  loadIn(pageIdx: number): void {
    this.inPage = pageIdx;
    const p = this.filterParams();
    p['type'] = 'IN';
    p['page'] = this.inPage;
    p['size'] = this.reportSize;
    p['sortBy'] = 'entryDate';
    p['sortDir'] = 'desc';
    this.api.get<PageResponse<any>>('/accounting/entries/paged', p).subscribe({
      next: (res) => {
        this.inRows = res?.content ?? [];
        this.inTotal = Number(res?.totalElements ?? 0);
      },
      error: () => {}
    });
  }

  loadOut(pageIdx: number): void {
    this.outPage = pageIdx;
    const p = this.filterParams();
    p['type'] = 'OUT';
    p['page'] = this.outPage;
    p['size'] = this.reportSize;
    p['sortBy'] = 'entryDate';
    p['sortDir'] = 'desc';
    this.api.get<PageResponse<any>>('/accounting/entries/paged', p).subscribe({
      next: (res) => {
        this.outRows = res?.content ?? [];
        this.outTotal = Number(res?.totalElements ?? 0);
      },
      error: () => {}
    });
  }

  onInPage(e: PageEvent): void {
    this.reportSize = e.pageSize;
    this.loadIn(e.pageIndex);
  }

  onOutPage(e: PageEvent): void {
    this.reportSize = e.pageSize;
    this.loadOut(e.pageIndex);
  }

  onReportPage(e: PageEvent): void {
    this.reportSize = e.pageSize;
    this.loadReport(e.pageIndex);
  }

  exportCsv(): void {
    this.exporting = true;
    const p = this.filterParams();
    this.api.get<any[]>('/accounting/entries', p).subscribe({
      next: (list) => {
        this.exporting = false;
        const rows = list || [];
        const esc = (v: string) => '"' + String(v ?? '').replace(/"/g, '""') + '"';
        const lines = [
          ['#', 'Date', 'Type', 'Amount', 'Description', 'Balance after'].join(','),
          ...rows.map((r, i) =>
            [
              i + 1,
              r.entryDate,
              r.entryType,
              r.amount,
              esc(String(r.description ?? '')),
              r.balanceAfter
            ].join(',')
          )
        ];
        const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'accounting-export.csv';
        a.click();
        URL.revokeObjectURL(a.href);
        this.toast.success('Export', `${rows.length} row(s)`);
      },
      error: () => {
        this.exporting = false;
        this.toast.error('Error', 'Export failed');
      }
    });
  }

  private iso(d: Date): string {
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  startEditRow(r: any): void {
    this.editingId = r.id;
    this.editingLastUpdatedBy = r.lastUpdatedBy ?? '';
    this.editRowForm.patchValue({
      entryDate: r.entryDate ? new Date(String(r.entryDate)) : new Date(),
      entryType: String(r.entryType || 'IN'),
      amount: r.amount != null ? Number(r.amount) : null,
      description: r.description ?? ''
    });
  }

  cancelEditRow(): void {
    this.editingId = null;
    this.editingLastUpdatedBy = '';
    this.editRowForm.reset({
      entryDate: new Date(),
      entryType: 'IN',
      amount: null,
      description: ''
    });
  }

  saveEditRow(): void {
    if (!this.editingId || this.editRowForm.invalid) {
      return;
    }
    const raw = this.editRowForm.getRawValue();
    const amtRaw = raw.amount;
    const parsed =
      amtRaw === null || amtRaw === undefined || amtRaw === ''
        ? NaN
        : typeof amtRaw === 'number'
          ? amtRaw
          : Number(String(amtRaw).replace(/,/g, '').trim());
    if (!Number.isFinite(parsed) || parsed <= 0) {
      this.toast.error('Validation', 'Enter a valid amount greater than zero.');
      return;
    }
    const entryDate =
      raw.entryDate instanceof Date ? this.iso(raw.entryDate as Date) : raw.entryDate;
    const body = {
      entryDate,
      entryType: String(raw.entryType || '').trim(),
      amount: parsed,
      description: String(raw.description ?? '').trim()
    };
    this.savingEdit = true;
    this.api.put('/accounting/entries', this.editingId, body).subscribe({
      next: () => {
        this.savingEdit = false;
        this.toast.success('Updated', '');
        this.cancelEditRow();
        this.refreshSummary();
        this.loadAllViews(this.reportPage);
      },
      error: (err) => {
        this.savingEdit = false;
        const msg = err?.error?.message || err?.error?.error || 'Update failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Update failed');
      }
    });
  }

  deleteRow(r: any): void {
    if (!r?.id) {
      return;
    }
    if (!window.confirm('Delete this accounting entry? Running balances will be recalculated.')) {
      return;
    }
    this.api.deletePath(`/accounting/entries/${r.id}`).subscribe({
      next: () => {
        this.toast.success('Deleted', '');
        this.refreshSummary();
        this.loadAllViews(this.reportPage);
      },
      error: (err) => {
        const msg = err?.error?.message || err?.error?.error || 'Delete failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Delete failed');
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const amtRaw = raw.amount;
    const parsed =
      amtRaw === null || amtRaw === undefined || amtRaw === ''
        ? NaN
        : typeof amtRaw === 'number'
          ? amtRaw
          : Number(String(amtRaw).replace(/,/g, '').trim());
    if (!Number.isFinite(parsed) || parsed <= 0) {
      this.toast.error('Validation', 'Enter a valid amount greater than zero.');
      return;
    }
    const entryDate =
      raw.entryDate instanceof Date ? this.iso(raw.entryDate as Date) : raw.entryDate;
    const body = {
      entryDate,
      entryType: String(raw.entryType || '').trim(),
      amount: parsed,
      description: String(raw.description ?? '').trim()
    };
    this.saving = true;
    this.api.post('/accounting/entries', body).subscribe({
      next: () => {
        this.saving = false;
        this.toast.success('Saved', '');
        const today = new Date();
        this.form.reset({
          entryDate: today,
          entryType: 'IN',
          amount: null,
          description: ''
        });
        Object.values(this.form.controls).forEach((c) => {
          c.setErrors(null);
          c.markAsPristine();
          c.markAsUntouched();
        });
        this.refreshSummary();
        this.loadAllViews(this.reportPage);
        queueMicrotask(() => this.accDateField?.nativeElement?.focus());
      },
      error: (err) => {
        this.saving = false;
        const fe = err?.error?.fieldErrors;
        let msg: string | undefined = err?.error?.message;
        if (!msg && fe && typeof fe === 'object') {
          const first = Object.values(fe).find((x) => typeof x === 'string');
          if (typeof first === 'string') {
            msg = first;
          }
        }
        if (!msg) {
          msg = err?.error?.error;
        }
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
      }
    });
  }
}
