import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { ApiService } from '../../../../core/services/api.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ZoneConfigurationService } from '../../../../core/services/zone-configuration.service';
import { ZoneConfiguration, ZoneRateConfig } from '../../../../core/models/zone-configuration.model';
import { OnboardQuotationService } from '../../../../core/services/onboard-quotation.service';
import { OnboardQuotation, OnboardSlab } from '../../../../core/models/onboard-quotation.model';

@Component({
  selector: 'app-onboard-quotation-form',
  template: `
    <div class="cq-page">
      <div class="cq-header">
        <div>
          <h1 class="cq-title">{{ quotationId ? 'Edit Onboard Rate Quotation' : 'Create Onboard Rate Quotation' }}</h1>
          <span *ngIf="quotationNumber" class="cq-qno">{{ quotationNumber }}</span>
          <span *ngIf="currentStatus" [class]="'cq-badge cq-badge-' + currentStatus.toLowerCase()">{{ currentStatus }}</span>
        </div>
        <div class="cq-hdr-actions">
          <ng-container *ngIf="quotationId">
            <button *appHasPermission="'ONBOARD_QUOTATION:download'" mat-stroked-button (click)="downloadPdf()" matTooltip="Download PDF">
              <mat-icon>picture_as_pdf</mat-icon> PDF
            </button>
            <button *appHasPermission="'ONBOARD_QUOTATION:email'" mat-stroked-button color="primary" (click)="sendEmail()" matTooltip="Send Email">
              <mat-icon>mail</mat-icon> Send Email
            </button>
            <button *appHasPermission="'ONBOARD_QUOTATION:export'" mat-stroked-button color="primary" (click)="downloadExcel()" matTooltip="Export to Excel">
              <mat-icon>table_view</mat-icon> Excel
            </button>
          </ng-container>
          <button mat-stroked-button color="primary" (click)="goBack()" matTooltip="Go Back">
            <mat-icon>arrow_back</mat-icon> Back
          </button>
          <button mat-button (click)="cancel()"><mat-icon>close</mat-icon> Cancel</button>
        </div>
      </div>

      <!-- Header Information Form -->
      <mat-card class="cq-meta-card">
        <form [formGroup]="headerForm">
          <div class="cq-meta-grid">
            <mat-form-field *ngIf="!quotationId" appearance="outline">
              <mat-label>Quotation Number</mat-label>
              <input matInput formControlName="quotationNumber" placeholder="Leave blank for auto-generated">
              <mat-hint>Optional. Leave blank to auto-generate</mat-hint>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Prospective Customer Name *</mat-label>
              <input matInput formControlName="customerName" placeholder="e.g. ABC Enterprises">
              <mat-error *ngIf="headerForm.get('customerName')?.hasError('required')">Customer name is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Branch Name</mat-label>
              <input matInput formControlName="branchName" placeholder="e.g. Head Office / Branch">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Effective Date *</mat-label>
              <input matInput [matDatepicker]="effPicker" formControlName="effectiveDate">
              <mat-datepicker-toggle matSuffix [for]="effPicker"></mat-datepicker-toggle>
              <mat-datepicker #effPicker></mat-datepicker>
              <mat-error *ngIf="headerForm.get('effectiveDate')?.hasError('required')">Effective date is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Valid Till Date *</mat-label>
              <input matInput [matDatepicker]="tillPicker" formControlName="validTillDate">
              <mat-datepicker-toggle matSuffix [for]="tillPicker"></mat-datepicker-toggle>
              <mat-datepicker #tillPicker></mat-datepicker>
              <mat-error *ngIf="headerForm.get('validTillDate')?.hasError('required')">Valid till date is required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Fuel Charge %</mat-label>
              <input matInput type="number" step="0.01" formControlName="fuelChargePercentage" placeholder="e.g. 15">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>FOV Charges</mat-label>
              <input matInput type="number" step="0.01" formControlName="fovCharges" placeholder="e.g. 2">
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Select Bank Account</mat-label>
              <mat-select formControlName="selectedBankAccountId">
                <mat-option [value]="null">— None —</mat-option>
                <mat-option *ngFor="let b of bankAccountsList" [value]="b.id">{{ b.accountName }} – {{ b.accountNumber }}</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="cq-col-span-2">
              <mat-label>Remarks</mat-label>
              <input matInput formControlName="remarks" placeholder="Optional notes for prospective client">
            </mat-form-field>
          </div>
        </form>
      </mat-card>

      <!-- Slab Management & Rate Matrix Card -->
      <mat-card class="cq-rates-card">
        <div class="slab-toolbar">
          <div class="slab-toolbar-left">
            <mat-icon color="primary">layers</mat-icon>
            <span class="slab-toolbar-title">Custom Quotation Slabs</span>
            <span class="slab-count-tag">{{ slabs.length }} Slab(s) Configured</span>
          </div>
          <button mat-raised-button color="primary" (click)="addSlab()" type="button">
            <mat-icon>add</mat-icon> Add New Slab
          </button>
        </div>

        <!-- Slab Tabs Bar -->
        <div class="slab-tabs-bar">
          <div *ngFor="let slab of slabs; let i = index"
               class="slab-tab-item"
               [class.active]="i === activeSlabIndex"
               (click)="activeSlabIndex = i">
            <span class="slab-tab-title">{{ slab.slabName || ('Slab ' + (i + 1)) }}</span>
            <mat-icon *ngIf="slab.selected !== false" class="slab-tab-icon success" matTooltip="Included in quotation">check_circle</mat-icon>
            <mat-icon *ngIf="slab.selected === false" class="slab-tab-icon muted" matTooltip="Excluded from quotation">cancel</mat-icon>
          </div>
        </div>

        <!-- Active Slab Configuration Area -->
        <div class="active-slab-container" *ngIf="activeSlab">
          <div class="slab-config-bar">
            <div class="slab-name-group">
              <label class="slab-name-label">Slab Name:</label>
              <input class="slab-name-input" [(ngModel)]="activeSlab.slabName" placeholder="e.g. Slab A - Economy">
            </div>

            <div class="slab-checkbox-group">
              <mat-checkbox color="primary" [(ngModel)]="activeSlab.selected">
                <strong>Include this Slab in Quotation Document</strong>
              </mat-checkbox>
            </div>

            <button *ngIf="slabs.length > 1" mat-stroked-button color="warn" (click)="deleteSlab(activeSlabIndex)" type="button">
              <mat-icon>delete</mat-icon> Delete Slab
            </button>
          </div>

          <!-- Rate Matrix Tables for Active Slab -->
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
                <tr *ngIf="s1Zones.length === 0"><td colspan="6" class="cq-td" style="padding:16px;">No EXPRESS_SURFACE zones configured in admin panel.</td></tr>
                <tr *ngFor="let zone of s1Zones">
                  <td class="cq-td cq-zone-td" [innerHTML]="zoneLabelHtml(zone.zoneName)"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_E1')" (change)="setRate(activeSlab, zone.id + '_E1', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_E2')" (change)="setRate(activeSlab, zone.id + '_E2', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_E3')" (change)="setRate(activeSlab, zone.id + '_E3', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_S1')" (change)="setRate(activeSlab, zone.id + '_S1', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_S2')" (change)="setRate(activeSlab, zone.id + '_S2', $event)" placeholder="—" inputmode="decimal"></td>
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
                <tr *ngIf="s2Zones.length === 0"><td colspan="6" class="cq-td" style="padding:16px;">No PRIORITY_SAFETY zones configured in admin panel.</td></tr>
                <tr *ngFor="let zone of s2Zones">
                  <td class="cq-td cq-zone-td">{{ zone.zoneName }}</td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_PC1')" (change)="setRate(activeSlab, zone.id + '_PC1', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_PC2')" (change)="setRate(activeSlab, zone.id + '_PC2', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_PC3')" (change)="setRate(activeSlab, zone.id + '_PC3', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_SP1')" (change)="setRate(activeSlab, zone.id + '_SP1', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_SP2')" (change)="setRate(activeSlab, zone.id + '_SP2', $event)" placeholder="—" inputmode="decimal"></td>
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
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_1')" (change)="setRate(activeSlab, zone.id + '_STD_1', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_2')" (change)="setRate(activeSlab, zone.id + '_STD_2', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_3')" (change)="setRate(activeSlab, zone.id + '_STD_3', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_4')" (change)="setRate(activeSlab, zone.id + '_STD_4', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_5')" (change)="setRate(activeSlab, zone.id + '_STD_5', $event)" placeholder="—" inputmode="decimal"></td>
                  <td class="cq-td"><input class="cq-input" [value]="getRate(activeSlab, zone.id + '_STD_ABOVE3')" (change)="setRate(activeSlab, zone.id + '_STD_ABOVE3', $event)" placeholder="—" inputmode="decimal"></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="cq-footer-actions">
          <button [appDisableIfNoPermission]="quotationId ? 'ONBOARD_QUOTATION:edit' : 'ONBOARD_QUOTATION:create'" mat-raised-button color="primary" (click)="save()" [disabled]="saving" class="btn-primary">
            <mat-icon>save</mat-icon>
            {{ saving ? 'Saving...' : (quotationId ? 'Update Onboard Quotation' : 'Save Onboard Quotation') }}
          </button>
          <button mat-button (click)="cancel()">Cancel</button>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    .cq-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .cq-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 16px; }
    .cq-title { margin: 0; font-size: 1.5rem; font-weight: 700; color: #0f172a; }
    .cq-qno { font-size: 0.9rem; color: #64748b; font-weight: 600; margin-left: 8px; }
    .cq-badge { font-size: 11px; font-weight: 700; text-transform: uppercase; padding: 2px 8px; border-radius: 4px; margin-left: 8px; }
    .cq-badge-draft { background: #f1f5f9; color: #475569; }
    .cq-badge-approved { background: #dcfce7; color: #15803d; }
    .cq-hdr-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .cq-meta-card { padding: 20px !important; margin-bottom: 20px; border-radius: 12px !important; border: 1px solid #e2e8f0; }
    .cq-meta-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
    .cq-col-span-2 { grid-column: span 2; }
    .cq-rates-card { padding: 20px !important; border-radius: 12px !important; border: 1px solid #e2e8f0; }

    .slab-toolbar { display: flex; justify-content: space-between; align-items: center; padding-bottom: 16px; border-bottom: 1px solid #f1f5f9; margin-bottom: 16px; flex-wrap: wrap; gap: 12px; }
    .slab-toolbar-left { display: flex; align-items: center; gap: 10px; }
    .slab-toolbar-title { font-size: 1.1rem; font-weight: 700; color: #0f172a; }
    .slab-count-tag { font-size: 12px; font-weight: 600; background: #eef2ff; color: #4338ca; padding: 2px 10px; border-radius: 999px; border: 1px solid #c7d2fe; }

    .slab-tabs-bar { display: flex; gap: 8px; overflow-x: auto; border-bottom: 2px solid #e2e8f0; margin-bottom: 20px; padding-bottom: 2px; }
    .slab-tab-item { display: flex; align-items: center; gap: 8px; padding: 10px 18px; border-radius: 8px 8px 0 0; background: #f8fafc; border: 1px solid #e2e8f0; border-bottom: none; cursor: pointer; user-select: none; transition: all 0.15s ease; }
    .slab-tab-item:hover { background: #f1f5f9; }
    .slab-tab-item.active { background: #ffffff; border-color: #2563eb; border-bottom: 2px solid #ffffff; margin-bottom: -2px; }
    .slab-tab-title { font-size: 13px; font-weight: 700; color: #334155; }
    .slab-tab-item.active .slab-tab-title { color: #2563eb; }
    .slab-tab-icon.success { color: #16a34a; font-size: 16px; width: 16px; height: 16px; }
    .slab-tab-icon.muted { color: #cbd5e1; font-size: 16px; width: 16px; height: 16px; }

    .active-slab-container { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 18px; margin-bottom: 20px; }
    .slab-config-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; padding-bottom: 14px; border-bottom: 1px solid #f1f5f9; flex-wrap: wrap; }
    .slab-name-group { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 260px; }
    .slab-name-label { font-size: 13px; font-weight: 700; color: #475569; }
    .slab-name-input { font-size: 14px; font-weight: 600; color: #0f172a; padding: 6px 12px; border: 1px solid #cbd5e1; border-radius: 6px; width: 240px; }
    .slab-name-input:focus { border-color: #2563eb; outline: none; }

    .cq-table-wrap { overflow-x: auto; margin-bottom: 16px; }
    .cq-table { width: 100%; border-collapse: collapse; min-width: 800px; }
    .cq-th { background: #f8fafc; border: 1px solid #cbd5e1; padding: 8px 10px; font-size: 11px; font-weight: 700; text-transform: uppercase; text-align: center; color: #475569; }
    .cq-zone-th { width: 180px; text-align: left; }
    .cq-td { border: 1px solid #e2e8f0; padding: 4px 6px; font-size: 13px; text-align: center; }
    .cq-zone-td { text-align: left; font-weight: 600; color: #1e293b; background: #f8fafc; }
    .cq-input { width: 100%; text-align: center; border: 1px solid #e2e8f0; border-radius: 4px; padding: 4px 6px; font-size: 13px; font-weight: 500; }
    .cq-input:focus { border-color: #2563eb; outline: none; background: #eff6ff; }
    .cq-footer-actions { display: flex; gap: 12px; align-items: center; margin-top: 20px; }
  `]
})
export class OnboardQuotationFormComponent implements OnInit {
  quotationId: string | null = null;
  quotationNumber: string | null = null;
  currentStatus: string | null = null;

  headerForm: FormGroup;
  bankAccountsList: any[] = [];
  s1Zones: ZoneConfiguration[] = [];
  s2Zones: ZoneConfiguration[] = [];
  s3Zones: ZoneConfiguration[] = [];

  slabs: OnboardSlab[] = [];
  activeSlabIndex = 0;

  saving = false;
  rateMapCache: { [slabIndex: number]: { [key: string]: string } } = {};

  get activeSlab(): OnboardSlab | null {
    return this.slabs[this.activeSlabIndex] || null;
  }

  constructor(
    private fb: FormBuilder,
    private onboardService: OnboardQuotationService,
    private zoneConfigService: ZoneConfigurationService,
    private apiService: ApiService,
    private toast: ToastService,
    private router: Router,
    private route: ActivatedRoute,
    private location: Location
  ) {
    this.headerForm = this.fb.group({
      quotationNumber: [''],
      customerName: ['', Validators.required],
      branchName: [''],
      effectiveDate: [new Date(), Validators.required],
      validTillDate: [new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), Validators.required],
      fuelChargePercentage: [null],
      fovCharges: [null],
      selectedBankAccountId: [null],
      remarks: ['']
    });
  }

  ngOnInit(): void {
    this.quotationId = this.route.snapshot.paramMap.get('id');
    this.loadBankAccounts();
    this.loadZonesAndData();
  }

  private loadBankAccounts(): void {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (res) => {
        this.bankAccountsList = res?.bankAccounts || [];
      },
      error: () => {}
    });
  }

  private loadZonesAndData(): void {
    this.zoneConfigService.getActiveZones().subscribe({
      next: (zones) => {
        const active = zones || [];
        this.s1Zones = active.filter((z) => z.zoneType === 'EXPRESS_SURFACE');
        this.s2Zones = active.filter((z) => z.zoneType === 'PRIORITY_SAFETY');
        this.s3Zones = active.filter((z) => z.zoneType === 'STANDARD_SLABS');

        if (this.quotationId) {
          this.loadExistingQuotation(this.quotationId);
        } else {
          this.initDefaultSlabs();
        }
      },
      error: () => this.toast.error('Error', 'Failed to load zone configurations')
    });
  }

  private initDefaultSlabs(): void {
    this.slabs = [
      { slabId: '1', slabName: 'Slab A', selected: true, zoneRates: [] },
      { slabId: '2', slabName: 'Slab B', selected: true, zoneRates: [] }
    ];
    this.activeSlabIndex = 0;
    this.initRateMaps();
  }

  private initRateMaps(): void {
    this.rateMapCache = {};
    this.slabs.forEach((slab, idx) => {
      const map: { [key: string]: string } = {};
      (slab.zoneRates || []).forEach((zr) => {
        const id = zr.zoneId;
        if (zr.expressBaseRate != null) map[id + '_E1'] = String(zr.expressBaseRate);
        if (zr.expressIncrementalRate != null) map[id + '_E2'] = String(zr.expressIncrementalRate);
        if (zr.expressPerKgRate != null) map[id + '_E3'] = String(zr.expressPerKgRate);
        if (zr.surfaceSlab1Rate != null) map[id + '_S1'] = String(zr.surfaceSlab1Rate);
        if (zr.surfaceSlab2Rate != null) map[id + '_S2'] = String(zr.surfaceSlab2Rate);

        if (zr.expressBaseRate != null) map[id + '_PC1'] = String(zr.expressBaseRate);
        if (zr.expressIncrementalRate != null) map[id + '_PC2'] = String(zr.expressIncrementalRate);
        if (zr.expressPerKgRate != null) map[id + '_PC3'] = String(zr.expressPerKgRate);
        if (zr.surfaceSlab1Rate != null) map[id + '_SP1'] = String(zr.surfaceSlab1Rate);
        if (zr.surfaceSlab2Rate != null) map[id + '_SP2'] = String(zr.surfaceSlab2Rate);

        if (zr.standardRate1Kg != null) map[id + '_STD_1'] = String(zr.standardRate1Kg);
        if (zr.standardRate2Kg != null) map[id + '_STD_2'] = String(zr.standardRate2Kg);
        if (zr.standardRate3Kg != null) map[id + '_STD_3'] = String(zr.standardRate3Kg);
        if (zr.standardRate4Kg != null) map[id + '_STD_4'] = String(zr.standardRate4Kg);
        if (zr.standardRate5Kg != null) map[id + '_STD_5'] = String(zr.standardRate5Kg);
        if (zr.standardPerKgAbove3 != null) map[id + '_STD_ABOVE3'] = String(zr.standardPerKgAbove3);
      });
      this.rateMapCache[idx] = map;
    });
  }

  private loadExistingQuotation(id: string): void {
    this.onboardService.findById(id).subscribe({
      next: (q) => {
        this.quotationNumber = q.quotationNumber || null;
        this.currentStatus = q.status || null;

        this.headerForm.patchValue({
          quotationNumber: q.quotationNumber,
          customerName: q.customerName,
          branchName: q.branchName,
          effectiveDate: q.effectiveDate ? new Date(q.effectiveDate) : new Date(),
          validTillDate: q.validTillDate ? new Date(q.validTillDate) : new Date(),
          fuelChargePercentage: q.fuelChargePercentage,
          fovCharges: q.fovCharges,
          selectedBankAccountId: q.selectedBankAccountId,
          remarks: q.remarks
        });

        this.slabs = q.slabs && q.slabs.length > 0 ? q.slabs : [
          { slabId: '1', slabName: 'Slab A', selected: true, zoneRates: [] }
        ];
        this.activeSlabIndex = 0;
        this.initRateMaps();
      },
      error: () => this.toast.error('Error', 'Failed to load onboard quotation')
    });
  }

  addSlab(): void {
    const nextChar = String.fromCharCode(65 + this.slabs.length);
    const newSlabName = `Slab ${nextChar}`;
    const newSlab: OnboardSlab = {
      slabId: String(Date.now()),
      slabName: newSlabName,
      selected: true,
      zoneRates: []
    };
    this.slabs.push(newSlab);
    this.activeSlabIndex = this.slabs.length - 1;
    this.rateMapCache[this.activeSlabIndex] = {};
  }

  deleteSlab(index: number): void {
    if (this.slabs.length <= 1) return;
    this.slabs.splice(index, 1);
    this.initRateMaps();
    this.activeSlabIndex = Math.max(0, index - 1);
  }

  getRate(slab: OnboardSlab, key: string): string {
    const idx = this.slabs.indexOf(slab);
    return this.rateMapCache[idx]?.[key] ?? '';
  }

  setRate(slab: OnboardSlab, key: string, event: any): void {
    const val = event.target ? event.target.value : event;
    const idx = this.slabs.indexOf(slab);
    if (!this.rateMapCache[idx]) {
      this.rateMapCache[idx] = {};
    }
    this.rateMapCache[idx][key] = val;
  }

  zoneLabelHtml(name: string): string {
    return name ? name.replace(/\//g, '/<br>') : '';
  }

  save(): void {
    if (this.headerForm.invalid) {
      this.headerForm.markAllAsTouched();
      this.toast.error('Validation Error', 'Please fill in all required fields');
      return;
    }

    this.saving = true;
    const formVal = this.headerForm.value;

    // Build zoneRates for each slab from rateMapCache
    const processedSlabs: OnboardSlab[] = this.slabs.map((slab, idx) => {
      const map = this.rateMapCache[idx] || {};
      const allZoneIds = new Set<string>();
      [...this.s1Zones, ...this.s2Zones, ...this.s3Zones].forEach((z) => {
        if (z.id) allZoneIds.add(z.id);
      });
      Object.keys(map).forEach((k) => {
        const parts = k.split('_');
        if (parts.length >= 2) allZoneIds.add(parts[0]);
      });

      const zoneRates: ZoneRateConfig[] = Array.from(allZoneIds).map((zId) => {
        const zObj = [...this.s1Zones, ...this.s2Zones, ...this.s3Zones].find((z) => z.id === zId);
        return {
          zoneId: zId,
          zoneName: zObj?.zoneName || '',
          expressBaseRate: this.parseNum(map[zId + '_E1'] ?? map[zId + '_PC1']),
          expressIncrementalRate: this.parseNum(map[zId + '_E2'] ?? map[zId + '_PC2']),
          expressPerKgRate: this.parseNum(map[zId + '_E3'] ?? map[zId + '_PC3']),
          surfaceSlab1Rate: this.parseNum(map[zId + '_S1'] ?? map[zId + '_SP1']),
          surfaceSlab2Rate: this.parseNum(map[zId + '_S2'] ?? map[zId + '_SP2']),
          standardRate1Kg: this.parseNum(map[zId + '_STD_1']),
          standardRate2Kg: this.parseNum(map[zId + '_STD_2']),
          standardRate3Kg: this.parseNum(map[zId + '_STD_3']),
          standardRate4Kg: this.parseNum(map[zId + '_STD_4']),
          standardRate5Kg: this.parseNum(map[zId + '_STD_5']),
          standardPerKgAbove3: this.parseNum(map[zId + '_STD_ABOVE3'])
        };
      });

      return {
        slabId: slab.slabId,
        slabName: slab.slabName || `Slab ${idx + 1}`,
        selected: slab.selected !== false,
        zoneRates
      };
    });

    const payload: OnboardQuotation = {
      id: this.quotationId || undefined,
      quotationNumber: formVal.quotationNumber || undefined,
      customerName: formVal.customerName,
      branchName: formVal.branchName,
      effectiveDate: this.formatDateIso(formVal.effectiveDate),
      validTillDate: this.formatDateIso(formVal.validTillDate),
      fuelChargePercentage: formVal.fuelChargePercentage,
      fovCharges: formVal.fovCharges,
      selectedBankAccountId: formVal.selectedBankAccountId,
      remarks: formVal.remarks,
      slabs: processedSlabs
    };

    const request = this.quotationId ?
      this.onboardService.update(this.quotationId, payload) :
      this.onboardService.create(payload);

    request.subscribe({
      next: (res) => {
        this.toast.success('Success', 'Onboard quotation saved successfully');
        this.saving = false;
        this.router.navigate(['/onboard-quotations']);
      },
      error: (err) => {
        this.toast.error('Error', err?.error?.message || 'Failed to save onboard quotation');
        this.saving = false;
      }
    });
  }

  private parseNum(val: any): number | undefined {
    if (val === undefined || val === null || val === '') return undefined;
    const n = parseFloat(val);
    return isNaN(n) ? undefined : n;
  }

  private formatDateIso(d: any): string {
    if (!d) return '';
    const date = new Date(d);
    return date.toISOString().split('T')[0];
  }

  downloadPdf(): void {
    if (!this.quotationId) return;
    this.onboardService.downloadPdf(this.quotationId).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `Onboard-Quotation-${this.quotationNumber || 'Document'}.pdf`;
        a.click();
      },
      error: () => this.toast.error('Error', 'Failed to download PDF')
    });
  }

  downloadExcel(): void {
    if (!this.quotationId) return;
    this.onboardService.exportExcel(this.quotationId).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `Onboard-Quotation-${this.quotationNumber || 'Document'}.xlsx`;
        a.click();
      },
      error: () => this.toast.error('Error', 'Failed to download Excel')
    });
  }

  sendEmail(targetEmail?: string): void {
    if (!this.quotationId) return;
    const email = targetEmail || this.headerForm.get('email')?.value || '';
    if (!email) {
      this.toast.error('Validation Error', 'Please enter recipient email in the form');
      return;
    }
    this.onboardService.sendEmail(this.quotationId, email).subscribe({
      next: () => this.toast.success('Success', 'Quotation email sent successfully'),
      error: (err) => this.toast.error('Error', err?.error?.message || 'Failed to send email')
    });
  }

  goBack(): void {
    this.location.back();
  }

  cancel(): void {
    this.router.navigate(['/onboard-quotations']);
  }
}
