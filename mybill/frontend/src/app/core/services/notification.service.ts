import { Injectable, NgZone } from '@angular/core';
import { ApiService } from './api.service';
import { AppNotification } from '../models/notification.model';
import { BehaviorSubject, Observable, Subject, interval, Subscription, of } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$: Observable<number> = this.unreadCountSubject.asObservable();

  private newNotificationSubject = new Subject<AppNotification>();
  public newNotification$: Observable<AppNotification> = this.newNotificationSubject.asObservable();

  private eventSource?: EventSource;
  private pollingSubscription?: Subscription;
  private reconnectTimer?: any;

  constructor(private apiService: ApiService, private zone: NgZone) {}

  public connectRealtime(): void {
    if (typeof window === 'undefined' || !('EventSource' in window)) {
      return;
    }
    const token = localStorage.getItem('token');
    if (!token) {
      return;
    }

    if (this.eventSource) {
      this.eventSource.close();
    }

    const streamUrl = `${this.apiService.getApiUrl()}/notifications/stream?token=${encodeURIComponent(token)}`;
    this.eventSource = new EventSource(streamUrl);

    this.eventSource.addEventListener('NOTIFICATION', (event: MessageEvent) => {
      this.zone.run(() => {
        try {
          const notification: AppNotification = JSON.parse(event.data);
          this.unreadCountSubject.next(this.unreadCountSubject.value + 1);
          this.newNotificationSubject.next(notification);
        } catch (e) {
          console.error('Failed to parse SSE notification:', e);
        }
      });
    });

    this.eventSource.addEventListener('PING', () => {
      // Keepalive ping received
    });

    this.eventSource.onerror = () => {
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = undefined;
      }
      if (!this.reconnectTimer) {
        this.reconnectTimer = setTimeout(() => {
          this.reconnectTimer = undefined;
          this.connectRealtime();
        }, 5000);
      }
    };
  }

  public disconnectRealtime(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = undefined;
    }
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }

  public startPolling(intervalMs: number = 15000): void {
    this.refreshUnreadCount();
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
    }
    this.pollingSubscription = interval(intervalMs).subscribe(() => {
      this.refreshUnreadCount();
    });
  }

  public stopPolling(): void {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = undefined;
    }
  }

  public refreshUnreadCount(): void {
    this.apiService.get<{ unreadCount: number }>('/notifications/unread-count')
      .pipe(
        catchError(() => of({ unreadCount: 0 }))
      )
      .subscribe({
        next: (res) => {
          if (res && typeof res.unreadCount === 'number') {
            this.unreadCountSubject.next(res.unreadCount);
          }
        }
      });
  }

  public getNotifications(read?: boolean, module?: string, page: number = 0, size: number = 20): Observable<AppNotification[]> {
    const params: any = { page, size };
    if (read !== undefined && read !== null) {
      params.read = read;
    }
    if (module && module.trim() !== '') {
      params.module = module;
    }
    return this.apiService.get<AppNotification[]>('/notifications', params);
  }

  public markAsRead(id: string): Observable<void> {
    return this.apiService.put<void>(`/notifications/${id}/read`, {}).pipe(
      tap(() => this.refreshUnreadCount())
    );
  }

  public markAllAsRead(): Observable<void> {
    return this.apiService.put<void>('/notifications/read-all', {}).pipe(
      tap(() => this.unreadCountSubject.next(0))
    );
  }

  public deleteNotification(id: string): Observable<void> {
    return this.apiService.delete<void>('/notifications', id).pipe(
      tap(() => this.refreshUnreadCount())
    );
  }
}
