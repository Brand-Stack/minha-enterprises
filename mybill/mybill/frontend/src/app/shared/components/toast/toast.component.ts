import { Component, OnInit, OnDestroy } from '@angular/core';
import { ToastService, ToastMessage } from './toast.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-toast',
  template: `
    <div class="toast-container">
      <div *ngFor="let toast of toasts; trackBy: trackById" 
           [class]="'toast toast-' + toast.type"
           role="alert"
           (click)="removeToast(toast.id)">
        <div class="toast-content">
          <mat-icon class="toast-icon">{{ getIcon(toast.type) }}</mat-icon>
          <div class="toast-text">
            <p class="toast-title">{{ toast.title }}</p>
            <p class="toast-message">{{ toast.message }}</p>
          </div>
          <button class="toast-close" (click)="removeToast(toast.id); $event.stopPropagation()">
            <mat-icon>close</mat-icon>
          </button>
        </div>
        <div class="toast-progress" [style.animation-duration.ms]="toast.duration"></div>
      </div>
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      top: 80px;
      right: 16px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 400px;
      width: calc(100vw - 32px);
    }
    
    .toast {
      padding: 16px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      cursor: pointer;
      animation: slideIn 0.3s ease-out;
      position: relative;
      overflow: hidden;
    }
    
    @keyframes slideIn {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }
    
    .toast-success {
      background: #10B981;
      color: white;
    }
    
    .toast-error {
      background: #EF4444;
      color: white;
    }
    
    .toast-warning {
      background: #F59E0B;
      color: white;
    }
    
    .toast-info {
      background: #3B82F6;
      color: white;
    }
    
    .toast-content {
      display: flex;
      align-items: flex-start;
      gap: 12px;
    }
    
    .toast-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
      flex-shrink: 0;
    }
    
    .toast-text {
      flex: 1;
      min-width: 0;
    }
    
    .toast-title {
      font-weight: 600;
      font-size: 14px;
      margin: 0 0 4px 0;
    }
    
    .toast-message {
      font-size: 13px;
      margin: 0;
      opacity: 0.9;
    }
    
    .toast-close {
      background: transparent;
      border: none;
      color: inherit;
      cursor: pointer;
      padding: 0;
      opacity: 0.7;
      transition: opacity 0.2s;
      flex-shrink: 0;
    }
    
    .toast-close:hover {
      opacity: 1;
    }
    
    .toast-close mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    
    .toast-progress {
      position: absolute;
      bottom: 0;
      left: 0;
      height: 3px;
      background: rgba(255, 255, 255, 0.5);
      animation: progress linear forwards;
    }
    
    @keyframes progress {
      from { width: 100%; }
      to { width: 0%; }
    }
    
    @media (max-width: 480px) {
      .toast-container {
        right: 8px;
        left: 8px;
        width: auto;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .toast {
        animation: none;
      }
      .toast-progress {
        display: none;
      }
    }
  `]
})
export class ToastComponent implements OnInit, OnDestroy {
  toasts: ToastMessage[] = [];
  private subscription?: Subscription;
  private timeouts: Map<number, any> = new Map();

  constructor(private toastService: ToastService) {}

  ngOnInit() {
    this.subscription = this.toastService.toast$.subscribe(toast => {
      this.toasts.push(toast);
      
      if (toast.duration > 0) {
        const timeout = setTimeout(() => {
          this.removeToast(toast.id);
        }, toast.duration);
        this.timeouts.set(toast.id, timeout);
      }
    });
  }

  ngOnDestroy() {
    this.subscription?.unsubscribe();
    this.timeouts.forEach(timeout => clearTimeout(timeout));
  }

  removeToast(id: number) {
    const timeout = this.timeouts.get(id);
    if (timeout) {
      clearTimeout(timeout);
      this.timeouts.delete(id);
    }
    this.toasts = this.toasts.filter(t => t.id !== id);
  }

  trackById(index: number, toast: ToastMessage): number {
    return toast.id;
  }

  getIcon(type: string): string {
    switch (type) {
      case 'success': return 'check_circle';
      case 'error': return 'error';
      case 'warning': return 'warning';
      default: return 'info';
    }
  }
}
