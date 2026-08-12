import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export interface ToastMessage {
  id: number;
  type: 'success' | 'error' | 'info' | 'warning';
  title: string;
  message: string;
  duration: number;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private toastId = 0;
  private toastSubject = new Subject<ToastMessage>();
  toast$ = this.toastSubject.asObservable();

  show(type: 'success' | 'error' | 'info' | 'warning', title: string, message: string, duration: number = 2500) {
    const toast: ToastMessage = {
      id: ++this.toastId,
      type,
      title,
      message,
      duration
    };
    this.toastSubject.next(toast);
  }

  success(title: string, message: string, duration: number = 2500) {
    this.show('success', title, message, duration);
  }

  error(title: string, message: string, duration: number = 5000) {
    this.show('error', title, message, duration);
  }

  info(title: string, message: string, duration: number = 2500) {
    this.show('info', title, message, duration);
  }

  warning(title: string, message: string, duration: number = 3000) {
    this.show('warning', title, message, duration);
  }
}
