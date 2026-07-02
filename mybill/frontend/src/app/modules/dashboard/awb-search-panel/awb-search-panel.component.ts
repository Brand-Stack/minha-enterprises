import { Component, ElementRef, ViewChild, OnInit } from '@angular/core';
import { gsap } from 'gsap';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { formatLocalDateOnly } from '../../../core/utils/date-only.util';
import { CompanySettingsListsService } from '../../../core/services/company-settings-lists.service';

export interface AwbGlobalHit {
  moduleCode: string;
  moduleLabel: string;
  awbNo: string;
  receiverName?: string;
  customerOrConsignor?: string;
  entryDate?: string | null;
  courierType?: string;
  status?: string;
  amount?: number | null;
  path: string;
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-awb-search-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSelectModule
  ],
  template: `
    <section class="awb-shell" @panelIn>
      <div class="awb-head">
        <div>
          <h2 class="awb-title">AWB search</h2>
          <p class="awb-sub">Search by AWB number, date range, courier type, or any combination across all entry modules</p>
        </div>
      </div>

      <div class="awb-search-bar" [class.awb-search-bar--shake]="shakeError" [class.awb-search-bar--focus]="searchFocused">
        <mat-form-field appearance="outline" class="awb-field awb-field--awb" subscriptSizing="dynamic">
          <mat-label>AWB Number</mat-label>
          <mat-icon matPrefix class="awb-prefix-icon">search</mat-icon>
          <input
            #searchInput
            matInput
            [(ngModel)]="awbQuery"
            (keyup.enter)="searchAwb()"
            (focus)="searchFocused = true"
            (blur)="searchFocused = false"
            placeholder="Enter AWB or tracking number"
            autocomplete="off"
            [disabled]="awbLoading"
          />
        </mat-form-field>
        <mat-form-field appearance="outline" class="awb-field awb-field--date" subscriptSizing="dynamic">
          <mat-label>From Date</mat-label>
          <input matInput [matDatepicker]="fromPicker" [(ngModel)]="fromDate" [disabled]="awbLoading">
          <mat-datepicker-toggle matSuffix [for]="fromPicker"></mat-datepicker-toggle>
          <mat-datepicker #fromPicker></mat-datepicker>
        </mat-form-field>
        <mat-form-field appearance="outline" class="awb-field awb-field--date" subscriptSizing="dynamic">
          <mat-label>To Date</mat-label>
          <input matInput [matDatepicker]="toPicker" [(ngModel)]="toDate" [disabled]="awbLoading">
          <mat-datepicker-toggle matSuffix [for]="toPicker"></mat-datepicker-toggle>
          <mat-datepicker #toPicker></mat-datepicker>
        </mat-form-field>
        <mat-form-field appearance="outline" class="awb-field awb-field--courier" subscriptSizing="dynamic">
          <mat-label>Courier Type</mat-label>
          <mat-select [(ngModel)]="courierType" [disabled]="awbLoading">
            <mat-option [value]="null">All couriers</mat-option>
            <mat-option *ngFor="let c of courierOptions" [value]="c">{{ c }}</mat-option>
          </mat-select>
        </mat-form-field>
        <div class="awb-actions">
          <button
            type="button"
            mat-flat-button
            class="awb-search-btn js-awb-search-btn"
            (click)="searchAwb()"
            [disabled]="awbLoading"
          >
            <ng-container *ngIf="!awbLoading; else loadingBtn">
              <mat-icon>travel_explore</mat-icon>
              Search
            </ng-container>
            <ng-template #loadingBtn>
              <mat-spinner diameter="22" class="awb-btn-spinner"></mat-spinner>
              Searching
            </ng-template>
          </button>
          <button type="button" mat-stroked-button class="awb-reset-btn" (click)="resetSearch()" [disabled]="awbLoading">
            Reset
          </button>
        </div>
      </div>

      <div *ngIf="awbError" class="awb-error" @errorIn>{{ awbError }}</div>

      <div *ngIf="awbHits.length" class="awb-results" id="awbHitsList">
        <div class="awb-results-head">
          <span class="awb-results-count">{{ awbHits.length }} result{{ awbHits.length === 1 ? '' : 's' }}</span>
        </div>
        <div class="awb-table-wrap">
          <table class="awb-table">
            <thead>
              <tr>
                <th>AWB Number</th>
                <th>Module</th>
                <th>Customer / Client Name</th>
                <th>Courier Type</th>
                <th>Entry Date</th>
              </tr>
            </thead>
            <tbody>
              <tr
                *ngFor="let h of awbHits; trackBy: trackHit"
                class="awb-table-row"
                (click)="openHit(h)"
                (keyup.enter)="openHit(h)"
                tabindex="0"
                role="button"
                [matTooltip]="'Open in ' + h.moduleLabel"
              >
                <td class="awb-td-awb">{{ h.awbNo }}</td>
                <td><span class="awb-mod-badge">{{ h.moduleLabel }}</span></td>
                <td>{{ h.customerOrConsignor || '—' }}</td>
                <td>{{ h.courierType || '—' }}</td>
                <td>{{ formatDate(h.entryDate) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .awb-shell {
      margin-bottom: 0;
      padding: 28px 28px 30px;
      border-radius: 20px;
      background: linear-gradient(135deg, rgba(255,255,255,0.72) 0%, rgba(248,250,252,0.88) 40%, rgba(238,242,255,0.75) 100%);
      border: 1px solid rgba(148, 163, 184, 0.28);
      box-shadow: 0 8px 40px rgba(15, 23, 42, 0.08);
      backdrop-filter: blur(14px);
      -webkit-backdrop-filter: blur(14px);
    }
    .awb-head { margin-bottom: 20px; }
    .awb-title {
      margin: 0 0 6px;
      font-size: 1.35rem;
      font-weight: 700;
      letter-spacing: -0.02em;
      color: #0f172a;
    }
    .awb-sub {
      margin: 0;
      font-size: 0.9rem;
      color: #64748b;
      line-height: 1.45;
    }
    .awb-search-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: flex-end;
      padding: 4px;
      border-radius: 18px;
      transition: box-shadow 0.25s ease, border-color 0.25s ease;
    }
    .awb-search-bar--focus {
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.22);
      border-radius: 18px;
    }
    .awb-search-bar--shake { animation: awbShake 0.42s ease; }
    @keyframes awbShake {
      0%, 100% { transform: translateX(0); }
      20% { transform: translateX(-6px); }
      40% { transform: translateX(6px); }
      60% { transform: translateX(-4px); }
      80% { transform: translateX(4px); }
    }
    .awb-field {
      flex: 1 1 160px;
      min-width: 140px;
      margin-bottom: 0 !important;
    }
    .awb-field--awb { flex: 2 1 220px; min-width: 200px; }
    .awb-field--date { flex: 1 1 150px; max-width: 180px; }
    .awb-field--courier { flex: 1 1 160px; min-width: 150px; max-width: 200px; }
    .awb-field ::ng-deep .mat-mdc-text-field-wrapper {
      background: rgba(255,255,255,0.92);
      border-radius: 16px !important;
      box-shadow: 0 6px 22px rgba(15, 23, 42, 0.08);
    }
    .awb-field ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    .awb-prefix-icon {
      color: #6366f1;
      margin-right: 4px;
      align-self: center;
    }
    .awb-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      flex: 0 0 auto;
    }
    .awb-search-btn {
      min-height: 48px;
      padding: 0 24px !important;
      border-radius: 16px !important;
      font-weight: 600 !important;
      letter-spacing: 0.02em;
      color: #fff !important;
      background: linear-gradient(135deg, #4f46e5 0%, #6366f1 40%, #7c3aed 100%) !important;
      box-shadow: 0 8px 22px rgba(79, 70, 229, 0.35);
      transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s ease;
    }
    .awb-search-btn:hover:not([disabled]) {
      transform: translateY(-1px);
      box-shadow: 0 12px 28px rgba(79, 70, 229, 0.42);
    }
    .awb-search-btn mat-icon { margin-right: 6px; vertical-align: middle; }
    .awb-reset-btn {
      min-height: 48px;
      border-radius: 16px !important;
      font-weight: 600 !important;
    }
    .awb-btn-spinner { display: inline-block; margin-right: 10px; vertical-align: middle; }
    .awb-search-btn ::ng-deep circle { stroke: #fff !important; }

    .awb-error {
      margin-top: 14px;
      padding: 12px 16px;
      border-radius: 12px;
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
      font-weight: 600;
      font-size: 0.9rem;
    }

    .awb-results { margin-top: 20px; }
    .awb-results-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
    }
    .awb-results-count {
      font-size: 0.85rem;
      font-weight: 600;
      color: #64748b;
    }
    .awb-table-wrap {
      overflow-x: auto;
      border-radius: 14px;
      border: 1px solid #e2e8f0;
      background: rgba(255,255,255,0.96);
      box-shadow: 0 8px 28px rgba(15, 23, 42, 0.06);
    }
    .awb-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9rem;
    }
    .awb-table th {
      text-align: left;
      padding: 12px 16px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
    }
    .awb-table td {
      padding: 12px 16px;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
      vertical-align: middle;
    }
    .awb-table-row {
      cursor: pointer;
      transition: background 0.15s ease;
    }
    .awb-table-row:hover { background: #f8fafc; }
    .awb-table-row:focus-visible {
      outline: 2px solid #6366f1;
      outline-offset: -2px;
    }
    .awb-table tbody tr:last-child td { border-bottom: none; }
    .awb-td-awb {
      font-weight: 700;
      color: #0f172a;
      font-family: ui-monospace, monospace;
    }
    .awb-mod-badge {
      display: inline-flex;
      font-size: 0.68rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      padding: 5px 10px;
      border-radius: 999px;
      background: linear-gradient(135deg, #eef2ff, #e0e7ff);
      color: #4338ca;
      border: 1px solid rgba(99, 102, 241, 0.25);
      white-space: nowrap;
    }

    @media (max-width: 640px) {
      .awb-field--date { max-width: none; flex: 1 1 100%; }
      .awb-actions { width: 100%; }
      .awb-search-btn, .awb-reset-btn { flex: 1; }
    }
  `],
  animations: [
    trigger('panelIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(8px)' }),
        animate('400ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'none' }))
      ])
    ]),
    trigger('errorIn', [
      transition(':enter', [
        style({ opacity: 0 }),
        animate('280ms ease', style({ opacity: 1 }))
      ])
    ])
  ]
})
export class AwbSearchPanelComponent implements OnInit {
  @ViewChild('searchInput') private searchInput?: ElementRef<HTMLInputElement>;

  awbQuery = '';
  fromDate: Date | null = null;
  toDate: Date | null = null;
  courierType: string | null = null;
  courierOptions: string[] = [];
  awbLoading = false;
  awbError: string | null = null;
  awbHits: AwbGlobalHit[] = [];
  shakeError = false;
  searchFocused = false;
  private readonly reduceMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor(
    private apiService: ApiService,
    private toastService: ToastService,
    private router: Router,
    private companyLists: CompanySettingsListsService
  ) {}

  ngOnInit(): void {
    this.companyLists.getLists().subscribe((lists) => {
      this.courierOptions = lists.couriers || [];
    });
  }

  focusSearch() {
    this.searchInput?.nativeElement?.focus();
  }

  searchAwb() {
    const q = (this.awbQuery || '').trim();
    const hasAwb = q.length > 0;
    const hasFrom = this.fromDate != null;
    const hasTo = this.toDate != null;
    const hasCourier = !!(this.courierType || '').trim();

    if (!hasAwb && !hasFrom && !hasTo && !hasCourier) {
      this.toastService.warning('AWB Search', 'Enter an AWB number, date range, and/or courier type');
      return;
    }
    if (hasFrom && hasTo && this.fromDate! > this.toDate!) {
      this.toastService.warning('AWB Search', 'From Date cannot be after To Date');
      return;
    }

    const params: Record<string, string> = {};
    if (hasAwb) params['q'] = q;
    const fromStr = formatLocalDateOnly(this.fromDate);
    const toStr = formatLocalDateOnly(this.toDate);
    if (fromStr) params['fromDate'] = fromStr;
    if (toStr) params['toDate'] = toStr;
    const courier = (this.courierType || '').trim();
    if (courier) params['courierType'] = courier;

    this.awbLoading = true;
    this.awbError = null;
    this.awbHits = [];
    this.shakeError = false;

    this.apiService.get<AwbGlobalHit[]>('/dashboard/awb-search', params).subscribe({
      next: (rows) => {
        this.awbLoading = false;
        const list = rows || [];
        if (!list.length) {
          this.triggerErrorShake('No AWBs found for the selected criteria');
          return;
        }
        this.awbHits = list;
        setTimeout(() => this.playHitsMotion(), 0);
      },
      error: () => {
        this.awbLoading = false;
        this.triggerErrorShake('Could not search AWBs');
      }
    });
  }

  resetSearch() {
    this.awbQuery = '';
    this.fromDate = null;
    this.toDate = null;
    this.courierType = null;
    this.awbError = null;
    this.awbHits = [];
    this.shakeError = false;
  }

  trackHit(_i: number, h: AwbGlobalHit): string {
    return h.moduleCode + '|' + h.awbNo + '|' + (h.path || '') + '|' + (h.entryDate || '');
  }

  openHit(h: AwbGlobalHit): void {
    let url = h.path.startsWith('/') ? h.path : '/' + h.path;
    const qp = h.queryParams;
    if (qp && Object.keys(qp).length) {
      const qs = new URLSearchParams(qp as Record<string, string>).toString();
      url += (url.includes('?') ? '&' : '?') + qs;
    }
    this.router.navigateByUrl(url);
  }

  private triggerErrorShake(message: string) {
    this.awbError = message;
    this.shakeError = true;
    setTimeout(() => (this.shakeError = false), 450);
  }

  formatDate(d: string | number[] | null | undefined): string {
    if (d == null) return '—';
    if (Array.isArray(d) && d.length >= 3) {
      const dt = new Date(d[0], d[1] - 1, d[2]);
      return dt.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    }
    const s = String(d);
    const t = Date.parse(s);
    if (Number.isNaN(t)) return s;
    return new Date(t).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  private playHitsMotion(): void {
    if (this.reduceMotion) {
      return;
    }
    const el = document.getElementById('awbHitsList');
    if (!el) {
      return;
    }
    gsap.fromTo(
      el.querySelectorAll('.awb-table-row'),
      { opacity: 0, y: 10 },
      { opacity: 1, y: 0, duration: 0.32, stagger: 0.04, ease: 'power2.out' }
    );
  }
}
