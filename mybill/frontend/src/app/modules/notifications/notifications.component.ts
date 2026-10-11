import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../../core/services/notification.service';
import { AppNotification } from '../../core/models/notification.model';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-notifications',
  template: `
    <div class="page-container">
      <!-- Header Banner -->
      <div class="page-header indigo-accent">
        <div class="header-content">
          <div class="header-icon">
            <mat-icon>notifications</mat-icon>
          </div>
          <div>
            <h2>Application Notifications</h2>
            <p class="subtitle">View and manage your application-wide notifications, approvals, and system updates</p>
          </div>
        </div>

        <div class="header-actions">
          <button mat-flat-button class="btn-mark-all" (click)="markAllAsRead()">
            <mat-icon>done_all</mat-icon> Mark All as Read
          </button>
        </div>
      </div>

      <!-- Filter Controls -->
      <div class="filter-card">
        <div class="status-tabs">
          <button class="tab-btn" [class.active]="selectedTab === 'ALL'" (click)="setTab('ALL')">
            All Notifications
          </button>
          <button class="tab-btn" [class.active]="selectedTab === 'UNREAD'" (click)="setTab('UNREAD')">
            Unread <span class="badge" *ngIf="unreadCount > 0">{{ unreadCount }}</span>
          </button>
          <button class="tab-btn" [class.active]="selectedTab === 'READ'" (click)="setTab('READ')">
            Read
          </button>
        </div>

        <div class="module-filter">
          <mat-icon class="filter-icon">filter_list</mat-icon>
          <select [(ngModel)]="selectedModule" (change)="loadNotifications()" class="module-select">
            <option value="">All Modules</option>
            <option value="MY_ATTENDANCE">My Attendance</option>
            <option value="MASTER_ATTENDANCE">Master Attendance</option>
            <option value="LEAVES">Leaves</option>
            <option value="PERMISSIONS">Permissions</option>
            <option value="PAYROLL_PAYSLIPS">Payroll & Payslips</option>
            <option value="BIOMETRIC_DEVICES">Biometric Devices</option>
            <option value="WORKING_HOURS_CONFIG">Working Hours Config</option>
          </select>
        </div>
      </div>

      <!-- Notifications List -->
      <div class="notifications-card">
        <div class="notification-list" *ngIf="notifications.length > 0">
          <div 
            *ngFor="let item of notifications" 
            class="notification-item"
            [class.unread]="!item.read"
            (click)="onNotificationClick(item)">
            
            <div class="item-icon" [class]="getIconClass(item)">
              <mat-icon>{{ getIconName(item) }}</mat-icon>
            </div>

            <div class="item-content">
              <div class="item-header">
                <h4 class="item-title">{{ item.title }}</h4>
                <span class="item-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <p class="item-message">{{ item.message }}</p>
              <div class="item-footer">
                <span class="module-tag" [class]="item.module?.toLowerCase()">{{ formatModule(item.module) }}</span>
                <span class="status-tag" [class.read]="item.read" [class.unread]="!item.read">
                  {{ item.read ? 'Read' : 'New Unread' }}
                </span>
              </div>
            </div>

            <div class="item-actions" (click)="$event.stopPropagation()">
              <button mat-icon-button *ngIf="!item.read" (click)="markAsRead(item)" matTooltip="Mark as read">
                <mat-icon class="action-icon text-blue">check_circle_outline</mat-icon>
              </button>
              <button mat-icon-button (click)="deleteNotification(item)" matTooltip="Delete notification">
                <mat-icon class="action-icon text-red">delete_outline</mat-icon>
              </button>
            </div>
          </div>
        </div>

        <div *ngIf="notifications.length === 0" class="empty-state">
          <mat-icon class="empty-icon">notifications_off</mat-icon>
          <p class="empty-title">No Notifications Found</p>
          <p class="empty-desc">You are all caught up! You will be notified here when new requests or updates arrive.</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    
    .page-header { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 20px;
      padding: 20px 24px;
      background: #FFFFFF;
      border-radius: 14px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.04);
      border-left: 6px solid #2563EB;
      flex-wrap: wrap;
      gap: 16px;
    }
    .header-content { display: flex; align-items: center; gap: 16px; }
    .header-icon { 
      width: 48px; 
      height: 48px; 
      border-radius: 12px; 
      background: #EFF6FF; 
      color: #2563EB; 
      display: flex; 
      align-items: center; 
      justify-content: center;
    }
    .header-icon mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .page-header h2 { font-size: 20px; font-weight: 700; color: #0F172A; margin: 0; }
    .subtitle { color: #64748B; font-size: 13px; margin-top: 2px; margin-bottom: 0; }

    .btn-mark-all { background: #2563EB !important; color: white !important; font-weight: 600; border-radius: 8px; }

    .filter-card {
      background: white;
      padding: 14px 20px;
      border-radius: 14px;
      border: 1px solid #E2E8F0;
      margin-bottom: 20px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 16px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.02);
    }
    .status-tabs { display: flex; gap: 8px; }
    .tab-btn {
      padding: 8px 16px;
      border: 1px solid #CBD5E1;
      border-radius: 8px;
      background: #F8FAFC;
      color: #475569;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s;
    }
    .tab-btn:hover { background: #F1F5F9; color: #0F172A; }
    .tab-btn.active { background: #2563EB; color: white; border-color: #2563EB; }
    .tab-btn .badge { background: #EF4444; color: white; border-radius: 10px; padding: 2px 6px; font-size: 11px; font-weight: 800; }

    .module-filter { display: flex; align-items: center; gap: 8px; }
    .filter-icon { color: #64748B; }
    .module-select { padding: 8px 12px; border: 1px solid #CBD5E1; border-radius: 8px; font-size: 13px; font-weight: 600; outline: none; background: white; color: #0F172A; }

    .notifications-card { background: white; border-radius: 14px; border: 1px solid #E2E8F0; box-shadow: 0 2px 12px rgba(0,0,0,0.03); overflow: hidden; }
    .notification-list { display: flex; flex-direction: column; }
    .notification-item {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      padding: 18px 24px;
      border-bottom: 1px solid #F1F5F9;
      cursor: pointer;
      transition: background 0.2s;
    }
    .notification-item:hover { background-color: #F8FAFC; }
    .notification-item.unread { background-color: #EFF6FF; border-left: 4px solid #2563EB; }

    .item-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .item-icon.blue { background: #DBEAFE; color: #1D4ED8; }
    .item-icon.green { background: #DCFCE7; color: #15803D; }
    .item-icon.red { background: #FEE2E2; color: #B91C1C; }
    .item-icon.purple { background: #F3E8FF; color: #7E22CE; }
    .item-icon.amber { background: #FEF3C7; color: #B45309; }

    .item-content { flex: 1; min-width: 0; }
    .item-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .item-title { font-size: 15px; font-weight: 700; color: #0F172A; margin: 0; }
    .item-time { font-size: 12px; color: #64748B; font-weight: 500; }
    .item-message { font-size: 13.5px; color: #334155; margin: 0 0 10px 0; line-height: 1.4; }

    .item-footer { display: flex; gap: 10px; align-items: center; }
    .module-tag { padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; background: #F1F5F9; color: #475569; }
    .module-tag.leave_mgmt { background: #DCFCE7; color: #15803D; }
    .module-tag.permission_mgmt { background: #F3E8FF; color: #7E22CE; }
    .module-tag.payroll { background: #D1FAE5; color: #047857; }
    .module-tag.salary_advance { background: #FEF3C7; color: #B45309; }

    .status-tag { font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 10px; }
    .status-tag.unread { background: #2563EB; color: white; }
    .status-tag.read { background: #E2E8F0; color: #64748B; }

    .item-actions { display: flex; gap: 4px; align-items: center; }
    .action-icon { font-size: 20px; width: 20px; height: 20px; }
    .text-blue { color: #2563EB; }
    .text-red { color: #EF4444; }

    .empty-state { text-align: center; padding: 64px 24px; color: #64748B; }
    .empty-icon { font-size: 56px; width: 56px; height: 56px; color: #CBD5E1; margin-bottom: 12px; }
    .empty-title { font-size: 18px; font-weight: 700; color: #334155; margin: 0 0 6px 0; }
    .empty-desc { font-size: 13.5px; color: #94A3B8; margin: 0; }
  `]
})
export class NotificationsComponent implements OnInit, OnDestroy {
  notifications: AppNotification[] = [];
  selectedTab: 'ALL' | 'UNREAD' | 'READ' = 'ALL';
  selectedModule: string = '';
  unreadCount = 0;
  private unreadSub?: Subscription;

  constructor(
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.unreadSub = this.notificationService.unreadCount$.subscribe(count => {
      this.unreadCount = count;
    });
    this.loadNotifications();
  }

  ngOnDestroy(): void {
    if (this.unreadSub) {
      this.unreadSub.unsubscribe();
    }
  }

  setTab(tab: 'ALL' | 'UNREAD' | 'READ'): void {
    this.selectedTab = tab;
    this.loadNotifications();
  }

  loadNotifications(): void {
    let readFilter: boolean | undefined = undefined;
    if (this.selectedTab === 'UNREAD') readFilter = false;
    if (this.selectedTab === 'READ') readFilter = true;

    this.notificationService.getNotifications(readFilter, this.selectedModule, 0, 50).subscribe({
      next: (data) => this.notifications = data,
      error: (err) => console.error(err)
    });
  }

  markAsRead(item: AppNotification): void {
    if (!item.id || item.read) return;
    this.notificationService.markAsRead(item.id).subscribe({
      next: () => {
        item.read = true;
        this.loadNotifications();
      }
    });
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => this.loadNotifications()
    });
  }

  deleteNotification(item: AppNotification): void {
    if (!item.id) return;
    this.notificationService.deleteNotification(item.id).subscribe({
      next: () => this.loadNotifications()
    });
  }

  onNotificationClick(item: AppNotification): void {
    if (item.id && !item.read) {
      this.notificationService.markAsRead(item.id).subscribe();
    }
    if (item.navigationTarget) {
      this.router.navigateByUrl(item.navigationTarget);
    }
  }

  formatTime(dateStr?: string): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return dateStr;
    }
  }

  formatModule(module?: string): string {
    if (!module) return 'SYSTEM';
    return module.replace('_', ' ');
  }

  getIconClass(item: AppNotification): string {
    if (item.type?.includes('APPROVED') || item.type?.includes('GENERATED')) return 'green';
    if (item.type?.includes('REJECTED') || item.type?.includes('FAILURE')) return 'red';
    if (item.type?.includes('SUBMITTED')) return 'amber';
    return 'blue';
  }

  getIconName(item: AppNotification): string {
    if (item.type?.includes('LEAVE')) return 'event_note';
    if (item.type?.includes('PERMISSION')) return 'schedule';
    if (item.type?.includes('PAYSLIP') || item.type?.includes('BONUS')) return 'payments';
    if (item.type?.includes('ADVANCE')) return 'account_balance_wallet';
    if (item.type?.includes('DEVICE')) return 'hardware';
    return 'notifications';
  }
}
