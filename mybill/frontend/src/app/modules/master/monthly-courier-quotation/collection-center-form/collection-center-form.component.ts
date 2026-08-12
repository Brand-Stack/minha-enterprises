import { animate, style, transition, trigger } from '@angular/animations';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { concatMap, from, last } from 'rxjs';
import { ApiService } from '../../../../core/services/api.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import { PincodeLookupService } from '../../../../core/services/pincode-lookup.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { UiConfigService } from '../../../../core/services/ui-config.service';
import { buildYearOptions, currentMonthName, MONTH_NAMES } from '../../../../core/utils/month-year.util';

export interface PendingAwbOption {
  id: string;
  awbNo: string;
}

export interface CollectionDraftRow {
  localId: string;
  registryId: string;
  awbNo: string;
  form: FormGroup;
  pincodeLoading: boolean;
  pincodeAreaOptions: string[];
  districtByAreaName: Record<string, string>;
  lastUniformDistrict: string;
  stateLocked: boolean;
  areaLocked: boolean;
  cityLocked: boolean;
  filteredCouriers: string[];
  filteredStatuses: string[];
  filteredAmountStatuses: string[];
  saving?: boolean;
  saveError?: string;
}

@Component({
  selector: 'app-collection-center-form',
  templateUrl: './collection-center-form.component.html',
  styleUrls: ['./collection-center-form.component.scss'],
  animations: [
    trigger('rowEnter', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(12px)' }),
        animate('240ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'none' }))
      ])
    ])
  ]
})
export class CollectionCenterFormComponent implements OnInit {
  /** Edit mode single form */
  form: FormGroup;
  /** Create mode header fields */
  metaForm: FormGroup;

  customers: any[] = [];
  id: string | null = null;
  lastUpdatedBy = '';
  saving = false;
  savingAll = false;

  pincodeLoading = false;
  pincodeAreaOptions: string[] = [];
  private districtByAreaName: Record<string, string> = {};
  private lastUniformDistrict = '';
  stateLocked = false;
  areaLocked = false;
  cityLocked = false;

  allCouriers: string[] = [];
  allStatuses: string[] = [];
  filteredCouriers: string[] = [];
  filteredStatuses: string[] = [];
  readonly amountStatusOptions = ['Cash', 'GPay', 'Pending', 'COD', 'Paid', 'UnPaid'];
  filteredAmountStatuses: string[] = [];

  readonly monthNames = MONTH_NAMES;
  yearOptions: number[] = [];
  pendingAwbs: PendingAwbOption[] = [];
  pendingSearch = '';
  selectedPendingIds = new Set<string>();

  consumeRegistryAwbId: string | null = null;

  completedRegistryAwbs: { awbNo: string; status: string }[] = [];
  awbPanelMode: 'pending' | 'completed' | 'none' = 'pending';

  draftRows: CollectionDraftRow[] = [];

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private pincodeLookup: PincodeLookupService,
    private companySettingsLists: CompanySettingsListsService,
    private route: ActivatedRoute,
    private router: Router,
    private toast: ToastService,
    private uiConfig: UiConfigService
  ) {
    this.form = this.fb.group({
      collectionCustomerId: ['', Validators.required],
      entryDate: [null as Date | null],
      entryMonth: [''],
      entryYear: [null as number | null],
      consignor: [''],
      receiverName: [''],
      pincode: [''],
      state: [''],
      city: [''],
      areaName: [''],
      courier: [''],
      weight: [null as number | null],
      awbNo: [''],
      item: [''],
      status: [''],
      amount: [null as number | null],
      amountStatus: [''],
      remarks: ['']
    });
    this.metaForm = this.fb.group({
      collectionCustomerId: ['', Validators.required],
      entryMonth: ['', Validators.required],
      entryYear: [null as number | null, Validators.required],
      defaultConsignor: ['']
    });
    this.filteredAmountStatuses = [...this.amountStatusOptions];
  }

  ngOnInit() {
    this.uiConfig.getUi().subscribe({
      next: (ui) => {
        this.yearOptions = buildYearOptions(ui.collectionYearRangePast, ui.collectionYearRangeFuture);
        this.tryApplyCreateDefaults();
      },
      error: () => {
        this.yearOptions = buildYearOptions(5, 5);
        this.tryApplyCreateDefaults();
      }
    });
    this.form.get('collectionCustomerId')?.valueChanges.subscribe((cid) => {
      this.consumeRegistryAwbId = null;
      this.completedRegistryAwbs = [];
      if (cid) {
        this.loadPendingAwbs(String(cid));
        if (this.awbPanelMode === 'completed') {
          this.loadCompletedRegistryAwbs(String(cid));
        }
      } else {
        this.pendingAwbs = [];
      }
      this.applyConsignorFromSelectedCustomer();
    });
    this.metaForm.get('collectionCustomerId')?.valueChanges.subscribe((cid) => {
      this.selectedPendingIds.clear();
      this.draftRows = [];
      this.pendingSearch = '';
      if (cid) {
        this.loadPendingAwbs(String(cid));
      } else {
        this.pendingAwbs = [];
      }
      this.applyConsignorFromSelectedCustomer();
    });
    this.metaForm.get('defaultConsignor')?.valueChanges.subscribe(() => {
      this.applyDefaultConsignorToDrafts();
    });
    this.loadCompanyLists();
    this.api.get<any>('/collection-customers', { page: 0, size: 500 }).subscribe({
      next: (res) => {
        this.customers = res.content || [];
        this.applyConsignorFromSelectedCustomer();
      },
      error: () => {}
    });
    const eid = this.route.snapshot.paramMap.get('id');
    this.route.queryParams.subscribe((q) => {
      if (q['customerId'] && !eid) {
        this.form.patchValue({ collectionCustomerId: q['customerId'] });
        this.metaForm.patchValue({ collectionCustomerId: q['customerId'] });
      }
    });
    if (eid) {
      this.id = eid;
      this.api.get<any>(`/collection-center/entries/${eid}`).subscribe({
        next: (row) => {
          this.resetPincodeLookupUi();
          this.consumeRegistryAwbId = null;
          this.lastUpdatedBy = row.lastUpdatedBy ?? '';
          this.form.patchValue({
            ...row,
            entryDate: row.entryDate ? new Date(row.entryDate) : null
          });
          if (row?.collectionCustomerId) {
            this.loadPendingAwbs(String(row.collectionCustomerId));
          }
        },
        error: () => {
          this.toast.error('Error', 'Not found');
          this.router.navigate(['/client-entries/collection-center']);
        }
      });
    } else {
      this.tryApplyCreateDefaults();
    }
  }

  get isCreateMode(): boolean {
    return !this.id;
  }

  get filteredPendingAwbs(): PendingAwbOption[] {
    const q = (this.pendingSearch || '').trim().toLowerCase();
    if (!q) {
      return this.pendingAwbs;
    }
    return this.pendingAwbs.filter((p) => p.awbNo.toLowerCase().includes(q));
  }

  get draftAwbKeys(): Set<string> {
    return new Set(this.draftRows.map((r) => r.awbNo.toUpperCase()));
  }

  togglePendingSelect(p: PendingAwbOption): void {
    if (this.draftAwbKeys.has(p.awbNo.toUpperCase())) {
      return;
    }
    if (this.selectedPendingIds.has(p.id)) {
      this.selectedPendingIds.delete(p.id);
    } else {
      this.selectedPendingIds.add(p.id);
    }
  }

  isPendingSelected(p: PendingAwbOption): boolean {
    return this.selectedPendingIds.has(p.id);
  }

  selectAllFilteredPending(): void {
    for (const p of this.filteredPendingAwbs) {
      if (!this.draftAwbKeys.has(p.awbNo.toUpperCase())) {
        this.selectedPendingIds.add(p.id);
      }
    }
  }

  clearPendingSelection(): void {
    this.selectedPendingIds.clear();
  }

  addRowsForSelectedAwbs(): void {
    const cid = this.metaForm.get('collectionCustomerId')?.value;
    if (!cid) {
      this.toast.warning('Customer', 'Select a collection customer first');
      return;
    }
    const picked = this.pendingAwbs.filter((p) => this.selectedPendingIds.has(p.id));
    if (!picked.length) {
      this.toast.warning('AWBs', 'Select one or more pending AWBs');
      return;
    }
    const today = new Date();
    const defCourier = this.allCouriers.length ? this.allCouriers[0] : '';
    const defStatus = this.allStatuses.length ? this.allStatuses[0] : '';
    const defConsignor =
      this.selectedCollectionCustomerName() || String(this.metaForm.get('defaultConsignor')?.value || '').trim();
    for (const p of picked) {
      if (this.draftRows.some((r) => r.awbNo.toUpperCase() === p.awbNo.toUpperCase())) {
        continue;
      }
      const rowForm = this.fb.group({
        entryDate: [today],
        consignor: [defConsignor],
        receiverName: [''],
        pincode: [''],
        state: [''],
        city: [''],
        areaName: [''],
        courier: [defCourier],
        weight: [null as number | null],
        awbNo: [{ value: p.awbNo, disabled: true }],
        item: [''],
        status: [defStatus],
        amount: [null as number | null],
        amountStatus: [''],
        remarks: ['']
      });
      const row: CollectionDraftRow = {
        localId: this.newLocalId(),
        registryId: p.id,
        awbNo: p.awbNo,
        form: rowForm,
        pincodeLoading: false,
        pincodeAreaOptions: [],
        districtByAreaName: {},
        lastUniformDistrict: '',
        stateLocked: false,
        areaLocked: false,
        cityLocked: false,
        filteredCouriers: [...this.allCouriers],
        filteredStatuses: [...this.allStatuses],
        filteredAmountStatuses: [...this.amountStatusOptions]
      };
      this.draftRows = [...this.draftRows, row];
    }
    this.selectedPendingIds.clear();
    if (picked.length) {
      this.toast.success('Rows added', `${picked.length} shipment row(s)`);
    }
  }

  removeDraftRow(row: CollectionDraftRow): void {
    this.draftRows = this.draftRows.filter((r) => r.localId !== row.localId);
  }

  private newLocalId(): string {
    return typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : 'r-' + Math.random().toString(36).slice(2);
  }

  private applyDefaultConsignorToDrafts(): void {
    const c = String(this.metaForm.get('defaultConsignor')?.value || '').trim();
    for (const r of this.draftRows) {
      const cur = String(r.form.get('consignor')?.value || '').trim();
      if (!cur) {
        r.form.patchValue({ consignor: c }, { emitEvent: false });
      }
    }
  }

  private selectedCollectionCustomerName(): string {
    const cid = this.isCreateMode
      ? this.metaForm.get('collectionCustomerId')?.value
      : this.form.get('collectionCustomerId')?.value;
    if (!cid) {
      return '';
    }
    const c = this.customers.find((x: { id?: string }) => x.id === cid);
    return c?.customerName ? String(c.customerName).trim() : '';
  }

  /** Auto-fill consignor from the selected collection customer (create + edit). */
  private applyConsignorFromSelectedCustomer(): void {
    const name = this.selectedCollectionCustomerName();
    if (!name) {
      return;
    }
    if (!this.id) {
      this.metaForm.patchValue({ defaultConsignor: name }, { emitEvent: false });
      for (const r of this.draftRows) {
        r.form.patchValue({ consignor: name }, { emitEvent: false });
      }
    } else {
      this.form.patchValue({ consignor: name }, { emitEvent: false });
    }
  }

  private loadCompanyLists(): void {
    this.companySettingsLists.getLists().subscribe({
      next: (lists) => {
        this.allCouriers = lists.couriers;
        this.allStatuses = lists.statuses;
        this.filteredCouriers = [...this.allCouriers];
        this.filteredStatuses = [...this.allStatuses];
        this.filteredAmountStatuses = [...this.amountStatusOptions];
        this.tryApplyCreateDefaults();
        for (const r of this.draftRows) {
          r.filteredCouriers = [...this.allCouriers];
          r.filteredStatuses = [...this.allStatuses];
          r.filteredAmountStatuses = [...this.amountStatusOptions];
        }
      }
    });
  }

  private tryApplyCreateDefaults(): void {
    if (this.id) {
      return;
    }
    const today = new Date();
    const patchMeta: Record<string, unknown> = {};
    if (!this.metaForm.get('entryMonth')?.value) {
      patchMeta['entryMonth'] = currentMonthName();
    }
    if (this.metaForm.get('entryYear')?.value == null) {
      patchMeta['entryYear'] = today.getFullYear();
    }
    if (Object.keys(patchMeta).length) {
      this.metaForm.patchValue(patchMeta, { emitEvent: false });
    }
  }

  loadPendingAwbs(customerId: string): void {
    this.api.get<any[]>(`/collection-customers/${customerId}/awbs/pending`).subscribe({
      next: (rows) => {
        this.pendingAwbs = (rows || []).map((r) => ({ id: r.id, awbNo: r.awbNo }));
      },
      error: () => {
        this.pendingAwbs = [];
      }
    });
  }

  loadCompletedRegistryAwbs(customerId: string): void {
    this.api.get<any[]>(`/collection-customers/${customerId}/awbs`).subscribe({
      next: (rows) => {
        const used = (rows || []).filter((r) => String(r.status || '').toUpperCase() === 'USED');
        this.completedRegistryAwbs = used.map((r) => ({ awbNo: r.awbNo, status: r.status }));
      },
      error: () => {
        this.completedRegistryAwbs = [];
      }
    });
  }

  showPendingAwbsPanel(): void {
    this.awbPanelMode = 'pending';
    const cid = this.form.get('collectionCustomerId')?.value;
    if (cid) {
      this.loadPendingAwbs(String(cid));
    }
  }

  showCompletedAwbsPanel(): void {
    this.awbPanelMode = 'completed';
    const cid = this.form.get('collectionCustomerId')?.value;
    if (cid) {
      this.loadCompletedRegistryAwbs(String(cid));
    } else {
      this.completedRegistryAwbs = [];
    }
  }

  promptRegisterNewAwbs(): void {
    const cid = this.isCreateMode
      ? this.metaForm.get('collectionCustomerId')?.value
      : this.form.get('collectionCustomerId')?.value;
    if (!cid) {
      this.toast.warning('Customer', 'Select a collection customer first');
      return;
    }
    const raw = window.prompt('Enter AWB numbers (comma, space, or newline separated)', '');
    if (raw == null) {
      return;
    }
    const awbNumbers = this.parseAwbTokens(raw);
    if (!awbNumbers.length) {
      return;
    }
    this.api.post<any>(`/collection-customers/${cid}/awbs/bulk`, { awbNumbers }).subscribe({
      next: (res) => {
        const created = res?.created ?? res;
        const n = Array.isArray(created) ? created.length : 0;
        this.toastBulkRegisterMessages(res, n);
        this.loadPendingAwbs(String(cid));
        if (this.awbPanelMode === 'completed') {
          this.loadCompletedRegistryAwbs(String(cid));
        }
      },
      error: (err) => {
        const msg = err?.error?.message || err?.error?.error || 'Register failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Register failed');
      }
    });
  }

  private parseAwbTokens(raw: string): string[] {
    return raw
      .split(/[\s,;\n\r]+/)
      .map((s) => s.trim())
      .filter(Boolean);
  }

  private toastBulkRegisterMessages(res: any, createdCount: number): void {
    if (!res || Array.isArray(res)) {
      if (createdCount) {
        this.toast.success('Registered', `${createdCount} AWB(s) added`);
      }
      return;
    }
    if (createdCount) {
      this.toast.success('Registered', `${createdCount} AWB(s) added`);
    }
    const parts: string[] = [];
    const dup = res.duplicateInRequest as string[] | undefined;
    if (dup?.length) {
      parts.push(`Duplicates in list: ${dup.join(', ')}`);
    }
    const inv = res.invalidFormat as string[] | undefined;
    if (inv?.length) {
      parts.push(`Invalid: ${inv.join(', ')}`);
    }
    const onC = res.alreadyOnCustomer as string[] | undefined;
    if (onC?.length) {
      parts.push(`Already on customer: ${onC.join(', ')}`);
    }
    const cf = res.conflicts as { awbNo?: string; message?: string }[] | undefined;
    if (cf?.length) {
      parts.push(cf.map((c) => `${c.awbNo ?? '?'}: ${c.message ?? ''}`).join(' | '));
    }
    if (parts.length) {
      this.toast.warning('AWB register notes', parts.join(' — '));
    }
    if (!createdCount && !parts.length) {
      this.toast.warning('AWBs', 'Nothing was registered');
    }
  }

  onPickPendingAwb(row: PendingAwbOption | null): void {
    if (!row) {
      this.consumeRegistryAwbId = null;
      return;
    }
    this.consumeRegistryAwbId = row.id;
    this.form.patchValue({ awbNo: row.awbNo }, { emitEvent: false });
  }

  onAwbManualInput(): void {
    this.consumeRegistryAwbId = null;
  }

  onCourierFilter(ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    this.filteredCouriers = !needle
      ? [...this.allCouriers]
      : this.allCouriers.filter((x) => x.toLowerCase().includes(needle));
  }

  onCourierFilterRow(row: CollectionDraftRow, ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    row.filteredCouriers = !needle
      ? [...this.allCouriers]
      : this.allCouriers.filter((x) => x.toLowerCase().includes(needle));
  }

  onStatusFilter(ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    this.filteredStatuses = !needle
      ? [...this.allStatuses]
      : this.allStatuses.filter((x) => x.toLowerCase().includes(needle));
  }

  onStatusFilterRow(row: CollectionDraftRow, ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    row.filteredStatuses = !needle
      ? [...this.allStatuses]
      : this.allStatuses.filter((x) => x.toLowerCase().includes(needle));
  }

  onAmountStatusFilter(ev: Event): void {
    const v = String((ev.target as HTMLInputElement)?.value || '').toLowerCase();
    this.filteredAmountStatuses = this.amountStatusOptions.filter((x) => x.toLowerCase().includes(v));
  }

  onAmountStatusFilterRow(row: CollectionDraftRow, ev: Event): void {
    const v = String((ev.target as HTMLInputElement)?.value || '').toLowerCase();
    row.filteredAmountStatuses = this.amountStatusOptions.filter((x) => x.toLowerCase().includes(v));
  }

  onPincodeInput(): void {
    const pcCtrl = this.form.get('pincode');
    const raw = String(pcCtrl?.value || '').trim();
    pcCtrl?.setErrors(null);

    this.form.patchValue({ state: '', city: '', areaName: '' }, { emitEvent: false });
    this.resetPincodeLookupUi();

    if (/^\d{6}$/.test(raw)) {
      this.fetchPincodeDetails(raw);
    }
  }

  onPincodeInputRow(row: CollectionDraftRow): void {
    const pcCtrl = row.form.get('pincode');
    const raw = String(pcCtrl?.value || '').trim();
    pcCtrl?.setErrors(null);
    row.form.patchValue({ state: '', city: '', areaName: '' }, { emitEvent: false });
    this.resetRowPincodeUi(row);
    if (/^\d{6}$/.test(raw)) {
      this.fetchPincodeDetailsForRow(row, raw);
    }
  }

  private resetPincodeLookupUi(): void {
    this.pincodeAreaOptions = [];
    this.districtByAreaName = {};
    this.lastUniformDistrict = '';
    this.stateLocked = false;
    this.areaLocked = false;
    this.cityLocked = false;
  }

  private resetRowPincodeUi(row: CollectionDraftRow): void {
    row.pincodeAreaOptions = [];
    row.districtByAreaName = {};
    row.lastUniformDistrict = '';
    row.stateLocked = false;
    row.areaLocked = false;
    row.cityLocked = false;
  }

  private fetchPincodeDetails(pincode: string): void {
    this.pincodeLoading = true;
    this.pincodeLookup.lookup(pincode).subscribe((out) => {
      this.pincodeLoading = false;
      if (this.pincodeLookup.isUnavailable(out) || out.kind !== 'success') {
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

  private fetchPincodeDetailsForRow(row: CollectionDraftRow, pincode: string): void {
    row.pincodeLoading = true;
    this.pincodeLookup.lookup(pincode).subscribe((out) => {
      row.pincodeLoading = false;
      if (this.pincodeLookup.isUnavailable(out) || out.kind !== 'success') {
        return;
      }
      row.form.patchValue({ state: out.state }, { emitEvent: false });
      row.stateLocked = true;
      row.pincodeAreaOptions = out.areaNames;
      row.districtByAreaName = out.districtByAreaName;
      if (out.areaNames.length === 1) {
        const name = out.areaNames[0];
        const cityVal = row.districtByAreaName[name] || out.uniformDistrict || '';
        row.form.patchValue({ areaName: name, city: cityVal }, { emitEvent: false });
        row.areaLocked = true;
        row.cityLocked = !!cityVal;
      } else {
        row.lastUniformDistrict = out.uniformDistrict || '';
        row.form.patchValue({ areaName: '', city: row.lastUniformDistrict }, { emitEvent: false });
        row.areaLocked = false;
        row.cityLocked = !!row.lastUniformDistrict;
      }
    });
  }

  onAreaNameSelected(): void {
    if (this.pincodeAreaOptions.length <= 1) {
      return;
    }
    const area = String(this.form.get('areaName')?.value || '').trim();
    if (!area) {
      this.form.patchValue({ city: this.lastUniformDistrict }, { emitEvent: false });
      this.cityLocked = !!this.lastUniformDistrict;
      return;
    }
    const d = this.districtByAreaName[area] || '';
    this.form.patchValue({ city: d }, { emitEvent: false });
    this.cityLocked = !!d;
  }

  onAreaNameSelectedRow(row: CollectionDraftRow): void {
    if (row.pincodeAreaOptions.length <= 1) {
      return;
    }
    const area = String(row.form.get('areaName')?.value || '').trim();
    if (!area) {
      row.form.patchValue({ city: row.lastUniformDistrict }, { emitEvent: false });
      row.cityLocked = !!row.lastUniformDistrict;
      return;
    }
    const d = row.districtByAreaName[area] || '';
    row.form.patchValue({ city: d }, { emitEvent: false });
    row.cityLocked = !!d;
  }

  private buildPayloadFromDraftRow(row: CollectionDraftRow): Record<string, unknown> {
    const cid = this.metaForm.get('collectionCustomerId')?.value;
    const raw = row.form.getRawValue();
    const v: Record<string, unknown> = {
      ...raw,
      awbNo: row.awbNo,
      collectionCustomerId: cid,
      entryMonth: this.metaForm.get('entryMonth')?.value,
      entryYear: this.metaForm.get('entryYear')?.value,
      consumeRegistryAwbId: row.registryId
    };
    const entryDateRaw = v['entryDate'];
    if (entryDateRaw instanceof Date) {
      const d = entryDateRaw;
      v['entryDate'] =
        d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }
    return v;
  }

  saveDraftRow(row: CollectionDraftRow): void {
    if (this.metaForm.invalid) {
      this.toast.warning('Meta', 'Select collection customer, month, and year');
      return;
    }
    row.form.markAllAsTouched();
    if (row.form.invalid) {
      this.toast.warning('Row', 'Fix validation errors in this row');
      return;
    }
    row.saving = true;
    row.saveError = '';
    const v = this.buildPayloadFromDraftRow(row);
    this.api.post('/collection-center/entries', v).subscribe({
      next: () => {
        row.saving = false;
        this.toast.success('Saved', row.awbNo);
        this.removeDraftRow(row);
        const cid = this.metaForm.get('collectionCustomerId')?.value;
        if (cid) {
          this.loadPendingAwbs(String(cid));
        }
      },
      error: (err) => {
        row.saving = false;
        const msg = err?.error?.message || err?.error?.error || 'Save failed';
        row.saveError = typeof msg === 'string' ? msg : 'Save failed';
        this.toast.error('Error', row.saveError);
      }
    });
  }

  saveAllDraftRows(): void {
    if (this.metaForm.invalid) {
      this.toast.warning('Meta', 'Select collection customer, month, and year');
      return;
    }
    if (!this.draftRows.length) {
      this.toast.warning('Rows', 'Add at least one shipment row');
      return;
    }
    for (const row of this.draftRows) {
      row.form.markAllAsTouched();
      if (row.form.invalid) {
        this.toast.warning('Validation', 'Fix errors in all rows before saving together');
        return;
      }
    }
    this.savingAll = true;
    const payloads = this.draftRows.map((r) => this.buildPayloadFromDraftRow(r));
    from(payloads)
      .pipe(
        concatMap((body) => this.api.post('/collection-center/entries', body)),
        last()
      )
      .subscribe({
        next: () => {
          this.savingAll = false;
          this.toast.success('Saved', `${payloads.length} entr${payloads.length === 1 ? 'y' : 'ies'}`);
          this.back();
        },
        error: (err) => {
          this.savingAll = false;
          const msg = err?.error?.message || err?.error?.error || 'Save failed';
          this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
        }
      });
  }

  save() {
    if (!this.id || this.form.invalid) {
      return;
    }
    this.saving = true;
    const v: Record<string, unknown> = { ...this.form.getRawValue() };
    if (this.consumeRegistryAwbId) {
      v['consumeRegistryAwbId'] = this.consumeRegistryAwbId;
    }
    const entryDateRaw = v['entryDate'];
    if (entryDateRaw instanceof Date) {
      const d = entryDateRaw;
      v['entryDate'] =
        d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }
    const errFn = (err: any) => {
      this.saving = false;
      const msg = err?.error?.message || err?.error?.error || 'Save failed';
      this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
    };
    this.api.put(`/collection-center/entries`, this.id, v).subscribe({
      next: () => {
        this.saving = false;
        this.toast.success('Saved', '');
        this.back();
      },
      error: errFn
    });
  }

  trackDraftRow(_: number, row: CollectionDraftRow): string {
    return row.localId;
  }

  back() {
    this.router.navigate(['/client-entries/collection-center']);
  }
}
