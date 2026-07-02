import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Location } from '@angular/common';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { ZoneConfiguration } from '../../../../core/models/zone-configuration.model';
import {
  courierCustomerDownloadFilename,
  filenameFromContentDisposition
} from '../../../../core/utils/content-disposition-filename.util';

@Component({
  selector: 'app-courier-quotation-form',
  template: `
    <div class="cq-page">
      <div class="cq-header">
        <div>
          <h1 class="cq-title">{{ quotationId ? 'Edit Courier Rate Quotation' : 'Create Courier Rate Quotation' }}</h1>
          <span *ngIf="quotationNumber" class="cq-qno">{{ quotationNumber }}</span>
          <span *ngIf="currentStatus" [class]="'cq-badge cq-badge-' + currentStatus.toLowerCase()">{{ currentStatus }}</span>
        </div>
        <div class="cq-hdr-actions">
           <button *ngIf="quotationId" mat-stroked-button (click)="downloadPdf()" matTooltip="Download PDF">
            <mat-icon>picture_as_pdf</mat-icon> PDF
          </button>
          <button *ngIf="quotationId" mat-stroked-button color="primary" (click)="sendEmail()" matTooltip="Send Email">
            <mat-icon>mail</mat-icon> Send Email
          </button>
          <button *ngIf="quotationId" mat-stroked-button color="primary" (click)="downloadExcel()" matTooltip="Export to Excel">
            <mat-icon>table_view</mat-icon> Excel
          </button>
          <button mat-stroked-button color="primary" (click)="goBack()" matTooltip="Go Back">
            <mat-icon>arrow_back</mat-icon> Back
          </button>
          <button mat-button (click)="cancel()"><mat-icon>close</mat-icon> Cancel</button>
        </div>
      </div>

      <mat-card class="cq-meta-card">
        <form [formGroup]="headerForm">
          <div class="cq-meta-grid">
            <mat-form-field *ngIf="!quotationId" appearance="outline">
              <mat-label>Quotation Number</mat-label>
              <input matInput formControlName="quotationNumber" placeholder="Leave blank for auto-generated">
              <mat-hint>Optional. Leave blank to auto-generate</mat-hint>
            </mat-form-field>

            <div class="cq-customer-row">
              <mat-form-field appearance="outline" class="cq-customer-field">
                <mat-label>Customer *</mat-label>
                <input matInput formControlName="customerSearch"
                       [matAutocomplete]="customerAuto"
                       (input)="onCustomerInput($event)"
                       (keyup)="onCustomerKeyUp($event)"
                       placeholder="Type to search customer...">
                <mat-autocomplete #customerAuto="matAutocomplete" [displayWith]="customerDisplay"
                                  (optionSelected)="onCustomerSelected($event)">
                  <mat-option *ngFor="let c of filteredCustomers" [value]="c">
                    <span style="font-weight:600">{{ c.partyName }}</span>
                    <span *ngIf="c.partyCode" style="color:#6B7280;font-size:11px;margin-left:8px">{{ c.partyCode }}</span>
                  </mat-option>
                  <mat-option *ngIf="filteredCustomers.length === 0 && customerSearchTerm.length >= 2" disabled>
                    No customers found
                  </mat-option>
                </mat-autocomplete>
                <mat-error>Customer is required</mat-error>
              </mat-form-field>
              <button mat-stroked-button type="button" class="cq-create-cust-btn" (click)="createCustomer()" matTooltip="Create new customer">
                <mat-icon>person_add</mat-icon> Create Customer
              </button>
            </div>

            <mat-form-field appearance="outline">
              <mat-label>Branch Name</mat-label>
              <input matInput formControlName="branchName" placeholder="e.g. SBI Padur Branch">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Effective Date *</mat-label>
              <input matInput [matDatepicker]="effPicker" formControlName="effectiveDate">
              <mat-datepicker-toggle matSuffix [for]="effPicker"></mat-datepicker-toggle>
              <mat-datepicker #effPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Valid Till Date *</mat-label>
              <input matInput [matDatepicker]="tillPicker" formControlName="validTillDate">
              <mat-datepicker-toggle matSuffix [for]="tillPicker"></mat-datepicker-toggle>
              <mat-datepicker #tillPicker></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Fuel Charge %</mat-label>
              <input matInput type="number" step="0.01" formControlName="fuelChargePercentage" placeholder="e.g. 15">
              <mat-hint>Applied on courier amount for invoice (e.g. 15 for 15%)</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>FOV Charges</mat-label>
              <input matInput type="number" step="0.01" formControlName="fovCharges" placeholder="e.g. 2">
              <mat-hint>Shown in quotation PDF (e.g. % or fixed value)</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Select Bank Account</mat-label>
              <mat-select formControlName="selectedBankAccountId" placeholder="For invoice RTGS/NEFT">
                <mat-option [value]="null">— None —</mat-option>
                <mat-option *ngFor="let b of bankAccountsList" [value]="b.id">{{ b.accountName }} – {{ b.accountNumber }}</mat-option>
              </mat-select>
              <mat-hint>Shown on Monthly Courier Invoice PDF</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline" class="cq-col-span-2">
              <mat-label>Remarks</mat-label>
              <input matInput formControlName="remarks" placeholder="Optional remarks">
            </mat-form-field>
          </div>
        </form>
      </mat-card>

      <mat-card class="cq-rates-card">
        <div class="cq-brand-header">
          <img [src]="getImageUrl('minhaEnterprises.jpeg')" alt="Minha Enterprises" class="cq-logo">
          <div class="cq-doc-title">QUOTATION FOR DOMESTIC COURIER SERVICE</div>
          <img [src]="getImageUrl('franchExpress.png')" alt="Franch Express" class="cq-logo">
        </div>

        <div class="cq-table-wrap">
          <table class="cq-table">
            <thead>
              <tr>
                <th class="cq-th cq-zone-th" rowspan="2">ZONE</th>
                <th class="cq-th cq-section-th" colspan="3">Express Rate</th>
                <th class="cq-th cq-section-th" colspan="2">Surface Rate</th>
              </tr>
              <tr>
                <th class="cq-th cq-col-th">First 250 Gms</th>
                <th class="cq-th cq-col-th">Every Add 500 Gms<br>upto 3 Kg</th>
                <th class="cq-th cq-col-th">Above 3 Kg<br>Per Kg</th>
                <th class="cq-th cq-col-th">Above 10 Kg<br>Upto 200 Kg</th>
                <th class="cq-th cq-col-th">Above 200 Kg<br>Upto 500 Kg</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngIf="s1Zones.length === 0"><td colspan="6" class="cq-td" style="padding:16px;">No dynamic EXPRESS_SURFACE zones configured in admin.</td></tr>
              <tr *ngFor="let zone of s1Zones">
                <td class="cq-td cq-zone-td" [innerHTML]="zoneLabelHtml(zone.zoneName)"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_E1')" (change)="setRate(zone.id + '_E1', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_E2')" (change)="setRate(zone.id + '_E2', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_E3')" (change)="setRate(zone.id + '_E3', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_S1')" (change)="setRate(zone.id + '_S1', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_S2')" (change)="setRate(zone.id + '_S2', $event)" placeholder="—" inputmode="decimal"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="cq-table-wrap cq-table-s2">
          <table class="cq-table">
            <thead>
              <tr>
                <th class="cq-th cq-zone-th" rowspan="2">ZONE</th>
                <th class="cq-th cq-section-th" colspan="3">Safety(Priority)</th>
                <th class="cq-th cq-section-th" colspan="2">Surface</th>
              </tr>
              <tr>
                <th class="cq-th cq-col-th">First 250 Gms</th>
                <th class="cq-th cq-col-th">Every Add 500 Gms<br>upto 3 Kg</th>
                <th class="cq-th cq-col-th">Above 3 Kg<br>Per Kg</th>
                <th class="cq-th cq-col-th">Above 10 Kg<br>Upto 200 Kg</th>
                <th class="cq-th cq-col-th">Above 200 Kg<br>Upto 500 Kg</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngIf="s2Zones.length === 0"><td colspan="6" class="cq-td" style="padding:16px;">No dynamic PRIORITY_SAFETY zones configured in admin.</td></tr>
              <tr *ngFor="let zone of s2Zones">
                <td class="cq-td cq-zone-td">{{ zone.zoneName }}</td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_PC1')" (change)="setRate(zone.id + '_PC1', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_PC2')" (change)="setRate(zone.id + '_PC2', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_PC3')" (change)="setRate(zone.id + '_PC3', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_SP1')" (change)="setRate(zone.id + '_SP1', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_SP2')" (change)="setRate(zone.id + '_SP2', $event)" placeholder="—" inputmode="decimal"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="cq-table-wrap cq-table-s2" *ngIf="s3Zones.length > 0">
          <table class="cq-table">
            <thead>
              <tr>
                <th class="cq-th cq-zone-th">ZONE</th>
                <th class="cq-th cq-section-th">1 Kg</th>
                <th class="cq-th cq-section-th">2 Kg</th>
                <th class="cq-th cq-section-th">3 Kg</th>
                <th class="cq-th cq-section-th">4 Kg</th>
                <th class="cq-th cq-section-th">5 Kg</th>
                <th class="cq-th cq-section-th">Above 5 Kg → Per Kg</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let zone of s3Zones">
                <td class="cq-td cq-zone-td">{{ zone.zoneName }}</td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_1')" (change)="setRate(zone.id + '_STD_1', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_2')" (change)="setRate(zone.id + '_STD_2', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_3')" (change)="setRate(zone.id + '_STD_3', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_4')" (change)="setRate(zone.id + '_STD_4', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_5')" (change)="setRate(zone.id + '_STD_5', $event)" placeholder="—" inputmode="decimal"></td>
                <td class="cq-td"><input class="cq-input" [value]="getRate(zone.id + '_STD_ABOVE3')" (change)="setRate(zone.id + '_STD_ABOVE3', $event)" placeholder="—" inputmode="decimal"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <p class="cq-hint">
          <mat-icon class="cq-hint-icon">info</mat-icon>
          Amount fields accept numeric values or leave blank for no rate.
        </p>

        <div class="cq-partners-footer">
          <div class="cq-partner-item">
            <img [src]="getImageUrl('brandPartner.png')" alt="Brand Partners" (error)="partnerLogoError.brandPartner = true" *ngIf="!partnerLogoError.brandPartner">
            <span class="cq-partner-fallback" *ngIf="partnerLogoError.brandPartner">Brand Partners</span>
          </div>
        </div>
      </mat-card>

      <app-last-updated-by-field *ngIf="quotationId" [value]="lastUpdatedBy"></app-last-updated-by-field>

      <div class="cq-action-bar">
        <button mat-raised-button color="primary" (click)="save()" [disabled]="loading">
          <mat-icon>save</mat-icon> {{ loading ? 'Saving…' : 'Save as Draft' }}
        </button>
        <button *ngIf="quotationId && currentStatus === 'DRAFT'" mat-raised-button color="accent"
                (click)="approve()" [disabled]="loading">
          <mat-icon>thumb_up</mat-icon> Approve
        </button>
        <button *ngIf="quotationId && currentStatus === 'APPROVED'" mat-raised-button
                class="cq-btn-activate" (click)="activate()" [disabled]="loading">
          <mat-icon>check_circle</mat-icon> Activate
        </button>
        <button *ngIf="quotationId && (currentStatus === 'ACTIVE' || currentStatus === 'APPROVED')"
                mat-raised-button color="warn" (click)="expire()" [disabled]="loading">
          <mat-icon>cancel</mat-icon> Expire
        </button>
        <button *ngIf="quotationId" mat-stroked-button (click)="print()">
          <mat-icon>print</mat-icon> Print
        </button>
        <button *ngIf="quotationId" mat-stroked-button (click)="downloadPdf()">
          <mat-icon>download</mat-icon> Download PDF
        </button>
      </div>
    </div>
  `,
  styles: [`
    .cq-page { padding: 24px; max-width: 1200px; margin: 0 auto; color: #1f2937; }
    .cq-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
    .cq-title { font-size: 20px; font-weight: 700; color: #0f1116; margin: 0 12px 0 0; }
    .cq-qno { font-size: 13px; font-weight: 600; color: #374151; background: #F3F4F6; padding: 2px 10px; border-radius: 8px; margin-right: 8px; }
    .cq-badge { font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 10px; text-transform: uppercase; }
    .cq-badge-draft    { background: #F3F4F6; color: #374151; }
    .cq-badge-approved { background: #DBEAFE; color: #1E40AF; }
    .cq-badge-active   { background: #D1FAE5; color: #065F46; }
    .cq-badge-expired  { background: #FEE2E2; color: #991B1B; }
    .cq-hdr-actions { display: flex; gap: 8px; }
    .cq-customer-row { display: flex; align-items: flex-start; gap: 8px; grid-column: span 2; }
    .cq-customer-field { flex: 1; }
    .cq-create-cust-btn { white-space: nowrap; height: 56px; margin-top: 0; }
    .cq-meta-card { padding: 20px; margin-bottom: 20px; }
    .cq-meta-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    .cq-col-span-2 { grid-column: span 2; }
    @media (max-width: 900px) {
      .cq-meta-grid { grid-template-columns: repeat(2, 1fr); }
      .cq-col-span-2 { grid-column: span 2; }
    }
    mat-form-field { width: 100%; }
    .cq-rates-card { padding: 24px; margin-bottom: 20px; }
    .cq-doc-title {
      font-size: 14px; font-weight: 700; text-align: center;
      letter-spacing: 0.08em; text-transform: uppercase;
      margin-bottom: 20px; color: #0f1116;
      border-bottom: 2px solid #E5E7EB; padding-bottom: 12px;
    }
    .cq-table-wrap { overflow-x: auto; margin-bottom: 4px; }
    .cq-table-s2 { margin-top: 20px; }
    .cq-table {
      width: 100%; border-collapse: collapse;
      font-size: 12px; font-family: 'Helvetica Neue', Arial, sans-serif;
      color: #1f2937;
    }
    .cq-th {
      border: 1px solid #9CA3AF; padding: 6px 8px;
      text-align: center; vertical-align: middle;
      font-weight: 700; line-height: 1.3; white-space: nowrap;
    }
    .cq-zone-th {
      background: #D1D5DB; min-width: 160px; width: 22%;
      font-size: 12px; letter-spacing: 0.04em;
    }
    .cq-section-th { background: #E5E7EB; font-size: 12px; }
    .cq-col-th { background: #F3F4F6; font-size: 11px; font-weight: 600; white-space: normal; min-width: 100px; }
    .cq-td {
      border: 1px solid #9CA3AF; padding: 0;
      vertical-align: middle; text-align: center;
    }
    .cq-zone-td {
      background: #FAFAFA; padding: 8px 10px;
      text-align: left; font-weight: 500;
      white-space: pre-line; line-height: 1.4;
      min-width: 160px;
    }
    .cq-input {
      width: 100%; box-sizing: border-box;
      border: none; outline: none;
      text-align: center; font-size: 12px;
      padding: 7px 6px;
      background: transparent; color: #111827;
      font-family: inherit;
      font-weight: 500;
    }
    .cq-input:focus { background: #EFF6FF; }
    .cq-input::placeholder { color: #D1D5DB; }
    .cq-hint { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #4b5563; margin-top: 12px; }
    .cq-hint-icon { font-size: 14px; width: 14px; height: 14px; }
    .cq-action-bar { display: flex; gap: 12px; flex-wrap: wrap; padding: 8px 0; }
    .cq-btn-activate { background: #16A34A !important; color: white !important; }
    .cq-brand-header {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: center;
      justify-items: center;
      gap: 16px;
      margin-bottom: 20px;
      padding-bottom: 20px;
      border-bottom: 2px solid #E5E7EB;
    }
    .cq-brand-header .cq-logo:first-of-type { justify-self: start; }
    .cq-brand-header .cq-logo:last-of-type { justify-self: end; }
    .cq-brand-header .cq-doc-title { border-bottom: none; margin-bottom: 0; padding-bottom: 0; justify-self: center; }
    .cq-logo { height: 56px; width: auto; max-width: 140px; object-fit: contain; object-position: center; }
    .cq-partners-footer {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #E5E7EB;
      flex-wrap: wrap;
      max-width: 100%;
      margin-left: auto;
      margin-right: auto;
    }
    .cq-partner-item {
      display: flex;
      align-items: center;
      justify-content: center;
      min-width: 320px;
      height: 80px;
    }
    .cq-partners-footer img {
      height: 80px;
      width: auto;
      max-width: 480px;
      object-fit: contain;
      object-position: center;
    }
    .cq-partner-fallback {
      font-size: 12px;
      font-weight: 600;
      color: #1f2937;
    }
  `]
})
export class CourierQuotationFormComponent implements OnInit {

  headerForm!: FormGroup;
  quotationId: string | null = null;
  lastUpdatedBy = '';
  quotationNumber: string | null = null;
  currentStatus: string | null = null;
  loading = false;

  rates: Record<string, string> = {};

  partnerLogoError: { brandPartner: boolean } = {
    brandPartner: false
  };

  filteredCustomers: any[] = [];
  customerSearchTerm = '';
  private customerSearch$ = new Subject<string>();

  allZones: ZoneConfiguration[] = [];
  s1Zones: ZoneConfiguration[] = [];
  s2Zones: ZoneConfiguration[] = [];
  s3Zones: ZoneConfiguration[] = [];
  bankAccountsList: { id: string; accountName: string; accountNumber: string }[] = [];

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private zoneConfigService: ZoneConfigurationService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService,
    private http: HttpClient,
    private location: Location
  ) { }

  ngOnInit() {
    this.headerForm = this.fb.group({
      quotationNumber: [''],
      customerId: ['', Validators.required],
      customerSearch: [''],
      customerName: [''],
      branchName: [''],
      fuelChargePercentage: [15],
      fovCharges: [null as number | null],
      selectedBankAccountId: [null as string | null],
      effectiveDate: [new Date(), Validators.required],
      validTillDate: [null, Validators.required],
      remarks: ['']
    });

    this.customerSearch$.pipe(debounceTime(300), distinctUntilChanged()).subscribe(term => {
      if (term.length >= 2) {
        this.apiService.search<any>('/clients', term, 0, 20).subscribe({
          next: (res) => {
            this.filteredCustomers = (res.content || []).filter(
              (p: any) => !p.partyType || p.partyType === 'CUSTOMER' || p.partyType === 'BOTH'
            );
          },
          error: () => this.filteredCustomers = []
        });
      } else {
        this.filteredCustomers = [];
      }
    });

    const navState = this.router.getCurrentNavigation()?.extras?.state || (window.history.state as any);
    if (navState?.selectedParty) {
      const c = navState.selectedParty;
      this.headerForm.patchValue({
        customerId: c.id, customerSearch: c.partyName, customerName: c.partyName
      });
    }

    const id = this.route.snapshot.paramMap.get('id');

    this.apiService.get<any>('/company-settings').subscribe({
      next: (s) => {
        this.bankAccountsList = (s.bankAccounts || []).map((b: any) => ({
          id: b.id || '',
          accountName: b.accountName || '',
          accountNumber: b.accountNumber || ''
        }));
      }
    });

    this.zoneConfigService.getActiveZones().subscribe({
      next: (zones) => {
        this.allZones = zones || [];
        this.s1Zones = this.allZones.filter(z => z.zoneType === 'EXPRESS_SURFACE');
        this.s2Zones = this.allZones.filter(z => z.zoneType === 'PRIORITY_SAFETY');
        this.s3Zones = this.allZones.filter(z => z.zoneType === 'STANDARD');
        if (id) {
          this.quotationId = id;
          this.loadQuotation(id);
        }
      },
      error: () => {
        this.toastService.error('Error', 'Failed to load dynamic zones');
        if (id) { this.quotationId = id; this.loadQuotation(id); }
      }
    });
  }

  getImageUrl(imageName: string): string {
    return this.apiService.getBaseUrl() + '/images/' + imageName;
  }

  getRate(key: string): string {
    const val = this.rates[key];
    if (!val) return '';
    return `₹ ${val}`;
  }

  setRate(key: string, event: Event) {
    const input = event.target as HTMLInputElement;
    let val = input.value.trim();
    val = val.replace(/[^0-9.]/g, '');

    if (val && !isNaN(Number(val))) {
      this.rates[key] = Number(val).toString();
    } else {
      delete this.rates[key];
    }

    if (this.rates[key]) {
      input.value = `₹ ${this.rates[key]}`;
    } else {
      input.value = '';
    }
  }

  onCustomerInput(ev: Event) {
    this.customerSearchTerm = (ev.target as HTMLInputElement).value;
    this.customerSearch$.next(this.customerSearchTerm);
  }

  onCustomerKeyUp(ev: KeyboardEvent) {
    if (!(ev.target as HTMLInputElement).value) {
      this.headerForm.patchValue({ customerId: '', customerName: '' });
      this.filteredCustomers = [];
    }
  }

  onCustomerSelected(ev: any) {
    const c = ev.option.value;
    this.headerForm.patchValue({ customerId: c.id, customerSearch: c.partyName, customerName: c.partyName });
  }

  customerDisplay(c: any | string): string {
    return typeof c === 'string' ? c : (c?.partyName || '');
  }

  createCustomer() {
    this.router.navigate(['/parties/create'], {
      queryParams: { returnUrl: this.router.url }
    });
  }

  zoneLabelHtml(label: string): string {
    return label.replace(/\n/g, '<br>');
  }

  loadQuotation(id: string) {
    this.loading = true;
    this.apiService.get<any>(`/courier-quotations/${id}`).subscribe({
      next: (q) => {
        this.lastUpdatedBy = q.lastUpdatedBy ?? '';
        this.quotationNumber = q.quotationNumber;
        this.currentStatus = q.status;

        // Transform incoming flat array zoneRates -> dict
        this.rates = {};
        if (q.zoneRates && Array.isArray(q.zoneRates)) {
          q.zoneRates.forEach((zr: any) => {
            const id = zr.zoneId;
            const zoneMeta = this.allZones.find(z => z.id === id);
            const zt = zoneMeta?.zoneType;
            // Use zone-type-specific keys so PRIORITY_SAFETY edits are not overwritten by stale EXPRESS_SURFACE *_E* keys on save
            if (zt === 'PRIORITY_SAFETY') {
              if (zr.expressBaseRate != null) this.rates[`${id}_PC1`] = String(zr.expressBaseRate);
              if (zr.expressIncrementalRate != null) this.rates[`${id}_PC2`] = String(zr.expressIncrementalRate);
              if (zr.expressPerKgRate != null) this.rates[`${id}_PC3`] = String(zr.expressPerKgRate);
              if (zr.surfaceSlab1Rate != null) this.rates[`${id}_SP1`] = String(zr.surfaceSlab1Rate);
              if (zr.surfaceSlab2Rate != null) this.rates[`${id}_SP2`] = String(zr.surfaceSlab2Rate);
            } else if (zt === 'EXPRESS_SURFACE') {
              if (zr.expressBaseRate != null) this.rates[`${id}_E1`] = String(zr.expressBaseRate);
              if (zr.expressIncrementalRate != null) this.rates[`${id}_E2`] = String(zr.expressIncrementalRate);
              if (zr.expressPerKgRate != null) this.rates[`${id}_E3`] = String(zr.expressPerKgRate);
              if (zr.surfaceSlab1Rate != null) this.rates[`${id}_S1`] = String(zr.surfaceSlab1Rate);
              if (zr.surfaceSlab2Rate != null) this.rates[`${id}_S2`] = String(zr.surfaceSlab2Rate);
            } else if (zt === 'STANDARD') {
              if (zr.standardRate1Kg != null) this.rates[`${id}_STD_1`] = String(zr.standardRate1Kg);
              if (zr.standardRate2Kg != null) this.rates[`${id}_STD_2`] = String(zr.standardRate2Kg);
              if (zr.standardRate3Kg != null) this.rates[`${id}_STD_3`] = String(zr.standardRate3Kg);
              if (zr.standardRate4Kg != null) this.rates[`${id}_STD_4`] = String(zr.standardRate4Kg);
              if (zr.standardRate5Kg != null) this.rates[`${id}_STD_5`] = String(zr.standardRate5Kg);
              if (zr.standardPerKgAbove3 != null) this.rates[`${id}_STD_ABOVE3`] = String(zr.standardPerKgAbove3);
              if (zr.standardBaseRate3Kg != null) this.rates[`${id}_STD_BASE`] = String(zr.standardBaseRate3Kg);
              if (zr.standardAdditionalPerKg != null) this.rates[`${id}_STD_ADD`] = String(zr.standardAdditionalPerKg);
              if (this.rates[`${id}_STD_3`] == null && this.rates[`${id}_STD_BASE`] != null) {
                this.rates[`${id}_STD_3`] = this.rates[`${id}_STD_BASE`];
              }
              if (this.rates[`${id}_STD_ABOVE3`] == null && this.rates[`${id}_STD_ADD`] != null) {
                this.rates[`${id}_STD_ABOVE3`] = this.rates[`${id}_STD_ADD`];
              }
            } else {
              if (zr.expressBaseRate != null) {
                this.rates[`${id}_E1`] = String(zr.expressBaseRate);
                this.rates[`${id}_PC1`] = String(zr.expressBaseRate);
              }
              if (zr.expressIncrementalRate != null) {
                this.rates[`${id}_E2`] = String(zr.expressIncrementalRate);
                this.rates[`${id}_PC2`] = String(zr.expressIncrementalRate);
              }
              if (zr.expressPerKgRate != null) {
                this.rates[`${id}_E3`] = String(zr.expressPerKgRate);
                this.rates[`${id}_PC3`] = String(zr.expressPerKgRate);
              }
              if (zr.surfaceSlab1Rate != null) {
                this.rates[`${id}_S1`] = String(zr.surfaceSlab1Rate);
                this.rates[`${id}_SP1`] = String(zr.surfaceSlab1Rate);
              }
              if (zr.surfaceSlab2Rate != null) {
                this.rates[`${id}_S2`] = String(zr.surfaceSlab2Rate);
                this.rates[`${id}_SP2`] = String(zr.surfaceSlab2Rate);
              }
            }
          });
        }
        this.headerForm.patchValue({
          customerId: q.customerId,
          customerSearch: q.customerName,
          customerName: q.customerName,
          branchName: q.branchName,
          fuelChargePercentage: q.fuelChargePercentage ?? 15,
          fovCharges: q.fovCharges ?? null,
          selectedBankAccountId: q.selectedBankAccountId ?? null,
          effectiveDate: q.effectiveDate ? new Date(q.effectiveDate) : null,
          validTillDate: q.validTillDate ? new Date(q.validTillDate) : null,
          remarks: q.remarks
        });
        this.loading = false;
      },
      error: () => { this.toastService.error('Error', 'Failed to load quotation'); this.loading = false; }
    });
  }

  private buildPayload() {
    const v = this.headerForm.value;

    const formatDate = (date: any) => {
      if (!date) return null;
      const d = new Date(date);
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };

    const payload: any = {
      customerId: v.customerId,
      customerName: v.customerName,
      branchName: v.branchName,
      fuelChargePercentage: v.fuelChargePercentage != null ? Number(v.fuelChargePercentage) : null,
      fovCharges: v.fovCharges != null && v.fovCharges !== '' ? Number(v.fovCharges) : null,
      selectedBankAccountId: v.selectedBankAccountId || null,
      effectiveDate: formatDate(v.effectiveDate),
      validTillDate: formatDate(v.validTillDate),
      remarks: v.remarks,
      zoneRates: this.buildZoneRatesPayload()
    };
    if (!this.quotationId && v.quotationNumber?.trim()) {
      payload.quotationNumber = v.quotationNumber.trim();
    }
    return payload;
  }

  private parseRateCell(key: string): number {
    const raw = this.rates[key];
    if (raw == null || raw === '') return 0;
    const n = Number(String(raw).replace(/[^0-9.-]/g, ''));
    return Number.isFinite(n) ? n : 0;
  }

  private buildZoneRatesPayload(): any[] {
    const arr: any[] = [];
    for (const z of this.allZones) {
      if (!z.id) continue;
      const id = z.id;
      const std1 = this.parseRateCell(`${id}_STD_1`);
      const std2 = this.parseRateCell(`${id}_STD_2`);
      const std3 = this.parseRateCell(`${id}_STD_3`);
      const std4 = this.parseRateCell(`${id}_STD_4`);
      const std5 = this.parseRateCell(`${id}_STD_5`);
      const stdAbove3 = this.parseRateCell(`${id}_STD_ABOVE3`);

      if (z.zoneType === 'STANDARD') {
        if (std1 || std2 || std3 || std4 || std5 || stdAbove3) {
          arr.push({
            zoneId: id,
            zoneName: z.zoneName,
            standardRate1Kg: std1,
            standardRate2Kg: std2,
            standardRate3Kg: std3,
            standardRate4Kg: std4,
            standardRate5Kg: std5,
            standardPerKgAbove3: stdAbove3
          });
        }
      } else if (z.zoneType === 'PRIORITY_SAFETY') {
        const e1 = this.parseRateCell(`${id}_PC1`);
        const e2 = this.parseRateCell(`${id}_PC2`);
        const e3 = this.parseRateCell(`${id}_PC3`);
        const s1 = this.parseRateCell(`${id}_SP1`);
        const s2 = this.parseRateCell(`${id}_SP2`);
        if (e1 || e2 || e3 || s1 || s2) {
          arr.push({
            zoneId: id,
            zoneName: z.zoneName,
            expressBaseRate: e1,
            expressIncrementalRate: e2,
            expressPerKgRate: e3,
            surfaceSlab1Rate: s1,
            surfaceSlab2Rate: s2
          });
        }
      } else if (z.zoneType === 'EXPRESS_SURFACE') {
        const e1 = this.parseRateCell(`${id}_E1`);
        const e2 = this.parseRateCell(`${id}_E2`);
        const e3 = this.parseRateCell(`${id}_E3`);
        const s1 = this.parseRateCell(`${id}_S1`);
        const s2 = this.parseRateCell(`${id}_S2`);
        if (e1 || e2 || e3 || s1 || s2) {
          arr.push({
            zoneId: id,
            zoneName: z.zoneName,
            expressBaseRate: e1,
            expressIncrementalRate: e2,
            expressPerKgRate: e3,
            surfaceSlab1Rate: s1,
            surfaceSlab2Rate: s2
          });
        }
      } else {
        const e1 = this.parseRateCell(`${id}_E1`) || this.parseRateCell(`${id}_PC1`);
        const e2 = this.parseRateCell(`${id}_E2`) || this.parseRateCell(`${id}_PC2`);
        const e3 = this.parseRateCell(`${id}_E3`) || this.parseRateCell(`${id}_PC3`);
        const s1 = this.parseRateCell(`${id}_S1`) || this.parseRateCell(`${id}_SP1`);
        const s2 = this.parseRateCell(`${id}_S2`) || this.parseRateCell(`${id}_SP2`);
        if (e1 || e2 || e3 || s1 || s2) {
          arr.push({
            zoneId: id,
            zoneName: z.zoneName,
            expressBaseRate: e1,
            expressIncrementalRate: e2,
            expressPerKgRate: e3,
            surfaceSlab1Rate: s1,
            surfaceSlab2Rate: s2
          });
        }
      }
    }
    return arr;
  }

  save() {
    if (!this.headerForm.get('customerId')?.value) {
      this.toastService.warning('Validation', 'Please select a customer');
      return;
    }

    const data = this.buildPayload();

    if (data.effectiveDate && data.validTillDate) {
      if (data.effectiveDate > data.validTillDate) {
        this.toastService.warning('Validation', 'Effective date must be on or before Valid Till date');
        return;
      }
    }

    this.loading = true;
    const req = this.quotationId
      ? this.apiService.put('/courier-quotations', this.quotationId, data)
      : this.apiService.post('/courier-quotations', data);

    req.subscribe({
      next: (res: any) => {
        this.toastService.success('Success', this.quotationId ? 'Quotation updated' : 'Saved as Draft');
        if (!this.quotationId) {
          this.quotationId = res.id;
          this.quotationNumber = res.quotationNumber;
          this.currentStatus = 'DRAFT';
        }
        this.loading = false;
      },
      error: (err: any) => {
        this.toastService.error('Error', err?.error?.message || 'Save failed');
        this.loading = false;
      }
    });
  }

  approve() {
    this.http.patch(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/approve`, {}).subscribe({
      next: () => { this.currentStatus = 'APPROVED'; this.toastService.success('Success', 'Quotation approved'); },
      error: (e: any) => this.toastService.error('Error', e?.error?.message || 'Approval failed')
    });
  }

  activate() {
    this.http.patch(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/activate`, {}).subscribe({
      next: () => { this.currentStatus = 'ACTIVE'; this.toastService.success('Success', 'Quotation activated'); },
      error: (e: any) => this.toastService.error('Error', e?.error?.message || 'Activation failed')
    });
  }

  expire() {
    this.http.patch(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/expire`, {}).subscribe({
      next: () => { this.currentStatus = 'EXPIRED'; this.toastService.success('Success', 'Quotation expired'); },
      error: (e: any) => this.toastService.error('Error', e?.error?.message || 'Action failed')
    });
  }

  print() {
    this.http.get(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/print`,
      { responseType: 'blob', observe: 'response' }).subscribe({
        next: (res: any) => {
          const url = window.URL.createObjectURL(res.body);
          const w = window.open(url, '_blank');
          if (w) w.onload = () => setTimeout(() => w.print(), 500);
        },
        error: () => this.toastService.error('Error', 'Failed to generate PDF')
      });
  }

  downloadPdf() {
    this.toastService.info('Downloading', 'Generating PDF…');
    this.http.get(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/pdf`,
      { responseType: 'blob', observe: 'response' }).subscribe({
        next: (res: any) => {
          const cn = this.headerForm?.get('customerName')?.value;
          const fallback = courierCustomerDownloadFilename(cn, 'pdf');
          const name = filenameFromContentDisposition(res.headers?.get('content-disposition'), fallback);
          const url = window.URL.createObjectURL(res.body);
          const a = document.createElement('a'); a.href = url;
          a.download = name;
          document.body.appendChild(a); a.click();
          document.body.removeChild(a); window.URL.revokeObjectURL(url);
          this.toastService.success('Success', 'PDF downloaded');
        },
        error: () => this.toastService.error('Error', 'Failed to download PDF')
      });
  }

  downloadExcel() {
    if (!this.quotationId) return;
    this.toastService.info('Downloading', 'Generating Excel file...');
    const apiUrl = this.apiService.getBaseUrl();
    this.http.get(`${apiUrl}/courier-quotations/${this.quotationId}/export/excel`, { responseType: 'blob', observe: 'response' }).subscribe({
      next: (res: any) => {
        const contentDisposition = res.headers.get('content-disposition');
        const cn = this.headerForm?.get('customerName')?.value;
        const fallback = courierCustomerDownloadFilename(cn, 'xlsx');
        let filename = filenameFromContentDisposition(contentDisposition, fallback);
        const url = window.URL.createObjectURL(res.body);
        const a = document.createElement('a');
        a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.toastService.success('Success', 'Excel downloaded');
      },
      error: () => this.toastService.error('Error', 'Failed to download Excel')
    });
  }

  sendEmail() {
    if (!this.quotationId) return;
    this.toastService.info('Sending', 'Sending email...');
    this.http.post<any>(`${this.apiService.getBaseUrl()}/courier-quotations/${this.quotationId}/email`, {})
      .subscribe({
        next: (res: any) => {
          this.toastService.success('Success', res?.message || 'Email sent successfully');
        },
        error: (err: any) => {
          this.toastService.error('Error', err?.error?.message || 'Failed to send email');
        }
      });
  }

  cancel() { this.router.navigate(['/courier-quotations']); }

  goBack() {
    this.location.back();
  }
}
