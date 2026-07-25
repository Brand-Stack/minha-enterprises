import { Component, OnInit, HostListener } from '@angular/core';
import { Router } from '@angular/router';
import { trigger, transition, style, animate } from '@angular/animations';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { PermissionService } from '../../../core/services/permission.service';

@Component({
  selector: 'app-layout',
  template: `
    <div class="layout-container">
      <!-- Mobile Header -->
      <header class="mobile-header" *ngIf="isMobile">
        <button mat-icon-button (click)="toggleSidebar()">
          <mat-icon>menu</mat-icon>
        </button>
        <h1 class="mobile-title">{{ companyName || 'mybuddy' }}</h1>
      </header>

      <!-- Overlay for mobile -->
      <div class="sidebar-overlay" 
           *ngIf="isMobile && sidebarOpen" 
           (click)="closeSidebar()"></div>

      <!-- Sidebar -->
      <aside class="sidebar" [class.open]="sidebarOpen || !isMobile" [@slideIn]>
        <!-- Sidebar Header -->
        <div class="sidebar-header">
          <div class="user-profile">
            <div class="user-avatar">
              <span>{{ getUserInitials() }}</span>
            </div>
            <div class="user-info">
              <div class="user-id">ID: {{ currentUser?.employeeId || 'N/A' }}</div>
              <div class="user-email">{{ currentUser?.email || 'User' }}</div>
            </div>
          </div>
        </div>

        <!-- Primary Action Button -->
        <!--<div class="sidebar-primary-action" *ngIf="hasAccess('BILLING')">
          <button mat-raised-button class="btn-primary-action" (click)="navigateToCreate()">
            <mat-icon>add</mat-icon>
            <span>Create Transaction</span>
          </button>
        </div> -->

        <!-- Navigation Sections -->
        <nav class="sidebar-nav">
          <!-- GENERAL Section -->
          <div class="nav-section">
            <div class="section-header">GENERAL</div>
            <a routerLink="/dashboard" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>dashboard</mat-icon>
              <span>Dashboard</span>
            </a>
            <a *ngIf="hasAccess('CLIENTS')" 
               routerLink="/clients" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>people</mat-icon>
              <span>Clients</span>
            </a>
            <a *ngIf="hasAccess('SMALL_CLIENTS')" 
               routerLink="/small-clients" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>storefront</mat-icon>
              <span>Small Clients</span>
            </a>
            <a *ngIf="hasAccess('COLLECTION_CUSTOMER')" 
               routerLink="/collection-customer" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>groups</mat-icon>
              <span>Collection Customer</span>
            </a>
          </div>

          <!-- MASTER Section -->
          <div class="nav-section">
            <div class="section-header">MASTER</div>
            <a *ngIf="hasAccess('MASTER_DATA')" 
               routerLink="/master/master-data" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>category</mat-icon>
              <span>Create Category</span>
            </a>
            <a *ngIf="hasAccess('ITEMS')" 
               routerLink="/master/items" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>inventory_2</mat-icon>
              <span>Items</span>
            </a>
            <a *ngIf="hasAccess('AWB_CENTER')" 
               routerLink="/master/awb-center" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>confirmation_number</mat-icon>
              <span>AWB Center</span>
            </a>
            <a *ngIf="hasAccess('ZONE_CONFIG')" 
               routerLink="/master/zone-configurations" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>map</mat-icon>
              <span>Zone Configs</span>
            </a>
          </div>

          <!-- QUOTATIONS Section -->
          <div class="nav-section">
            <div class="section-header">QUOTATIONS</div>
            <a *ngIf="hasAccess('COURIER_QUOTATION')" 
               routerLink="/courier-quotations" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>local_shipping</mat-icon>
              <span>Courier Quotations</span>
            </a>
          </div>

          <!-- BILLING Section -->
          <div class="nav-section">
            <div class="section-header">BILLING</div>
            <a *ngIf="hasAccess('CLIENT_ENTRY')" 
               routerLink="/client-entries" 
               [class.active]="isClientEntryNavActive()"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>date_range</mat-icon>
              <span>Client Entry</span>
            </a>
            <a *ngIf="hasAccess('SMALL_CLIENT_ENTRY')" 
               routerLink="/small-client-entries" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>receipt</mat-icon>
              <span>Small Client Entry</span>
            </a>
            <a *ngIf="hasAccess('COLLECTION_CENTER')" 
               routerLink="/client-entries/collection-center" 
               [class.active]="isCollectionCenterNavActive()"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>warehouse</mat-icon>
              <span>Collection Center</span>
            </a>
            <a *ngIf="hasAccess('CASH_BOOKING')" 
               routerLink="/cash-booking" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>payments</mat-icon>
              <span>Cash Booking</span>
            </a>
          </div>

          <!-- ACCOUNTS Section -->
          <div class="nav-section">
            <div class="section-header">ACCOUNTS</div>
            <a *ngIf="hasAccess('ACCOUNTING')" 
               routerLink="/accounting" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{ exact: true }"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>account_balance_wallet</mat-icon>
              <span>Accounting</span>
            </a>
            <a *ngIf="hasAccess('PURCHASE_BILLS')" 
               routerLink="/purchase-expense/purchase-bills" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>shopping_cart</mat-icon>
              <span>Purchase</span>
            </a>
            <a *ngIf="hasAccess('EXPENSES')" 
               routerLink="/purchase-expense/cash-in" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>receipt_long</mat-icon>
              <span>Cash In/Out</span>
            </a>
            <a *ngIf="hasAccess('GST_REPORT')" 
               routerLink="/reports/gst-report" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>assessment</mat-icon>
              <span>GST Report</span>
            </a>
          </div>

          <!-- ACCOUNTING SOLUTIONS Section -->
          <div class="nav-section">
            <div class="section-header">ACCOUNTING SOLUTIONS</div>
            <a *ngIf="hasAccess('EMPLOYEES')" 
               routerLink="/employees" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>badge</mat-icon>
              <span>Employees</span>
            </a>
          </div>

          <!-- REPORTS Section -->
          <div class="nav-section">
            <div class="section-header">REPORTS</div>
            <a *ngIf="hasAccess('REPORTS')" 
               routerLink="/reports" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>assessment</mat-icon>
              <span>Reports</span>
            </a>
          </div>

          <!-- SETTINGS Section -->
          <div class="nav-section">
            <div class="section-header">SETTINGS</div>
            <a *ngIf="hasAccess('SETTINGS')" 
               routerLink="/settings" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{ exact: true }"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>settings</mat-icon>
              <span>Company Settings</span>
            </a>
            <a *ngIf="isAdmin()" 
               routerLink="/settings/entitlements" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item"
               [@fadeIn]>
              <mat-icon>admin_panel_settings</mat-icon>
              <span>Access Management</span>
            </a>
          </div>

        </nav>

        <!-- Sidebar Footer -->
        <div class="sidebar-footer">
          <a routerLink="/profile/change-password"
             routerLinkActive="active"
             (click)="closeSidebar()"
             class="nav-item change-password-link">
            <mat-icon>lock</mat-icon>
            <span>Change Password</span>
          </a>
          <button mat-button class="nav-item logout-btn" (click)="logout()">
            <mat-icon>logout</mat-icon>
            <span>Logout</span>
          </button>
        </div>
      </aside>

      <!-- Main Content Area -->
      <main class="main-content" [class.sidebar-open]="!isMobile">
        <!-- Top Bar -->
        <header class="topbar" *ngIf="!isMobile">
          <div class="topbar-content">
            <h1 class="page-title">{{ companyName || 'mybuddy' }}</h1>
          </div>
        </header>

        <!-- Page Content -->
        <div class="content-wrapper">
          <router-outlet></router-outlet>
        </div>
      </main>
    </div>

    <!-- Toast Container -->
    <app-toast></app-toast>
  `,
  styles: [`
    .layout-container {
      display: flex;
      min-height: 100vh;
      background: #F9FAFB;
    }

    /* Mobile Header */
    .mobile-header {
      display: none;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: 56px;
      background: #1A1D2E;
      color: white;
      z-index: 1001;
      align-items: center;
      padding: 0 8px;
      gap: 8px;
    }

    .mobile-title {
      font-size: 18px;
      font-weight: 600;
      margin: 0;
    }

    /* Sidebar Overlay */
    .sidebar-overlay {
      display: none;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 999;
    }

    /* Sidebar */
    .sidebar {
      width: 260px;
      background: #1A1D2E;
      padding: 12px;
      display: flex;
      flex-direction: column;
      position: fixed;
      height: 100vh;
      overflow-y: auto;
      z-index: 1000;
      transition: transform 0.3s ease;
    }

    .sidebar-header {
      padding-bottom: 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      margin-bottom: 16px;
    }

    .user-profile {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .user-avatar {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      background: #5B6FE8;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-weight: 600;
      font-size: 14px;
      flex-shrink: 0;
    }

    .user-info {
      flex: 1;
      min-width: 0;
    }

    .user-id {
      font-size: 11px;
      color: #6B7280;
      text-transform: uppercase;
      font-weight: 600;
      margin-bottom: 2px;
    }

    .user-email {
      font-size: 14px;
      color: white;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .sidebar-primary-action {
      margin-bottom: 24px;
    }

    .btn-primary-action {
      width: 100%;
      height: 44px;
      background: #5B6FE8 !important;
      color: white !important;
      border-radius: 8px !important;
      font-weight: 600 !important;
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      gap: 8px;
      transition: all 0.2s ease;

      &:hover {
        background: #3B4FC8 !important;
      }

      &:active {
        transform: scale(0.98);
      }

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }
    }

    .sidebar-nav {
      flex: 1;
      overflow-y: auto;
    }

    .nav-section {
      margin-bottom: 8px;
    }


    .section-header {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      color: #6B7280;
      padding: 12px 16px 8px;
      letter-spacing: 0.05em;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      height: 44px;
      border-radius: 8px;
      color: rgba(255, 255, 255, 0.8);
      text-decoration: none;
      transition: all 0.2s ease;
      cursor: pointer;
      font-size: 14px;
      font-weight: 500;

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: rgba(255, 255, 255, 0.7);
      }

      &:hover {
        background: rgba(255, 255, 255, 0.05);
        color: white;
      }

      &.active {
        background: #5B6FE8 !important;
        color: white !important;

        mat-icon {
          color: white !important;
        }
      }
    }

    .sidebar-footer {
      padding-top: 16px;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
      margin-top: auto;
    }

    .logout-btn {
      width: 100%;
      color: rgba(255, 255, 255, 0.7) !important;

      &:hover {
        background: rgba(255, 255, 255, 0.05);
        color: white !important;
      }
    }

    .main-content {
      flex: 1;
      margin-left: 260px;
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }

    .topbar {
      background: white;
      border-bottom: 1px solid #E5E7EB;
      padding: 16px 24px;
      position: sticky;
      top: 0;
      z-index: 100;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .topbar-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      max-width: 1440px;
      margin: 0 auto;
    }

    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: #1A1D2E;
      margin: 0;
    }

    .content-wrapper {
      flex: 1;
      padding: 24px;
      max-width: 1440px;
      margin: 0 auto;
      width: 100%;
    }

    .sidebar::-webkit-scrollbar {
      width: 6px;
    }

    .sidebar::-webkit-scrollbar-track {
      background: transparent;
    }

    .sidebar::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.2);
      border-radius: 3px;
    }

    /* Responsive - Tablet */
    @media (max-width: 1023px) {
      .sidebar {
        transform: translateX(-100%);
      }

      .sidebar.open {
        transform: translateX(0);
      }

      .main-content {
        margin-left: 0;
      }

      .mobile-header {
        display: flex;
      }

      .sidebar-overlay {
        display: block;
      }

      .content-wrapper {
        padding: 16px;
        padding-top: 72px;
      }
    }

    /* Responsive - Mobile */
    @media (max-width: 767px) {
      .sidebar {
        width: 280px;
      }

      .content-wrapper {
        padding: 12px;
        padding-top: 68px;
      }

      .topbar {
        padding: 12px 16px;
      }

      .page-title {
        font-size: 20px;
      }
    }

    /* Reduced motion */
    @media (prefers-reduced-motion: reduce) {
      .sidebar {
        transition: none;
      }
    }
  `],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(4px)' }),
        animate('0.3s ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ]),
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateX(-20px)', opacity: 0 }),
        animate('0.3s ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
      ])
    ])
  ]
})
export class LayoutComponent implements OnInit {
  currentUser: any;
  sidebarOpen = false;
  isMobile = false;
  companyName: string = '';
  /** Master sidebar links: Transaction (/master/billing) and Quotations (/quotations). Set true to show. */
  showTransactionAndQuotationNav = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private apiService: ApiService,
    private permissionService: PermissionService
  ) { }

  ngOnInit() {
    this.currentUser = this.authService.getCurrentUser();
    this.checkScreenSize();
    this.loadCompanyName();
  }

  loadCompanyName() {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        if (settings?.companyName) {
          this.companyName = settings.companyName;
          // Update page title
          document.title = this.companyName;
        }
      },
      error: () => {
        // Keep default name if settings can't be loaded
        this.companyName = '';
      }
    });
  }

  @HostListener('window:resize')
  onResize() {
    this.checkScreenSize();
  }

  private checkScreenSize() {
    this.isMobile = window.innerWidth < 1024;
    if (!this.isMobile) {
      this.sidebarOpen = false;
    }
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  closeSidebar() {
    if (this.isMobile) {
      this.sidebarOpen = false;
    }
  }

  getUserInitials(): string {
    const label = this.currentUser?.email || this.currentUser?.employeeName;
    if (label) {
      return label.charAt(0).toUpperCase();
    }
    return 'U';
  }

  navigateToCreate() {
    this.closeSidebar();
    this.router.navigate(['/master/billing/create']);
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  /**
   * Client Entry vs Collection Center share the same lazy module under `/client-entries` and
   * `/monthly-courier-quotations`; avoid dual `routerLinkActive` highlights.
   */
  isClientEntryNavActive(): boolean {
    const path = (this.router.url || '').split('?')[0];
    if (path.startsWith('/client-entries/collection-center')) {
      return false;
    }
    if (path.startsWith('/monthly-courier-quotations/collection-center')) {
      return false;
    }
    if (path.startsWith('/client-entries')) {
      return true;
    }
    if (path.startsWith('/monthly-courier-quotations')) {
      return true;
    }
    return false;
  }

  isCollectionCenterNavActive(): boolean {
    const path = (this.router.url || '').split('?')[0];
    return (
      path.startsWith('/client-entries/collection-center') ||
      path.startsWith('/monthly-courier-quotations/collection-center')
    );
  }

  /**
   * Sidebar visibility is now driven entirely by entitlements. A menu item is
   * shown when the user has VIEW permission on the corresponding module.
   * ADMIN bypasses all checks inside {@link PermissionService}.
   */
  hasAccess(moduleKey: string): boolean {
    return this.permissionService.hasPermission(moduleKey, 'view');
  }

  isAdmin(): boolean {
    return this.permissionService.isAdmin();
  }
}
