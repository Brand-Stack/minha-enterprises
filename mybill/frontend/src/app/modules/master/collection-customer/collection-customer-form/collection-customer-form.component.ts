import { animate, style, transition, trigger } from '@angular/animations';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../../../core/services/api.service';
import { PincodeLookupService } from '../../../../core/services/pincode-lookup.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

export interface AwbRegistryRow {
  id?: string;
  awbNo: string;
  status?: string;
}

@Component({
  selector: 'app-collection-customer-form',
  templateUrl: './collection-customer-form.component.html',
  styleUrls: ['./collection-customer-form.component.scss'],
  animations: [
    trigger('chipEnter', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(10px)' }),
        animate('220ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'none' }))
      ])
    ])
  ]
})
export class CollectionCustomerFormComponent implements OnInit {
  @ViewChild('awbInputRef') awbInputRef?: ElementRef<HTMLInputElement>;

  form: FormGroup;
  isEdit = false;
  lastUpdatedBy = '';
  id: string | null = null;
  saving = false;
  awbAddBusy = false;
  awbRows: AwbRegistryRow[] = [];
  awbSearch = '';
  newAwbInput = '';
  /** Bulk paste: one per line and/or comma-separated */
  bulkAwbText = '';
  /** Single-line buffer for barcode scanner (commits on Enter). */
  bulkScanLine = '';
  pincodeLoading = false;
  pincodeAreaOptions: string[] = [];
  private districtByAreaName: Record<string, string> = {};
  private lastUniformDistrict = '';
  stateLocked = false;
  areaLocked = false;
  cityLocked = false;
  awbSectionError = '';
  awbFieldError = '';
  bulkDuplicateNote = '';
  bulkConflictNote = '';
  bulkInvalidNote = '';
  bulkAlreadyNote = '';

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router,
    private toast: ToastService,
    private pincodeLookup: PincodeLookupService
  ) {
    this.form = this.fb.group({
      customerCode: [''],
      customerName: ['', Validators.required],
      contactPerson: [''],
      email: [''],
      phone: [''],
      whatsappNumber: [''],
      address: [''],
      city: [''],
      state: [''],
      areaName: [''],
      pincode: [''],
      gstin: ['']
    });
  }

  ngOnInit() {
    const pid = this.route.snapshot.paramMap.get('id');
    if (pid) {
      this.isEdit = true;
      this.id = pid;
      forkJoin({
        customer: this.api.get<any>(`/collection-customers/${pid}`),
        awbs: this.api.get<any[]>(`/collection-customers/${pid}/awbs`)
      }).subscribe({
        next: ({ customer, awbs }) => {
          this.lastUpdatedBy = customer.lastUpdatedBy ?? '';
          this.form.patchValue(customer);
          this.awbRows = (awbs || []).map((a) => ({
            id: a.id,
            awbNo: a.awbNo != null ? String(a.awbNo).trim() : '',
            status: a.status
          })).filter((r) => r.awbNo);
        },
        error: () => {
          this.toast.error('Error', 'Failed to load');
          this.router.navigate(['/collection-customer']);
        }
      });
    }
  }

  onPincodeInput(): void {
    const pcCtrl = this.form.get('pincode');
    const raw = String(pcCtrl?.value || '').trim();
    pcCtrl?.setErrors(null);
    this.form.patchValue({ state: '', city: '', areaName: '' }, { emitEvent: false });
    this.resetPincodeUi();
    if (/^\d{6}$/.test(raw)) {
      this.fetchPincode(raw);
    }
  }

  private resetPincodeUi(): void {
    this.pincodeAreaOptions = [];
    this.districtByAreaName = {};
    this.lastUniformDistrict = '';
    this.stateLocked = false;
    this.areaLocked = false;
    this.cityLocked = false;
  }

  private fetchPincode(pincode: string): void {
    this.pincodeLoading = true;
    this.pincodeLookup.lookup(pincode).subscribe((out) => {
      this.pincodeLoading = false;
      if (this.pincodeLookup.isUnavailable(out)) {
        return;
      }
      if (out.kind !== 'success') {
        return;
      }
      this.form.patchValue({ state: out.state }, { emitEvent: false });
      this.stateLocked = true;
      this.pincodeAreaOptions = out.areaNames;
      this.districtByAreaName = out.districtByAreaName;
      if (out.areaNames.length === 1) {
        const name = out.areaNames[0];
        const cityVal = this.districtByAreaName[name] || out.uniformDistrict || '';
        this.form.patchValue({ areaName: name, city: cityVal }, { emitEvent: false });
        this.areaLocked = true;
        this.cityLocked = !!cityVal;
      } else {
        this.lastUniformDistrict = out.uniformDistrict || '';
        this.form.patchValue({ areaName: '', city: this.lastUniformDistrict }, { emitEvent: false });
        this.areaLocked = false;
        this.cityLocked = !!this.lastUniformDistrict;
      }
    });
  }

  onAreaSelected(): void {
    const name = String(this.form.get('areaName')?.value || '').trim();
    const city = this.districtByAreaName[name] || this.lastUniformDistrict || '';
    this.form.patchValue({ city }, { emitEvent: false });
    this.cityLocked = !!city;
  }

  /** Scanner-friendly: append token + comma on Enter; keep focus in scan field. */
  onBulkScanEnter(ev: Event): void {
    ev.preventDefault();
    const raw = (this.bulkScanLine || '').trim().replace(/,+$/, '');
    if (!raw) {
      return;
    }
    const cur = (this.bulkAwbText || '').trim();
    const next = (cur ? (cur.endsWith(',') ? cur : cur + ',') : '') + raw + ',';
    this.bulkAwbText = next;
    this.bulkScanLine = '';
    queueMicrotask(() => {
      const el = (ev.target as HTMLInputElement) || null;
      el?.focus();
    });
  }

  get filteredAwbRows(): AwbRegistryRow[] {
    const q = (this.awbSearch || '').trim().toLowerCase();
    if (!q) {
      return this.awbRows;
    }
    return this.awbRows.filter((r) => r.awbNo.toLowerCase().includes(q));
  }

  trackByAwb(_: number, row: AwbRegistryRow): string {
    return row.id || row.awbNo;
  }

  private normKey(s: string): string {
    return String(s || '').trim().toUpperCase();
  }

  private parseAwbTokens(raw: string): string[] {
    let s = String(raw || '').trim();
    s = s.replace(/,+$/, '');
    return s
      .split(/[\s,;\n\r]+/)
      .map((t) => t.trim())
      .filter(Boolean);
  }

  private mergeBulkRegisterResponse(res: any): void {
    const created = res?.created;
    const rows = Array.isArray(created) ? created : [];
    for (const dto of rows) {
      if (!dto?.awbNo) {
        continue;
      }
      const norm = String(dto.awbNo).trim();
      this.awbRows = [
        { id: dto.id, awbNo: norm, status: dto.status || 'PENDING' },
        ...this.awbRows.filter((x) => this.normKey(x.awbNo) !== this.normKey(norm))
      ];
    }
    this.bulkDuplicateNote = (res?.duplicateInRequest || []).join(', ');
    this.bulkInvalidNote = (res?.invalidFormat || []).join(', ');
    this.bulkAlreadyNote = (res?.alreadyOnCustomer || []).join(', ');
    const cf = res?.conflicts as { awbNo?: string; message?: string }[] | undefined;
    this.bulkConflictNote = cf?.length ? cf.map((c) => `${c.awbNo ?? '?'}: ${c.message ?? ''}`).join(' | ') : '';
  }

  addAwbsFromBulkTextarea(): void {
    this.awbSectionError = '';
    this.clearBulkMessages();
    const tokens = this.parseAwbTokens(this.bulkAwbText);
    if (!tokens.length) {
      this.awbSectionError = 'Paste or type at least one AWB number.';
      return;
    }
    if (!this.isEdit || !this.id) {
      const seen = new Set<string>();
      const unique: string[] = [];
      for (const t of tokens) {
        const k = this.normKey(t);
        if (seen.has(k)) {
          continue;
        }
        seen.add(k);
        if (this.awbRows.some((r) => this.normKey(r.awbNo) === k)) {
          continue;
        }
        unique.push(t);
      }
      if (!unique.length) {
        this.awbSectionError = 'All pasted AWBs are already in the list or duplicated in the text.';
        return;
      }
      const validateBody: { awbNumbers: string[]; collectionCustomerId?: string } = { awbNumbers: unique };
      this.awbAddBusy = true;
      this.api.post<any>('/collection-customers/awbs/validate', validateBody).subscribe({
        next: (r) => {
          if (!r?.valid) {
            this.awbAddBusy = false;
            this.awbSectionError = this.formatAwbValidation(r);
            return;
          }
          const queued = unique.map((awbNo) => ({ awbNo }));
          this.awbRows = [...queued, ...this.awbRows];
          this.bulkAwbText = '';
          this.awbAddBusy = false;
          this.toast.success('AWBs queued', `${unique.length} will register when you save the customer.`);
        },
        error: (e) => {
          this.awbAddBusy = false;
          const msg = e?.error?.message || e?.message || 'Validation failed';
          this.awbSectionError = typeof msg === 'string' ? msg : 'Validation failed';
        }
      });
      return;
    }

    this.awbAddBusy = true;
    this.api.post<any>(`/collection-customers/${this.id}/awbs/bulk`, { awbNumbers: tokens }).subscribe({
      next: (res) => {
        this.awbAddBusy = false;
        this.mergeBulkRegisterResponse(res);
        const n = res?.created?.length ?? 0;
        if (n) {
          this.toast.success('AWBs registered', `${n} added`);
        }
        this.showBulkWarnings(res, n);
        this.bulkAwbText = '';
      },
      error: (e) => {
        this.awbAddBusy = false;
        const msg = e?.error?.message || e?.error?.error || 'Bulk register failed';
        this.awbSectionError = typeof msg === 'string' ? msg : 'Bulk register failed';
      }
    });
  }

  private clearBulkMessages(): void {
    this.bulkDuplicateNote = '';
    this.bulkConflictNote = '';
    this.bulkInvalidNote = '';
    this.bulkAlreadyNote = '';
  }

  private showBulkWarnings(res: any, createdCount: number): void {
    const parts: string[] = [];
    if (res?.duplicateInRequest?.length) {
      parts.push(`Duplicates in list: ${res.duplicateInRequest.join(', ')}`);
    }
    if (res?.invalidFormat?.length) {
      parts.push(`Invalid: ${res.invalidFormat.join(', ')}`);
    }
    if (res?.alreadyOnCustomer?.length) {
      parts.push(`Already on customer: ${res.alreadyOnCustomer.join(', ')}`);
    }
    const cf = res?.conflicts as { awbNo?: string; message?: string }[] | undefined;
    if (cf?.length) {
      parts.push(cf.map((c) => `${c.awbNo ?? '?'}: ${c.message ?? ''}`).join(' | '));
    }
    if (parts.length) {
      this.toast.warning('AWB register notes', parts.join(' — '));
    }
    if (!createdCount && !parts.length) {
      this.toast.warning('AWBs', 'Nothing new was registered');
    }
  }

  private formatAwbValidation(r: {
    duplicateInRequest?: string[];
    invalidFormat?: string[];
    conflicts?: { awbNo?: string; message?: string }[];
  }): string {
    const parts: string[] = [];
    const d = r.duplicateInRequest || [];
    if (d.length) {
      parts.push(`Duplicate in this list (case-insensitive): ${d.join(', ')}`);
    }
    const inv = r.invalidFormat || [];
    if (inv.length) {
      parts.push(`Invalid AWB (letters/digits only): ${inv.join(', ')}`);
    }
    const c = r.conflicts || [];
    if (c.length) {
      parts.push(c.map((x) => `${x.awbNo ?? '?'}: ${x.message ?? 'Conflict'}`).join(' | '));
    }
    return parts.join(' — ') || 'AWB validation failed';
  }

  private focusAwbInput(): void {
    queueMicrotask(() => this.awbInputRef?.nativeElement?.focus());
  }

  copyAwb(awb: string): void {
    const t = String(awb || '').trim();
    if (!t || typeof navigator === 'undefined' || !navigator.clipboard) {
      this.toast.warning('Copy', 'Clipboard not available');
      return;
    }
    navigator.clipboard.writeText(t).then(
      () => this.toast.success('Copied', t),
      () => this.toast.error('Copy', 'Could not copy')
    );
  }

  addAwb(): void {
    this.awbSectionError = '';
    this.clearBulkMessages();
    const raw = (this.newAwbInput || '').trim();
    if (!raw) {
      this.awbSectionError = 'Enter an AWB number.';
      return;
    }
    if (this.awbRows.some((r) => this.normKey(r.awbNo) === this.normKey(raw))) {
      this.awbSectionError = 'This AWB is already in the list for this customer.';
      return;
    }
    const validateBody: { awbNumbers: string[]; collectionCustomerId?: string } = { awbNumbers: [raw] };
    if (this.id) {
      validateBody.collectionCustomerId = this.id;
    }
    this.awbAddBusy = true;
    this.api.post<any>('/collection-customers/awbs/validate', validateBody).subscribe({
      next: (r) => {
        if (!r?.valid) {
          this.awbAddBusy = false;
          this.awbSectionError = this.formatAwbValidation(r);
          return;
        }
        if (this.isEdit && this.id) {
          this.api.post<any>(`/collection-customers/${this.id}/awbs/bulk`, { awbNumbers: [raw] }).subscribe({
            next: (res) => {
              this.awbAddBusy = false;
              const dto = res?.created?.[0];
              if (!dto?.awbNo) {
                this.showBulkWarnings(res, 0);
                this.awbSectionError = 'AWB was not registered (see notes above or toast).';
                return;
              }
              this.mergeBulkRegisterResponse({ created: [dto] });
              this.newAwbInput = '';
              this.toast.success('AWB added', raw);
              this.focusAwbInput();
            },
            error: (e) => {
              this.awbAddBusy = false;
              const msg = e?.error?.message || e?.error?.error || 'Could not register AWB';
              this.awbSectionError = typeof msg === 'string' ? msg : 'Could not register AWB';
            }
          });
        } else {
          this.awbAddBusy = false;
          this.awbRows = [{ awbNo: raw }, ...this.awbRows];
          this.newAwbInput = '';
          this.toast.success('AWB queued', 'It will be saved when you save the customer.');
          this.focusAwbInput();
        }
      },
      error: (e) => {
        this.awbAddBusy = false;
        const msg = e?.error?.message || e?.message || 'Validation failed';
        this.awbSectionError = typeof msg === 'string' ? msg : 'Validation failed';
      }
    });
  }

  removeAwbRow(row: AwbRegistryRow): void {
    this.awbSectionError = '';
    if (row.status === 'USED') {
      this.toast.warning('Cannot remove', 'This AWB is already used on a collection entry.');
      return;
    }
    if (!row.id) {
      this.awbRows = this.awbRows.filter((r) => this.normKey(r.awbNo) !== this.normKey(row.awbNo));
      this.toast.success('Removed', row.awbNo);
      return;
    }
    if (!this.id) {
      return;
    }
    this.awbAddBusy = true;
    this.api.deletePath(`/collection-customers/${this.id}/awbs/${row.id}`).subscribe({
      next: () => {
        this.awbAddBusy = false;
        this.awbRows = this.awbRows.filter((r) => r.id !== row.id);
        this.toast.success('Removed', row.awbNo);
      },
      error: (e) => {
        this.awbAddBusy = false;
        const msg = e?.error?.message || e?.error?.error || 'Could not remove AWB';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Could not remove AWB');
      }
    });
  }

  clearPendingAwbs(): void {
    if (!this.isEdit || !this.id) {
      return;
    }
    const ok = confirm('Are you sure you want to clear all pending AWBs?');
    if (!ok) {
      return;
    }
    this.awbAddBusy = true;
    this.api.deletePath(`/collection-customers/${this.id}/awbs/pending`).subscribe({
      next: () => {
        this.awbAddBusy = false;
        this.awbRows = this.awbRows.filter((r) => String(r.status || '').toUpperCase() === 'USED');
        this.toast.success('Success', 'Pending AWBs cleared successfully');
      },
      error: (e) => {
        this.awbAddBusy = false;
        const msg = e?.error?.message || e?.error?.error || 'Could not clear pending AWBs';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Could not clear pending AWBs');
      }
    });
  }

  private payloadCustomer(): Record<string, unknown> {
    const raw = this.form.getRawValue() as Record<string, unknown>;
    const { customerCode: _c, ...rest } = raw;
    return rest;
  }

  save() {
    if (this.form.invalid) return;
    this.awbFieldError = '';
    this.awbSectionError = '';
    const tokens = this.awbRows.map((r) => r.awbNo).filter(Boolean);
    if (!this.isEdit && !tokens.length) {
      this.executeSaveCustomerOnly();
      return;
    }
    if (tokens.length) {
      const validateBody: { awbNumbers: string[]; collectionCustomerId?: string } = { awbNumbers: tokens };
      if (this.id) {
        validateBody.collectionCustomerId = this.id;
      }
      this.saving = true;
      this.api.post<any>('/collection-customers/awbs/validate', validateBody).subscribe({
        next: (r) => {
          if (!r?.valid) {
            this.saving = false;
            this.awbFieldError = this.formatAwbValidation(r);
            this.toast.error('AWB validation', 'Fix the issues shown below, then save again.');
            return;
          }
          this.runPersistAfterValidation();
        },
        error: (e) => {
          this.saving = false;
          const msg = e?.error?.message || e?.message || 'AWB validation request failed';
          this.toast.error('Error', typeof msg === 'string' ? msg : 'AWB validation request failed');
        }
      });
    } else {
      this.executeSaveCustomerOnly();
    }
  }

  private runPersistAfterValidation() {
    if (this.isEdit && this.id) {
      this.api.put('/collection-customers', this.id, this.payloadCustomer()).subscribe({
        next: () => {
          this.saving = false;
          this.toast.success('Saved', 'Customer updated');
          this.router.navigate(['/collection-customer']);
        },
        error: (e) => {
          this.saving = false;
          const msg = e?.error?.message || e?.message || 'Save failed';
          this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
        }
      });
      return;
    }
    const awbNumbers = this.awbRows.map((r) => r.awbNo);
    this.api.post<any>('/collection-customers', this.payloadCustomer()).subscribe({
      next: (created) => {
        const cid = created?.id as string | undefined;
        if (!cid) {
          this.saving = false;
          this.toast.error('Error', 'Customer created but id missing');
          this.router.navigate(['/collection-customer']);
          return;
        }
        if (!awbNumbers.length) {
          this.saving = false;
          this.toast.success('Saved', 'Created');
          this.router.navigate(['/collection-customer']);
          return;
        }
        this.api.post<any>(`/collection-customers/${cid}/awbs/bulk`, { awbNumbers }).subscribe({
          next: (res) => {
            this.saving = false;
            const n = res?.created?.length ?? 0;
            this.toast.success('Saved', n ? `Created with ${n} AWB(s)` : 'Customer created');
            this.showBulkWarnings(res, n);
            this.router.navigate(['/collection-customer']);
          },
          error: (e) => {
            this.saving = false;
            const msg = e?.error?.message || e?.error?.error || 'AWB register failed';
            this.toast.error('Customer created', typeof msg === 'string' ? msg : 'AWB register failed');
            this.router.navigate(['/collection-customer']);
          }
        });
      },
      error: (e) => {
        this.saving = false;
        const msg = e?.error?.message || e?.message || 'Save failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
      }
    });
  }

  /** Save customer only (no AWB list validation) — e.g. create with zero AWBs or edit path shortcut. */
  private executeSaveCustomerOnly() {
    this.saving = true;
    if (this.isEdit && this.id) {
      this.api.put('/collection-customers', this.id, this.payloadCustomer()).subscribe({
        next: () => {
          this.saving = false;
          this.toast.success('Saved', 'Customer updated');
          this.router.navigate(['/collection-customer']);
        },
        error: (e) => {
          this.saving = false;
          const msg = e?.error?.message || e?.message || 'Save failed';
          this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
        }
      });
      return;
    }
    this.api.post<any>('/collection-customers', this.payloadCustomer()).subscribe({
      next: () => {
        this.saving = false;
        this.toast.success('Saved', 'Created');
        this.router.navigate(['/collection-customer']);
      },
      error: (e) => {
        this.saving = false;
        const msg = e?.error?.message || e?.message || 'Save failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
      }
    });
  }

  cancel() {
    this.router.navigate(['/collection-customer']);
  }
}
