import { Component, OnInit, OnDestroy, HostListener } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';
import { PermissionService } from '../../../core/services/permission.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AppNotification } from '../../../core/models/notification.model';
import { gsap } from 'gsap';

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
        <div class="header-right-actions" style="margin-left: auto; position: relative;">
          <button mat-icon-button class="bell-btn text-white" (click)="toggleNotificationDropdown($event)">
            <mat-icon>notifications</mat-icon>
            <span class="bell-badge" *ngIf="unreadCount > 0">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
        </div>
      </header>

      <!-- Overlay for mobile -->
      <div class="sidebar-overlay" 
           *ngIf="isMobile && sidebarOpen" 
           (click)="closeSidebar()"></div>

      <!-- Sidebar -->
      <aside class="sidebar" [class.open]="sidebarOpen || !isMobile">
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

        <!-- Navigation Sections -->
        <nav class="sidebar-nav" #sidebarNav (scroll)="onSidebarScroll($event)">
          <!-- GENERAL Section -->
          <div class="nav-section">
            <div class="section-header">GENERAL</div>
            <a routerLink="/dashboard" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>dashboard</mat-icon>
              <span>Dashboard</span>
            </a>
            <a *ngIf="hasAccess('CLIENTS')" 
               routerLink="/clients" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>people</mat-icon>
              <span>Clients</span>
            </a>
            <a *ngIf="hasAccess('SMALL_CLIENTS')" 
               routerLink="/small-clients" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>storefront</mat-icon>
              <span>Small Clients</span>
            </a>
            <a *ngIf="hasAccess('COLLECTION_CUSTOMER')" 
               routerLink="/collection-customer" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
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
               class="nav-item">
              <mat-icon>category</mat-icon>
              <span>Create Category</span>
            </a>
            <a *ngIf="hasAccess('ITEMS')" 
               routerLink="/master/items" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>inventory_2</mat-icon>
              <span>Items</span>
            </a>
            <a *ngIf="hasAccess('AWB_CENTER')" 
               routerLink="/master/awb-center" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>confirmation_number</mat-icon>
              <span>AWB Center</span>
            </a>
            <a *ngIf="hasAccess('ZONE_CONFIG')" 
               routerLink="/master/zone-configurations" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
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
               class="nav-item">
              <mat-icon>local_shipping</mat-icon>
              <span>Courier Quotations</span>
            </a>
            <a *ngIf="hasAccess('ONBOARD_QUOTATION')" 
               routerLink="/onboard-quotations" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>assignment</mat-icon>
              <span>Onboard Quotation</span>
            </a>
          </div>

          <!-- BILLING Section -->
          <div class="nav-section">
            <div class="section-header">BILLING</div>
            <a *ngIf="hasAccess('CLIENT_ENTRY')" 
               routerLink="/client-entries" 
               [class.active]="isClientEntryNavActive()"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>date_range</mat-icon>
              <span>Client Entry</span>
            </a>
            <a *ngIf="hasAccess('SMALL_CLIENT_ENTRY')" 
               routerLink="/small-client-entries" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>receipt</mat-icon>
              <span>Small Client Entry</span>
            </a>
            <a *ngIf="hasAccess('COLLECTION_CENTER')" 
               routerLink="/client-entries/collection-center" 
               [class.active]="isCollectionCenterNavActive()"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>warehouse</mat-icon>
              <span>Collection Center</span>
            </a>
            <a *ngIf="hasAccess('CASH_BOOKING')" 
               routerLink="/cash-booking" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
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
               class="nav-item">
              <mat-icon>account_balance_wallet</mat-icon>
              <span>Accounting</span>
            </a>
            <a *ngIf="hasAccess('PURCHASE_BILLS')" 
               routerLink="/purchase-expense/purchase-bills" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>shopping_cart</mat-icon>
              <span>Purchase</span>
            </a>
            <a *ngIf="hasAccess('EXPENSES')" 
               routerLink="/purchase-expense/cash-in" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>receipt_long</mat-icon>
              <span>Cash In/Out</span>
            </a>
            <a *ngIf="hasAccess('GST_REPORT')" 
               routerLink="/reports/gst-report" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: false}"
               (click)="closeSidebar()"
               class="nav-item">
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
               class="nav-item">
              <mat-icon>badge</mat-icon>
              <span>Employees</span>
            </a>
          </div>

          <!-- ATTENDANCE MANAGEMENT Section -->
          <div class="nav-section" *ngIf="hasAnyAttendanceAccess()">
            <div class="section-header">ATTENDANCE MANAGEMENT</div>
            <a *ngIf="hasAccess('MY_ATTENDANCE')" 
               routerLink="/attendance/my-attendance" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>calendar_today</mat-icon>
              <span>My Attendance</span>
            </a>
            <a *ngIf="hasAccess('MASTER_ATTENDANCE')" 
               routerLink="/attendance/daily-list" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>fact_check</mat-icon>
              <span>Master Attendance</span>
            </a>
            <a *ngIf="hasAccess('LEAVES')" 
               routerLink="/attendance/leaves" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>event_note</mat-icon>
              <span>Leaves</span>
            </a>
            <a *ngIf="hasAccess('PERMISSIONS')" 
               routerLink="/attendance/permissions" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>schedule</mat-icon>
              <span>Permissions</span>
            </a>
            <a *ngIf="hasAccess('PAYROLL_PAYSLIPS')" 
               routerLink="/attendance/payroll" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>receipt_long</mat-icon>
              <span>Payroll & Payslips</span>
            </a>
            <a *ngIf="hasAccess('BIOMETRIC_DEVICES')" 
               routerLink="/attendance/devices" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>hardware</mat-icon>
              <span>Biometric Devices</span>
            </a>
            <a *ngIf="hasAccess('ATTENDANCE_CONFIG') || hasAccess('WORKING_HOURS_CONFIG')" 
               routerLink="/attendance/settings" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>punch_clock</mat-icon>
              <span>Attendance Config</span>
            </a>
          </div>

          <!-- REPORTS Section -->
          <div class="nav-section">
            <div class="section-header">REPORTS</div>
            <a *ngIf="hasAccess('REPORTS')" 
               routerLink="/reports" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>assessment</mat-icon>
              <span>Reports</span>
            </a>
          </div>

          <!-- SETTINGS Section -->
          <div class="nav-section">
            <div class="section-header">SETTINGS</div>
            <a *ngIf="isAdmin()" 
               routerLink="/settings/entitlements" 
               routerLinkActive="active"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>admin_panel_settings</mat-icon>
              <span>Access Management</span>
            </a>
            <a *ngIf="hasAccess('SETTINGS')" 
               routerLink="/settings" 
               routerLinkActive="active"
               [routerLinkActiveOptions]="{ exact: true }"
               (click)="closeSidebar()"
               class="nav-item">
              <mat-icon>settings</mat-icon>
              <span>Company Settings</span>
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

            <!-- Global Notification Bell -->
            <div class="notification-wrapper" style="position: relative;">
              <button mat-icon-button class="bell-btn" [class.has-unread]="unreadCount > 0" (click)="toggleNotificationDropdown($event)" matTooltip="Notifications">
                <mat-icon>notifications</mat-icon>
                <span class="bell-badge" *ngIf="unreadCount > 0">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
              </button>

              <!-- Dropdown Popover Panel -->
              <div class="notification-dropdown" *ngIf="notificationDropdownOpen" (click)="$event.stopPropagation()">
                <div class="dropdown-header">
                  <div class="dh-left">
                    <h3>Notifications</h3>
                    <span class="unread-pill" *ngIf="unreadCount > 0">{{ unreadCount }} unread</span>
                  </div>
                  <button mat-button class="btn-text-blue" (click)="markAllAsRead()" [disabled]="unreadCount === 0">Mark all as read</button>
                </div>

                <div class="dropdown-list" *ngIf="recentNotifications.length > 0">
                  <div 
                    *ngFor="let item of recentNotifications" 
                    class="dropdown-item" 
                    [class.unread]="!item.read"
                    (click)="onNotificationClick(item)">
                    
                    <div class="item-icon-box" [class.unread]="!item.read">
                      <mat-icon>{{ getNotificationIcon(item.module, item.type) }}</mat-icon>
                    </div>
                    <div class="item-body">
                      <div class="item-head">
                        <span class="item-title">{{ item.title }}</span>
                        <span class="item-time">{{ formatTime(item.createdAt) }}</span>
                      </div>
                      <p class="item-msg">{{ item.message }}</p>
                    </div>
                  </div>
                </div>

                <div class="dropdown-empty" *ngIf="recentNotifications.length === 0">
                  <mat-icon class="empty-icon">notifications_off</mat-icon>
                  <p>No recent notifications</p>
                </div>

                <div class="dropdown-footer">
                  <a (click)="goToAllNotifications()" class="view-all-link">View All Notifications →</a>
                </div>
              </div>
            </div>
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
      top: 0;
      left: 0;
      bottom: 0;
      height: 100vh;
      overflow: hidden;
      z-index: 1000;
      transition: transform 0.3s ease;
    }

    .sidebar-header {
      padding-bottom: 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
      margin-bottom: 16px;
      flex-shrink: 0;
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
      transition: background 0.2s ease, color 0.2s ease;
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
      flex-shrink: 0;
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
      width: 100%;
      box-sizing: border-box;
    }

    .bell-btn {
      position: relative;
      color: #475569;
      transition: all 0.2s ease;
    }
    .bell-btn.text-white { color: white; }
    .bell-btn.has-unread {
      color: #2563EB;
      background: #EFF6FF;
    }
    .bell-btn:hover { color: #1D4ED8; background: #DBEAFE; }
    .bell-badge {
      position: absolute;
      top: 1px;
      right: 1px;
      background: #EF4444;
      color: white;
      font-size: 10px;
      font-weight: 800;
      padding: 2px 5px;
      border-radius: 10px;
      line-height: 1;
      border: 2px solid white;
      box-shadow: 0 2px 4px rgba(239, 68, 68, 0.4);
    }

    .notification-dropdown {
      position: absolute;
      top: 48px;
      right: 0;
      width: 380px;
      background: white;
      border-radius: 12px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.12), 0 8px 10px -6px rgba(0, 0, 0, 0.08);
      border: 1px solid #E2E8F0;
      z-index: 1000;
      overflow: hidden;
    }

    .dropdown-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 16px;
      border-bottom: 1px solid #F1F5F9;
      background: #F8FAFC;
    }
    .dh-left { display: flex; align-items: center; gap: 8px; }
    .dropdown-header h3 { font-size: 14px; font-weight: 700; color: #0F172A; margin: 0; }
    .unread-pill {
      background: #DBEAFE;
      color: #1E40AF;
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 12px;
    }
    .btn-text-blue { color: #2563EB !important; font-size: 12px !important; font-weight: 600 !important; padding: 0 6px !important; min-width: auto !important; }

    .dropdown-list { max-height: 360px; overflow-y: auto; }
    .dropdown-item {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 16px;
      border-bottom: 1px solid #F1F5F9;
      cursor: pointer;
      transition: background 0.2s;
    }
    .dropdown-item:hover { background-color: #F8FAFC; }
    .dropdown-item.unread { background-color: #EFF6FF; }

    .item-icon-box {
      width: 34px;
      height: 34px;
      border-radius: 8px;
      background: #F1F5F9;
      color: #64748B;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      margin-top: 2px;
    }
    .item-icon-box.unread {
      background: #DBEAFE;
      color: #2563EB;
    }
    .item-icon-box mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .item-body { flex: 1; min-width: 0; }
    .item-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; }
    .item-title { font-size: 13px; font-weight: 700; color: #0F172A; }
    .item-time { font-size: 11px; color: #94A3B8; }
    .item-msg { font-size: 12px; color: #475569; margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .dropdown-empty { text-align: center; padding: 24px 16px; color: #94A3B8; }
    .dropdown-empty .empty-icon { font-size: 32px; width: 32px; height: 32px; margin-bottom: 4px; color: #CBD5E1; }
    .dropdown-empty p { font-size: 13px; margin: 0; }

    .dropdown-footer { padding: 10px; text-align: center; border-top: 1px solid #F1F5F9; background: #F8FAFC; }
    .view-all-link { color: #2563EB; font-size: 12.5px; font-weight: 700; cursor: pointer; text-decoration: none; }
    .view-all-link:hover { text-decoration: underline; }

    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: #1A1D2E;
      margin: 0;
    }

    .content-wrapper {
      flex: 1;
      padding: 24px;
      width: 100%;
      box-sizing: border-box;
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
  `]
})
export class LayoutComponent implements OnInit, OnDestroy {
  currentUser: any;
  sidebarOpen = false;
  isMobile = false;
  companyName: string = '';
  showTransactionAndQuotationNav = false;

  unreadCount = 0;
  notificationDropdownOpen = false;
  recentNotifications: AppNotification[] = [];
  private unreadSub?: Subscription;
  private newNotifSub?: Subscription;
  private pollSub?: Subscription;

  private static savedSidebarScrollTop = 0;

  constructor(
    private authService: AuthService,
    private router: Router,
    private apiService: ApiService,
    private permissionService: PermissionService,
    private notificationService: NotificationService
  ) { }

  onSidebarScroll(event: Event): void {
    const target = event.target as HTMLElement;
    if (target) {
      LayoutComponent.savedSidebarScrollTop = target.scrollTop;
    }
  }

  ngOnInit() {
    this.currentUser = this.authService.getCurrentUser();
    this.checkScreenSize();
    this.loadCompanyName();

    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe(() => {
      this.closeSidebar();
      this.notificationDropdownOpen = false;
      this.restoreSidebarScroll();
    });

    this.unreadSub = this.notificationService.unreadCount$.subscribe(count => {
      this.updateBellAnimation(count);
    });

    this.newNotifSub = this.notificationService.newNotification$.subscribe(notification => {
      this.triggerRealtimePulse();
      if (this.notificationDropdownOpen) {
        this.recentNotifications = [notification, ...this.recentNotifications.filter(n => n.id !== notification.id)].slice(0, 5);
      }
    });

    this.notificationService.refreshUnreadCount();
    this.notificationService.connectRealtime();

    // Poll unread count every 15s as backup reconciliation
    this.pollSub = interval(15000).subscribe(() => {
      this.notificationService.refreshUnreadCount();
      if (this.notificationDropdownOpen) {
        this.loadRecentNotifications();
      }
    });

    setTimeout(() => this.restoreSidebarScroll(), 50);
  }

  private triggerRealtimePulse(): void {
    const reduceMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduceMotion) return;
    const icons = document.querySelectorAll('.bell-btn mat-icon');
    if (icons.length > 0) {
      gsap.fromTo(icons, 
        { scale: 1.4, rotation: -15 }, 
        { scale: 1.0, rotation: 0, duration: 0.5, ease: 'back.out(1.7)' }
      );
    }
  }

  getNotificationIcon(module?: string, type?: string): string {
    if (!module) return 'notifications';
    const m = module.toUpperCase();
    if (m.includes('LEAVE')) return 'event_note';
    if (m.includes('PERMISSION')) return 'schedule';
    if (m.includes('PAYROLL') || m.includes('PAYSLIP')) return 'receipt_long';
    if (m.includes('ATTENDANCE')) return 'calendar_today';
    if (m.includes('BIOMETRIC') || m.includes('DEVICE')) return 'hardware';
    return 'notifications';
  }

  private restoreSidebarScroll(): void {
    const el = document.querySelector('.sidebar-nav');
    if (el && LayoutComponent.savedSidebarScrollTop > 0) {
      el.scrollTop = LayoutComponent.savedSidebarScrollTop;
    }
  }

  private bellTimeline: gsap.core.Timeline | null = null;

  private updateBellAnimation(count: number): void {
    this.unreadCount = count;
    const reduceMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (count <= 0 || reduceMotion) {
      if (this.bellTimeline) {
        this.bellTimeline.kill();
        this.bellTimeline = null;
        const buttons = document.querySelectorAll('.bell-btn mat-icon');
        gsap.set(buttons, { clearProps: 'all' });
      }
      return;
    }

    if (!this.bellTimeline) {
      const icons = document.querySelectorAll('.bell-btn mat-icon');
      if (icons.length > 0) {
        this.bellTimeline = gsap.timeline({ repeat: -1, repeatDelay: 2.5 });
        this.bellTimeline
          .to(icons, { rotation: -12, scale: 1.1, duration: 0.15, ease: 'power1.inOut' })
          .to(icons, { rotation: 12, scale: 1.1, duration: 0.15, ease: 'power1.inOut' })
          .to(icons, { rotation: -8, duration: 0.12, ease: 'power1.inOut' })
          .to(icons, { rotation: 8, duration: 0.12, ease: 'power1.inOut' })
          .to(icons, { rotation: 0, scale: 1.0, duration: 0.2, ease: 'power1.out', clearProps: 'transform' });
      }
    }
  }

  ngOnDestroy(): void {
    if (this.bellTimeline) {
      this.bellTimeline.kill();
      this.bellTimeline = null;
    }
    if (this.unreadSub) this.unreadSub.unsubscribe();
    if (this.newNotifSub) this.newNotifSub.unsubscribe();
    if (this.pollSub) this.pollSub.unsubscribe();
    this.notificationService.disconnectRealtime();
  }

  toggleNotificationDropdown(event: Event): void {
    event.stopPropagation();
    this.notificationDropdownOpen = !this.notificationDropdownOpen;
    if (this.notificationDropdownOpen) {
      this.loadRecentNotifications();
    }
  }

  loadRecentNotifications(): void {
    this.notificationService.getNotifications(undefined, undefined, 0, 5).subscribe({
      next: (data) => this.recentNotifications = data,
      error: (err) => console.error(err)
    });
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => {
        this.loadRecentNotifications();
      }
    });
  }

  onNotificationClick(item: AppNotification): void {
    this.notificationDropdownOpen = false;
    if (item.id && !item.read) {
      this.notificationService.markAsRead(item.id).subscribe();
    }
    if (item.navigationTarget) {
      this.router.navigateByUrl(item.navigationTarget);
    }
  }

  goToAllNotifications(): void {
    this.notificationDropdownOpen = false;
    this.router.navigate(['/notifications']);
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.notificationDropdownOpen) {
      this.notificationDropdownOpen = false;
    }
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

  hasAnyAttendanceAccess(): boolean {
    return (
      this.hasAccess('MY_ATTENDANCE') ||
      this.hasAccess('MASTER_ATTENDANCE') ||
      this.hasAccess('LEAVES') ||
      this.hasAccess('PERMISSIONS') ||
      this.hasAccess('PAYROLL_PAYSLIPS') ||
      this.hasAccess('BIOMETRIC_DEVICES') ||
      this.hasAccess('WORKING_HOURS_CONFIG')
    );
  }

  isAdmin(): boolean {
    return this.permissionService.isAdmin();
  }
}
