import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../../../core/services/api.service';
import { CompanySettingsListsService } from '../../../../core/services/company-settings-lists.service';
import { PincodeLookupService } from '../../../../core/services/pincode-lookup.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-cash-booking-form',
  templateUrl: './cash-booking-form.component.html',
  styleUrls: ['./cash-booking-form.component.scss']
})
export class CashBookingFormComponent implements OnInit {
  form: FormGroup;
  id: string | null = null;
  lastUpdatedBy = '';
  saving = false;
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
  readonly amountStatusOptions = ['Pending', 'Partial', 'Paid', 'COD', 'UnPaid'];
  filteredAmountStatuses: string[] = [];
  readonly paymentModeOptions = ['Cash', 'GPAY', 'PhonePe', 'Paytm', 'Others'];

  fromPhoneChips: string[] = [];
  toPhoneChips: string[] = [];
  newFromPhoneInput = '';
  newToPhoneInput = '';
  fromPhoneError = '';
  toPhoneError = '';

  addFromPhone(): void {
    const val = String(this.newFromPhoneInput || '').trim();
    if (!val) return;
    if (!/^\d{10}$/.test(val)) {
      this.fromPhoneError = 'Phone number must be exactly 10 digits';
      return;
    }
    this.fromPhoneError = '';
    if (!this.fromPhoneChips.includes(val)) {
      this.fromPhoneChips.push(val);
    }
    this.newFromPhoneInput = '';
  }

  removeFromPhone(index: number): void {
    this.fromPhoneChips.splice(index, 1);
  }

  addToPhone(): void {
    const val = String(this.newToPhoneInput || '').trim();
    if (!val) return;
    if (!/^\d{10}$/.test(val)) {
      this.toPhoneError = 'Phone number must be exactly 10 digits';
      return;
    }
    this.toPhoneError = '';
    if (!this.toPhoneChips.includes(val)) {
      this.toPhoneChips.push(val);
    }
    this.newToPhoneInput = '';
  }

  removeToPhone(index: number): void {
    this.toPhoneChips.splice(index, 1);
  }

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private pincodeLookup: PincodeLookupService,
    private lists: CompanySettingsListsService,
    private route: ActivatedRoute,
    private router: Router,
    private toast: ToastService
  ) {
    this.form = this.fb.group({
      bookingDate: [new Date(), Validators.required],
      receiverName: ['', Validators.required],
      pincode: [''],
      state: [''],
      city: [''],
      areaName: [''],
      fullAddress: [''],
      awbNo: [''],
      courier: [''],
      weight: [null as number | null],
      item: [''],
      status: [''],
      amount: [null as number | null],
      receivedAmount: [null as number | null],
      amountStatus: [''],
      paymentMode: [''],
      otherPaymentMode: [''],
      remarks: ['']
    });
    this.filteredAmountStatuses = [...this.amountStatusOptions];
  }

  get pendingAmount(): number {
    const amt = Number(this.form.get('amount')?.value) || 0;
    const rec = Number(this.form.get('receivedAmount')?.value) || 0;
    return Math.max(0, amt - rec);
  }

  ngOnInit(): void {
    this.lists.getLists().subscribe({
      next: (l) => {
        this.allCouriers = l.couriers;
        this.allStatuses = l.statuses;
        this.filteredCouriers = [...this.allCouriers];
        this.filteredStatuses = [...this.allStatuses];
        this.filteredAmountStatuses = [...this.amountStatusOptions];
      },
      error: () => {}
    });
    const pid = this.route.snapshot.paramMap.get('id');
    if (pid) {
      this.id = pid;
      this.api.get<any>(`/cash-bookings/${pid}`).subscribe({
        next: (row) => {
          this.lastUpdatedBy = row.lastUpdatedBy ?? '';
          if (row.fromPhoneNumbers && row.fromPhoneNumbers.length > 0) {
            this.fromPhoneChips = [...row.fromPhoneNumbers];
          } else if (row.fromPhone) {
            this.fromPhoneChips = row.fromPhone.split(',').map((s: string) => s.trim()).filter((s: string) => s);
          }
          if (row.toPhoneNumbers && row.toPhoneNumbers.length > 0) {
            this.toPhoneChips = [...row.toPhoneNumbers];
          } else if (row.toPhone) {
            this.toPhoneChips = row.toPhone.split(',').map((s: string) => s.trim()).filter((s: string) => s);
          }
          this.form.patchValue({
            ...row,
            bookingDate: row.bookingDate ? new Date(row.bookingDate) : new Date()
          });
        },
        error: () => {
          this.toast.error('Error', 'Not found');
          this.router.navigate(['/cash-booking']);
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

  onAmountStatusFilter(ev: Event): void {
    const v = String((ev.target as HTMLInputElement)?.value || '').toLowerCase();
    this.filteredAmountStatuses = this.amountStatusOptions.filter((x) => x.toLowerCase().includes(v));
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

  onCourierFilter(ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    this.filteredCouriers = !needle ? [...this.allCouriers] : this.allCouriers.filter((x) => x.toLowerCase().includes(needle));
  }

  onStatusFilter(ev: Event): void {
    const needle = ((ev.target as HTMLInputElement).value || '').toLowerCase();
    this.filteredStatuses = !needle ? [...this.allStatuses] : this.allStatuses.filter((x) => x.toLowerCase().includes(needle));
  }

  private isoDate(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }

  private payload(): Record<string, unknown> {
    const v = this.form.getRawValue();
    return {
      bookingDate: this.isoDate(v.bookingDate as Date),
      receiverName: String(v.receiverName || '').trim(),
      pincode: String(v.pincode || '').trim(),
      state: String(v.state || '').trim(),
      city: String(v.city || '').trim(),
      areaName: String(v.areaName || '').trim(),
      fullAddress: String(v.fullAddress || '').trim(),
      awbNo: String(v.awbNo || '').trim(),
      courier: String(v.courier || '').trim(),
      weight: v.weight,
      item: String(v.item || '').trim(),
      status: String(v.status || '').trim(),
      amount: v.amount,
      receivedAmount: v.receivedAmount !== null && v.receivedAmount !== undefined && v.receivedAmount !== '' ? Number(v.receivedAmount) : null,
      amountStatus: String(v.amountStatus || '').trim(),
      paymentMode: String(v.paymentMode || '').trim(),
      otherPaymentMode: String(v.otherPaymentMode || '').trim(),
      fromPhoneNumbers: this.fromPhoneChips,
      toPhoneNumbers: this.toPhoneChips,
      fromPhone: this.fromPhoneChips.join(', '),
      toPhone: this.toPhoneChips.join(', '),
      remarks: String(v.remarks || '').trim()
    };
  }

  save(): void {
    if (this.form.invalid) {
      return;
    }
    const amt = Number(this.form.get('amount')?.value) || 0;
    const rec = this.form.get('receivedAmount')?.value;
    if (rec !== null && rec !== undefined && rec !== '') {
      const numRec = Number(rec);
      if (numRec < 0) {
        this.toast.error('Validation Error', 'Received amount cannot be negative');
        return;
      }
      if (numRec > amt) {
        this.toast.error('Validation Error', `Received amount (₹${numRec}) cannot exceed total amount (₹${amt})`);
        return;
      }
    }
    this.saving = true;
    const body = this.payload();
    const req = this.id
      ? this.api.put<any>('/cash-bookings', this.id, body)
      : this.api.post<any>('/cash-bookings', body);
    req.subscribe({
      next: () => {
        this.saving = false;
        this.toast.success('Saved', '');
        this.router.navigate(['/cash-booking']);
      },
      error: (e) => {
        this.saving = false;
        const msg = e?.error?.message || e?.error?.error || 'Save failed';
        this.toast.error('Error', typeof msg === 'string' ? msg : 'Save failed');
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/cash-booking']);
  }
}
