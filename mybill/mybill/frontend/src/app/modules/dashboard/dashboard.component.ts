import { Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { ToastService } from '../../shared/components/toast/toast.service';
import { CompanySettingsListsService } from '../../core/services/company-settings-lists.service';
import { PermissionService } from '../../core/services/permission.service';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

/** Maps an internal dashboard card key to its dashboard entitlement (SHOW/HIDE) permission key. */
const CARD_PERMISSION_KEY: Record<string, string> = {
  'ops-today': 'VIEW_CLIENT_BOOKINGS_RANGE',
  'ops-ship': 'VIEW_SHIPMENTS_ALL_MODULES',
  'ops-del': 'VIEW_DELIVERED',
  'ops-transit': 'VIEW_IN_TRANSIT',
  'ops-pend': 'VIEW_PENDING',
  'rev-total': 'VIEW_CLIENT_REVENUE',
  'rev-cod': 'VIEW_COD_PENDING',
  'cc-tot': 'VIEW_CC_TOTAL_BOOKINGS',
  'cc-cash': 'VIEW_CC_CASH',
  'cc-gpay': 'VIEW_CC_GPAY',
  'cc-pend': 'VIEW_CC_PENDING',
  'cc-cod': 'VIEW_CC_COD',
  'cc-cgo': 'VIEW_CC_TOTAL_AMOUNT',
  'cb-tot': 'VIEW_CB_TOTAL_BOOKINGS',
  'cb-cash': 'VIEW_CB_CASH_AMOUNT',
  'cb-onl': 'VIEW_CB_ONLINE_AMOUNT',
  'cb-pend': 'VIEW_CB_PENDING',
  'cb-cod': 'VIEW_CB_COD',
  'cb-cgo': 'VIEW_CB_TOTAL_AMOUNT',
  'cash-total': 'VIEW_CB_TOTAL_BOOKINGS',
  'cash-rev': 'VIEW_CB_CASH_AMOUNT',
  'cash-del': 'VIEW_CB_TOTAL_BOOKINGS',
  'ce-tot': 'VIEW_CE_TOTAL_BOOKINGS',
  'ce-cash': 'VIEW_CE_CASH',
  'ce-gpay': 'VIEW_CE_GPAY',
  'ce-pend': 'VIEW_CE_PENDING',
  'ce-cod': 'VIEW_CE_COD',
  'ce-rev': 'VIEW_CE_BASE_REVENUE',
  'ce-fuel': 'VIEW_CE_FUEL_CHARGES',
  'ce-gst': 'VIEW_CE_GST_AMOUNT',
  'ce-total-rev': 'VIEW_CE_TOTAL_REVENUE',
  'sce-tot': 'VIEW_SCE_TOTAL_BOOKINGS',
  'sce-cash': 'VIEW_SCE_CASH',
  'sce-gpay': 'VIEW_SCE_GPAY',
  'sce-rev': 'VIEW_SCE_TOTAL_REVENUE',
  'acc-in': 'VIEW_LEDGER_IN',
  'acc-out': 'VIEW_LEDGER_OUT',
  'acc-bal': 'VIEW_LEDGER_NET'
};

Chart.register(...registerables);
gsap.registerPlugin(ScrollTrigger);

interface CourierSummary {
  bookingsToday: number;
  clientBookingsCount?: number;
  shipmentsAllModulesCount?: number;
  delivered: number;
  inTransit: number;
  pending: number;
  cancelled: number;
  failedDeliveries: number;
  totalRevenue: number;
  monthlyRevenue: number;
  codPending: number;
  cashBookingsCount?: number;
  cashBookingRevenue?: number;
  deliveredCashBookings?: number;
  revenueRangeActive?: boolean;
}

interface CourierDashPayload {
  summary: CourierSummary;
  dailyBookingTrend: { date: string; count: number }[];
  monthlyRevenueTrend: { monthKey: string; revenue: number }[];
  statusDistribution: { status: string; count: number }[];
  topClientsByRevenue: { clientName: string; revenue: number }[];
  latestDelivered: any[];
  failedDeliveries: any[];
  collection?: {
    summary: {
      totalEntries: number;
      paidCollections: number;
      pendingCollections: number;
      codCollections: number;
      totalCollectionAmount: number;
    };
    dailyCollectionTrend: { date: string; count: number }[];
    amountStatusDistribution: { amountStatus: string; count: number }[];
    topCollectionCustomers: { customerName: string; amount: number }[];
    monthlyCollectionTrend?: { monthKey: string; count: number }[];
  };
  pendingCollectionAwbs?: number;
  revenueFrom?: string;
  revenueTo?: string;
  accounting?: {
    currentBalance: number;
    totalIn: number;
    totalOut: number;
    monthIn: number;
    monthOut: number;
  };
  cashFlowTrend?: { date: string; netAmount: number }[];
  collectionCenterKpi?: ModuleRangeKpi;
  cashBookingKpi?: ModuleRangeKpi;
  clientEntryKpi?: ModuleRangeKpi;
  clientEntryAwbGroups?: ModuleAwbGroup[];
  collectionCenterAwbGroups?: ModuleAwbGroup[];
  cashBookingAwbGroups?: ModuleAwbGroup[];
  smallClientEntryKpi?: ModuleRangeKpi;
  smallClientEntryAwbGroups?: ModuleAwbGroup[];
}

interface ModuleRangeKpi {
  from?: string;
  to?: string;
  totalBookings: number;
  cashAmount: number;
  gpayAmount: number;
  pendingAmount: number;
  codAmount: number;
  revenueTotal?: number;
  baseRevenue?: number;
  fuelCharges?: number;
  gstAmount?: number;
  fovAmount?: number;
}

interface ModuleAwbGroup {
  name: string;
  totalCount: number;
}

export interface DashStatCard {
  key: string;
  label: string;
  icon: string;
  display: string;
  intTarget?: number;
  moneyTarget?: number;
  trend?: string;
}

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  animations: []
})
export class DashboardComponent implements OnInit, OnDestroy {
  payload: CourierDashPayload | null = null;
  revenueCards: DashStatCard[] = [];
  collectionCards: DashStatCard[] = [];
  cashCards: DashStatCard[] = [];
  clientEntryCards: DashStatCard[] = [];
  smallClientEntryCards: DashStatCard[] = [];
  accountingCards: DashStatCard[] = [];

  loadingDash = false;
  readonly skeletonPlaceholders = Array.from({ length: 8 });

  revenuePreset: 'none' | 'all' | 'yesterday' | 'today' | 'week' | 'month' | 'last30' | 'custom' = 'none';
  customRevenueFrom: Date | null = null;
  customRevenueTo: Date | null = null;
  revenueCourier = '';
  revenueStatus = '';
  courierOptions: string[] = [];
  statusOptions: string[] = [];
  dashboardLoaded = false;

  @ViewChild('dashRoot', { static: false }) dashRoot?: ElementRef<HTMLElement>;

  private chartDaily?: Chart;
  private chartStatus?: Chart;
  private chartCcDaily?: Chart;
  private chartCcAmt?: Chart;
  private chartCf?: Chart;
  private chartCcMonth?: Chart;

  private readonly reduceMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  private introPlayed = false;
  private filterTween?: gsap.core.Tween;
  private scrollRevealRegistered = false;

  constructor(
    private apiService: ApiService,
    private toastService: ToastService,
    private router: Router,
    private route: ActivatedRoute,
    private companyLists: CompanySettingsListsService,
    private permissionService: PermissionService,
    private cdr: ChangeDetectorRef
  ) {}

  /** True if the given dashboard card key is visible for the current user. ADMIN sees all. */
  hasCard(cardKey: string): boolean {
    return this.permissionService.hasCard('DASHBOARD', cardKey);
  }

  /** Keep only the cards the current user is entitled to see. */
  private filterCardsByPermission(cards: DashStatCard[]): DashStatCard[] {
    return cards.filter((c) => {
      const permKey = CARD_PERMISSION_KEY[c.key];
      return permKey ? this.permissionService.hasCard('DASHBOARD', permKey) : true;
    });
  }

  ngOnInit(): void {
    const t = new Date();
    t.setHours(0, 0, 0, 0);
    this.customRevenueFrom = new Date(t);
    this.customRevenueTo = new Date(t);
    this.companyLists.getLists().subscribe((lists) => {
      this.courierOptions = lists.couriers || [];
      this.statusOptions = lists.statuses || [];
    });
    this.route.queryParams.subscribe((q) => {
      if (q['revCourier']) this.revenueCourier = q['revCourier'];
      if (q['revStatus']) this.revenueStatus = q['revStatus'];
      if (q['revFrom']) this.customRevenueFrom = new Date(q['revFrom']);
      if (q['revTo']) this.customRevenueTo = new Date(q['revTo']);
      if (q['revPreset']) this.revenuePreset = q['revPreset'] as typeof this.revenuePreset;
      if (q['revPreset'] || q['revFrom'] || q['revTo'] || q['revCourier'] || q['revStatus']) {
        this.loadCourier();
      }
    });
  }

  ngOnDestroy(): void {
    this.filterTween?.kill();
    ScrollTrigger.getAll().forEach((t) => t.kill());
    this.destroyCharts();
  }

  setRevenuePreset(p: 'none' | 'all' | 'yesterday' | 'today' | 'week' | 'month' | 'last30' | 'custom'): void {
    this.revenuePreset = p;
    if (p !== 'custom' && p !== 'none') {
      this.loadCourier();
    }
  }

  applyRevenueFilters(): void {
    const q: Record<string, string | null> = {};
    const { from, to } = this.resolveRevenueRange();
    if (from) q['revFrom'] = from;
    if (to) q['revTo'] = to;
    if (this.revenueCourier.trim()) q['revCourier'] = this.revenueCourier.trim();
    else q['revCourier'] = null;
    if (this.revenueStatus.trim()) q['revStatus'] = this.revenueStatus.trim();
    else q['revStatus'] = null;
    q['revPreset'] = this.revenuePreset;
    this.router.navigate([], { relativeTo: this.route, queryParams: q, queryParamsHandling: 'merge' });
    this.loadCourier();
  }

  clearRevenueFilters(): void {
    this.revenuePreset = 'none';
    this.revenueCourier = '';
    this.revenueStatus = '';
    const t = new Date();
    t.setHours(0, 0, 0, 0);
    this.customRevenueFrom = new Date(t);
    this.customRevenueTo = new Date(t);
    this.dashboardLoaded = false;
    this.payload = null;
    this.destroyCharts();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { revFrom: null, revTo: null, revCourier: null, revStatus: null, revPreset: null },
      queryParamsHandling: 'merge'
    });
    this.cdr.markForCheck();
  }

  private revenueQueryParams(): Record<string, string> {
    const p: Record<string, string> = {};
    if (this.revenuePreset === 'all') {
      p['revenueAll'] = 'true';
    } else if (this.revenuePreset === 'custom') {
      const from = this.isoDate(this.customRevenueFrom);
      const to = this.isoDate(this.customRevenueTo);
      if (from) p['revenueFrom'] = from;
      if (to) p['revenueTo'] = to;
    } else if (this.revenuePreset !== 'none') {
      const { from, to } = this.resolveRevenueRange();
      if (from) p['revenueFrom'] = from;
      if (to) p['revenueTo'] = to;
    }
    if (this.revenueCourier.trim()) p['clientEntryCourier'] = this.revenueCourier.trim();
    if (this.revenueStatus.trim()) p['clientEntryStatus'] = this.revenueStatus.trim();

    const hasRange = !!(p['revenueFrom'] || p['revenueTo'] || p['revenueAll']);
    const hasDimension = !!(p['clientEntryCourier'] || p['clientEntryStatus']);
    if (!hasRange && !hasDimension) {
      p['revenueAll'] = 'true';
    }
    return p;
  }

  private isoDate(d: Date | null): string | undefined {
    if (!d) return undefined;
    const x = new Date(d);
    return (
      x.getFullYear() +
      '-' +
      String(x.getMonth() + 1).padStart(2, '0') +
      '-' +
      String(x.getDate()).padStart(2, '0')
    );
  }

  private resolveRevenueRange(): { from?: string; to?: string } {
    const iso = (d: Date) => {
      const x = new Date(d);
      return (
        x.getFullYear() +
        '-' +
        String(x.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(x.getDate()).padStart(2, '0')
      );
    };
    const today = new Date();
    const d0 = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    switch (this.revenuePreset) {
      case 'yesterday': {
        const y = new Date(d0);
        y.setDate(y.getDate() - 1);
        return { from: iso(y), to: iso(y) };
      }
      case 'today':
        return { from: iso(d0), to: iso(d0) };
      case 'week': {
        const d = new Date(d0);
        const day = d.getDay();
        const diffToMonday = day === 0 ? -6 : 1 - day;
        const w = new Date(d);
        w.setDate(w.getDate() + diffToMonday);
        return { from: iso(w), to: iso(d0) };
      }
      case 'month': {
        const m = new Date(d0.getFullYear(), d0.getMonth(), 1);
        return { from: iso(m), to: iso(d0) };
      }
      case 'last30': {
        const start = new Date(d0);
        start.setDate(start.getDate() - 29);
        return { from: iso(start), to: iso(d0) };
      }
      case 'custom': {
        const from = this.customRevenueFrom ? iso(this.customRevenueFrom) : undefined;
        const to = this.customRevenueTo ? iso(this.customRevenueTo) : undefined;
        return { from, to };
      }
      case 'none':
        return {};
      default:
        return {};
    }
  }

  loadCourier(): void {
    this.loadingDash = true;
    this.cdr.markForCheck();
    if (!this.reduceMotion) {
      this.filterTween?.kill();
      this.filterTween = gsap.to('.js-dash-metric', {
        opacity: 0.35,
        duration: 0.18,
        ease: 'power2.out',
        overwrite: 'auto'
      });
    }
    this.apiService.get<CourierDashPayload>('/dashboard/courier', this.revenueQueryParams()).subscribe({
      next: (data) => {
        this.payload = data;
        this.dashboardLoaded = true;
        this.buildCardModels(data);
        this.loadingDash = false;
        this.cdr.detectChanges();
        setTimeout(() => {
          this.renderCharts();
          if (!this.reduceMotion) {
            gsap.to('.js-dash-metric', { opacity: 1, duration: 0.25, ease: 'power2.out', overwrite: 'auto' });
            this.runCounterAnimations();
            this.playIntroIfNeeded();
            this.ensureScrollReveals();
            ScrollTrigger.refresh();
          }
        }, 0);
      },
      error: () => {
        this.loadingDash = false;
        this.toastService.error('Error', 'Failed to load courier dashboard');
        this.cdr.markForCheck();
      }
    });
  }

  private buildCardModels(data: CourierDashPayload): void {
    const s = data.summary;
    const n = (v: unknown) => (typeof v === 'number' ? v : Number(v || 0)).toFixed(2);
    const rangeOn = !!s.revenueRangeActive;
    const revLabel = rangeOn ? 'Client revenue (range)' : 'Client revenue';
    const clientBk = s.clientBookingsCount ?? s.bookingsToday;
    const shipCt = s.shipmentsAllModulesCount ?? clientBk;

    this.revenueCards = [
      {
        key: 'ops-today',
        label: rangeOn ? 'Client bookings (range)' : 'Client bookings (scope)',
        icon: 'today',
        display: String(clientBk),
        intTarget: clientBk
      },
      {
        key: 'ops-ship',
        label: 'Shipments (all modules)',
        icon: 'local_shipping',
        display: String(shipCt),
        intTarget: shipCt
      },
      {
        key: 'ops-del',
        label: 'Delivered',
        icon: 'check_circle',
        display: String(s.delivered),
        intTarget: s.delivered,
        trend: s.failedDeliveries ? `${s.failedDeliveries} failed` : undefined
      },
      {
        key: 'ops-transit',
        label: 'In transit',
        icon: 'airport_shuttle',
        display: String(s.inTransit),
        intTarget: s.inTransit
      },
      {
        key: 'ops-pend',
        label: 'Pending',
        icon: 'schedule',
        display: String(s.pending),
        intTarget: s.pending,
        trend: s.cancelled ? `${s.cancelled} cancelled` : undefined
      },
      {
        key: 'rev-total',
        label: revLabel,
        icon: 'payments',
        display: '₹' + n(s.totalRevenue),
        moneyTarget: Number(s.totalRevenue)
      },
      {
        key: 'rev-cod',
        label: 'COD pending',
        icon: 'account_balance_wallet',
        display: '₹' + n(s.codPending),
        moneyTarget: Number(s.codPending)
      }
    ];

    const ccKpi = data.collectionCenterKpi;
    if (ccKpi) {
      const pendNote =
        data.pendingCollectionAwbs != null && data.pendingCollectionAwbs > 0
          ? `${data.pendingCollectionAwbs} AWBs pending`
          : undefined;
      this.collectionCards = [
        {
          key: 'cc-tot',
          label: 'Total bookings',
          icon: 'inventory_2',
          display: String(ccKpi.totalBookings),
          intTarget: ccKpi.totalBookings
        },
        {
          key: 'cc-cash',
          label: 'Cash',
          icon: 'payments',
          display: '₹' + n(ccKpi.cashAmount),
          moneyTarget: Number(ccKpi.cashAmount)
        },
        {
          key: 'cc-gpay',
          label: 'GPay',
          icon: 'phone_android',
          display: '₹' + n(ccKpi.gpayAmount),
          moneyTarget: Number(ccKpi.gpayAmount)
        },
        {
          key: 'cc-pend',
          label: 'Pending',
          icon: 'hourglass_empty',
          display: '₹' + n(ccKpi.pendingAmount),
          moneyTarget: Number(ccKpi.pendingAmount),
          trend: pendNote
        },
        {
          key: 'cc-cod',
          label: 'Cash on delivery',
          icon: 'local_atm',
          display: '₹' + n(ccKpi.codAmount),
          moneyTarget: Number(ccKpi.codAmount)
        },
        {
          key: 'cc-cgo',
          label: 'Total Amount (Cash + GPay)',
          icon: 'calculate',
          display: '₹' + n(Number(ccKpi.cashAmount) + Number(ccKpi.gpayAmount)),
          moneyTarget: Number(ccKpi.cashAmount) + Number(ccKpi.gpayAmount)
        }
      ];
    } else {
      this.collectionCards = [];
    }

    const cbKpi = data.cashBookingKpi;
    if (cbKpi) {
      this.cashCards = [
        {
          key: 'cb-tot',
          label: 'Total bookings',
          icon: 'receipt_long',
          display: String(cbKpi.totalBookings),
          intTarget: cbKpi.totalBookings
        },
        {
          key: 'cb-cash',
          label: 'Cash amount',
          icon: 'payments',
          display: '₹' + n(cbKpi.cashAmount),
          moneyTarget: Number(cbKpi.cashAmount)
        },
        {
          key: 'cb-onl',
          label: 'Online amount',
          icon: 'language',
          display: '₹' + n(cbKpi.gpayAmount),
          moneyTarget: Number(cbKpi.gpayAmount)
        },
        {
          key: 'cb-pend',
          label: 'Pending',
          icon: 'pending',
          display: '₹' + n(cbKpi.pendingAmount),
          moneyTarget: Number(cbKpi.pendingAmount)
        },
        {
          key: 'cb-cod',
          label: 'COD',
          icon: 'local_atm',
          display: '₹' + n(cbKpi.codAmount),
          moneyTarget: Number(cbKpi.codAmount)
        },
        {
          key: 'cb-cgo',
          label: 'Total Amount (Cash + Online)',
          icon: 'calculate',
          display: '₹' + n(Number(cbKpi.cashAmount) + Number(cbKpi.gpayAmount)),
          moneyTarget: Number(cbKpi.cashAmount) + Number(cbKpi.gpayAmount)
        }
      ];
    } else {
      const cashTotal = s.cashBookingsCount ?? 0;
      const cashRev = Number(s.cashBookingRevenue ?? 0);
      const cashDel = s.deliveredCashBookings ?? 0;
      this.cashCards = [
        {
          key: 'cash-total',
          label: 'Total cash bookings',
          icon: 'payments',
          display: String(cashTotal),
          intTarget: cashTotal
        },
        {
          key: 'cash-rev',
          label: 'Cash booking revenue',
          icon: 'savings',
          display: '₹' + n(cashRev),
          moneyTarget: cashRev
        },
        {
          key: 'cash-del',
          label: 'Delivered cash bookings',
          icon: 'task_alt',
          display: String(cashDel),
          intTarget: cashDel
        }
      ];
    }

    const ceKpi = data.clientEntryKpi;
    if (ceKpi) {
      this.clientEntryCards = [
        {
          key: 'ce-tot',
          label: 'Total bookings',
          icon: 'description',
          display: String(ceKpi.totalBookings),
          intTarget: ceKpi.totalBookings
        },
        {
          key: 'ce-cash',
          label: 'Cash',
          icon: 'payments',
          display: '₹' + n(ceKpi.cashAmount),
          moneyTarget: Number(ceKpi.cashAmount)
        },
        {
          key: 'ce-gpay',
          label: 'GPay',
          icon: 'phone_android',
          display: '₹' + n(ceKpi.gpayAmount),
          moneyTarget: Number(ceKpi.gpayAmount)
        },
        {
          key: 'ce-pend',
          label: 'Pending',
          icon: 'hourglass_empty',
          display: '₹' + n(ceKpi.pendingAmount),
          moneyTarget: Number(ceKpi.pendingAmount)
        },
        {
          key: 'ce-cod',
          label: 'COD',
          icon: 'local_atm',
          display: '₹' + n(ceKpi.codAmount),
          moneyTarget: Number(ceKpi.codAmount)
        },
        {
          key: 'ce-rev',
          label: 'Base revenue',
          icon: 'account_balance',
          display: '₹' + n(ceKpi.baseRevenue ?? 0),
          moneyTarget: Number(ceKpi.baseRevenue ?? 0)
        },
        {
          key: 'ce-fuel',
          label: 'Fuel charges',
          icon: 'local_gas_station',
          display: '₹' + n(ceKpi.fuelCharges ?? 0),
          moneyTarget: Number(ceKpi.fuelCharges ?? 0)
        },
        {
          key: 'ce-gst',
          label: 'GST amount',
          icon: 'receipt',
          display: '₹' + n(ceKpi.gstAmount ?? 0),
          moneyTarget: Number(ceKpi.gstAmount ?? 0)
        },
        {
          key: 'ce-total-rev',
          label: 'Total revenue',
          icon: 'account_balance',
          display: '₹' + n(ceKpi.revenueTotal ?? 0),
          moneyTarget: Number(ceKpi.revenueTotal ?? 0),
          trend: Number(ceKpi.fovAmount ?? 0) > 0 ? `FOV ₹${n(ceKpi.fovAmount)}` : undefined
        }
      ];
    } else {
      this.clientEntryCards = [];
    }

    const sceKpi = data.smallClientEntryKpi;
    if (sceKpi) {
      this.smallClientEntryCards = [
        { key: 'sce-tot', label: 'Total bookings', icon: 'description', display: String(sceKpi.totalBookings), intTarget: sceKpi.totalBookings },
        { key: 'sce-cash', label: 'Cash', icon: 'payments', display: '₹' + n(sceKpi.cashAmount), moneyTarget: Number(sceKpi.cashAmount) },
        { key: 'sce-gpay', label: 'GPay', icon: 'phone_android', display: '₹' + n(sceKpi.gpayAmount), moneyTarget: Number(sceKpi.gpayAmount) },
        { key: 'sce-rev', label: 'Total revenue', icon: 'account_balance', display: '₹' + n(sceKpi.revenueTotal ?? 0), moneyTarget: Number(sceKpi.revenueTotal ?? 0) }
      ];
    } else {
      this.smallClientEntryCards = [];
    }

    this.accountingCards = [];
    if (data.accounting) {
      const a = data.accounting;
      this.accountingCards = [
        {
          key: 'acc-in',
          label: 'IN amount',
          icon: 'trending_up',
          display: '₹' + n(a.totalIn ?? a.monthIn),
          moneyTarget: Number(a.totalIn ?? a.monthIn)
        },
        {
          key: 'acc-out',
          label: 'OUT amount',
          icon: 'trending_down',
          display: '₹' + n(a.totalOut ?? a.monthOut),
          moneyTarget: Number(a.totalOut ?? a.monthOut)
        },
        {
          key: 'acc-bal',
          label: 'Net amount',
          icon: 'account_balance_wallet',
          display: '₹' + n(a.currentBalance),
          moneyTarget: Number(a.currentBalance)
        }
      ];
    }

    // Apply granular dashboard entitlement (SHOW / HIDE per card).
    this.revenueCards = this.filterCardsByPermission(this.revenueCards);
    this.collectionCards = this.filterCardsByPermission(this.collectionCards);
    this.cashCards = this.filterCardsByPermission(this.cashCards);
    this.clientEntryCards = this.filterCardsByPermission(this.clientEntryCards);
    this.smallClientEntryCards = this.filterCardsByPermission(this.smallClientEntryCards);
    this.accountingCards = this.filterCardsByPermission(this.accountingCards);
  }

  private chartAnimDuration(): number {
    return this.reduceMotion ? 0 : 420;
  }

  private compactChartOptions(legendBottom = false): ChartConfiguration['options'] {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: this.chartAnimDuration(), easing: 'easeOutQuart' },
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: legendBottom, position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } },
        tooltip: {
          backgroundColor: 'rgba(15,23,42,0.92)',
          titleFont: { size: 12, weight: 'bold' },
          bodyFont: { size: 12 },
          padding: 10,
          cornerRadius: 10
        }
      },
      scales: legendBottom
        ? undefined
        : {
            x: { grid: { display: false }, ticks: { maxRotation: 0, font: { size: 10 } } },
            y: { grid: { color: 'rgba(148,163,184,0.2)' }, ticks: { font: { size: 10 } } }
          }
    };
  }

  private renderCharts(): void {
    if (!this.payload) {
      return;
    }
    const dctx = document.getElementById('cdDaily') as HTMLCanvasElement | null;
    const sctx = document.getElementById('cdStatus') as HTMLCanvasElement | null;
    if (dctx) {
      this.chartDaily?.destroy();
      const trend = this.payload.dailyBookingTrend || [];
      this.chartDaily = new Chart(dctx, {
        type: 'line',
        data: {
          labels: trend.map((p) => p.date),
          datasets: [
            {
              label: 'Bookings',
              data: trend.map((p) => p.count),
              borderColor: '#4f46e5',
              tension: 0.35,
              fill: true,
              backgroundColor: 'rgba(79,70,229,0.12)',
              borderWidth: 2,
              pointRadius: 0,
              pointHoverRadius: 4
            }
          ]
        },
        options: this.compactChartOptions(false)
      });
    }
    if (sctx) {
      this.chartStatus?.destroy();
      const dist = this.payload.statusDistribution?.length
        ? this.payload.statusDistribution
        : [{ status: 'N/A', count: 1 }];
      this.chartStatus = new Chart(sctx, {
        type: 'doughnut',
        data: {
          labels: dist.map((x) => x.status),
          datasets: [
            {
              data: dist.map((x) => x.count),
              backgroundColor: ['#22c55e', '#3b82f6', '#eab308', '#ef4444', '#94a3b8', '#a855f7', '#0ea5e9'],
              borderWidth: 0,
              hoverOffset: 6
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: { duration: this.chartAnimDuration(), easing: 'easeOutQuart' },
          cutout: '62%',
          plugins: {
            legend: { position: 'bottom', labels: { boxWidth: 10, padding: 10, font: { size: 11 } } },
            tooltip: {
              backgroundColor: 'rgba(15,23,42,0.92)',
              padding: 10,
              cornerRadius: 10
            }
          }
        }
      });
    }
    const ccol = this.payload.collection;
    if (ccol) {
      const cd = document.getElementById('ccDaily') as HTMLCanvasElement | null;
      const ca = document.getElementById('ccAmt') as HTMLCanvasElement | null;
      if (cd && ccol.dailyCollectionTrend?.length) {
        this.chartCcDaily?.destroy();
        this.chartCcDaily = new Chart(cd, {
          type: 'line',
          data: {
            labels: ccol.dailyCollectionTrend.map((p) => p.date),
            datasets: [
              {
                label: 'Entries',
                data: ccol.dailyCollectionTrend.map((p) => p.count),
                borderColor: '#059669',
                tension: 0.35,
                fill: true,
                backgroundColor: 'rgba(5,150,105,0.12)',
                borderWidth: 2,
                pointRadius: 0
              }
            ]
          },
          options: this.compactChartOptions(false)
        });
      }
      if (ca && ccol.amountStatusDistribution?.length) {
        this.chartCcAmt?.destroy();
        this.chartCcAmt = new Chart(ca, {
          type: 'doughnut',
          data: {
            labels: ccol.amountStatusDistribution.map((x) => x.amountStatus),
            datasets: [
              {
                data: ccol.amountStatusDistribution.map((x) => x.count),
                backgroundColor: ['#10b981', '#f59e0b', '#64748b', '#8b5cf6', '#ec4899'],
                borderWidth: 0,
                hoverOffset: 6
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: { duration: this.chartAnimDuration(), easing: 'easeOutQuart' },
            cutout: '58%',
            plugins: {
              legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 11 } } },
              tooltip: {
                backgroundColor: 'rgba(15,23,42,0.92)',
                padding: 10,
                cornerRadius: 10
              }
            }
          }
        });
      }
      const cm = document.getElementById('ccMonth') as HTMLCanvasElement | null;
      if (cm && ccol.monthlyCollectionTrend?.length) {
        this.chartCcMonth?.destroy();
        this.chartCcMonth = new Chart(cm, {
          type: 'bar',
          data: {
            labels: ccol.monthlyCollectionTrend.map((x) => x.monthKey),
            datasets: [
              {
                label: 'Entries',
                data: ccol.monthlyCollectionTrend.map((x) => x.count),
                backgroundColor: '#059669',
                borderRadius: 8,
                maxBarThickness: 28
              }
            ]
          },
          options: this.compactChartOptions(false)
        });
      }
    }
    const cf = this.payload.cashFlowTrend;
    if (cf?.length) {
      const el = document.getElementById('cfFlow') as HTMLCanvasElement | null;
      if (el) {
        this.chartCf?.destroy();
        this.chartCf = new Chart(el, {
          type: 'line',
          data: {
            labels: cf.map((p) => p.date),
            datasets: [
              {
                label: 'Net (IN − OUT)',
                data: cf.map((p) => p.netAmount),
                borderColor: '#6366f1',
                backgroundColor: 'rgba(99,102,241,0.15)',
                fill: true,
                tension: 0.35,
                borderWidth: 2,
                pointRadius: 0,
                pointHoverRadius: 4
              }
            ]
          },
          options: this.compactChartOptions(false)
        });
      }
    }
  }

  private destroyCharts(): void {
    this.chartDaily?.destroy();
    this.chartStatus?.destroy();
    this.chartCcDaily?.destroy();
    this.chartCcAmt?.destroy();
    this.chartCf?.destroy();
    this.chartCcMonth?.destroy();
  }

  private runCounterAnimations(): void {
    const els = this.dashRoot?.nativeElement?.querySelectorAll<HTMLElement>('.js-dash-metric');
    if (!els?.length) {
      return;
    }
    els.forEach((el) => {
      const intT = el.dataset['intTarget'];
      const moneyT = el.dataset['moneyTarget'];
      if (intT != null && intT !== '') {
        const target = Number(intT);
        const proxy = { v: 0 };
        gsap.to(proxy, {
          v: target,
          duration: this.reduceMotion ? 0 : 0.55,
          ease: 'power2.out',
          onUpdate: () => {
            el.textContent = Math.round(proxy.v).toString();
          }
        });
      } else if (moneyT != null && moneyT !== '') {
        const target = Number(moneyT);
        const proxy = { v: 0 };
        gsap.to(proxy, {
          v: target,
          duration: this.reduceMotion ? 0 : 0.6,
          ease: 'power2.out',
          onUpdate: () => {
            el.textContent =
              '₹' +
              proxy.v.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
          }
        });
      }
    });
  }

  private playIntroIfNeeded(): void {
    if (this.introPlayed || !this.dashRoot) {
      return;
    }
    this.introPlayed = true;
    const root = this.dashRoot.nativeElement;
    const tl = gsap.timeline({ defaults: { ease: 'power3.out' } });
    tl.from(root.querySelectorAll('.js-dash-header'), { opacity: 0, y: -8, duration: 0.35 }, 0)
      .from(root.querySelectorAll('.js-dash-awb'), { opacity: 0, y: -18, duration: 0.45 }, 0.06)
      .from(root.querySelectorAll('.js-dash-filter'), { opacity: 0, y: 12, duration: 0.35 }, 0.12)
      .from(root.querySelectorAll('.dash-card'), { opacity: 0, y: 18, duration: 0.4, stagger: 0.04 }, 0.18);
  }

  /** Register after first payload render so chart/activity nodes exist. */
  private ensureScrollReveals(): void {
    if (this.reduceMotion || this.scrollRevealRegistered || !this.dashRoot) {
      return;
    }
    this.scrollRevealRegistered = true;
    ScrollTrigger.batch('.js-dash-charts', {
      onEnter: (batch) => {
        gsap.from(batch, {
          opacity: 0,
          y: 20,
          duration: 0.45,
          stagger: 0.08,
          ease: 'power2.out',
          overwrite: 'auto'
        });
      },
      once: true,
      start: 'top 90%'
    });
  }
}
