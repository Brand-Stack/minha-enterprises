export interface AppNotification {
  id?: string;
  recipientEmployeeId?: string;
  recipientEmail?: string;
  type?: string;
  title: string;
  message: string;
  module?: string;
  entityType?: string;
  entityId?: string;
  navigationTarget?: string;
  priority?: 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
  read: boolean;
  createdAt?: string;
  readAt?: string;
  metadata?: Record<string, string>;
}
