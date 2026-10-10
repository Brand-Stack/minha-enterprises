import { Injectable } from '@angular/core';
import { ModulePermission, StandardAction } from '../models/permission.model';

const STANDARD_ACTIONS: StandardAction[] = [
  'view',
  'create',
  'edit',
  'delete',
  'export',
  'print',
  'email',
  'download'
];

/**
 * Central client-side entitlement resolver. The effective permission matrix is
 * delivered in the login response and cached in localStorage; this service is the
 * single source of truth for "can the current user do X?" decisions in the UI.
 *
 * <p>This is a UX convenience layer only — the authoritative checks happen on the
 * backend via {@code @RequiresPermission}. Hiding a control here never replaces
 * server-side enforcement.</p>
 */
@Injectable({
  providedIn: 'root'
})
export class PermissionService {
  /** Check if the current user has a given action on a module. ADMIN bypasses all checks. */
  hasPermission(moduleKey: string, action: string = 'view'): boolean {
    if (!moduleKey) {
      return false;
    }
    if (this.isAdmin()) {
      return true;
    }

    const perm = this.getModulePermission(moduleKey);
    if (!perm) {
      return false;
    }
    return this.isActionAllowed(perm, action);
  }

  /** True if the user can see a field within a module. Defaults to visible when unconfigured. */
  hasFieldAccess(moduleKey: string, fieldKey: string): boolean {
    if (this.isAdmin()) {
      return true;
    }
    const perm = this.getModulePermission(moduleKey);
    if (!perm) {
      return false;
    }
    const field = perm.fields ? perm.fields[fieldKey] : undefined;
    // Unconfigured fields are visible by default; only an explicit { visible: false } hides them.
    return field ? field.visible : true;
  }

  /** True if a field is editable within a module. Defaults to editable when unconfigured. */
  isFieldEditable(moduleKey: string, fieldKey: string): boolean {
    if (this.isAdmin()) {
      return true;
    }
    const perm = this.getModulePermission(moduleKey);
    if (!perm) {
      return false;
    }
    const field = perm.fields ? perm.fields[fieldKey] : undefined;
    return field ? field.editable : true;
  }

  /** Return the set of dashboard card keys the user is allowed to see for a module. */
  getVisibleCards(moduleKey: string): string[] {
    const perm = this.getModulePermission(moduleKey);
    if (this.isAdmin()) {
      return perm && perm.dashboardCards ? Object.keys(perm.dashboardCards) : [];
    }
    if (!perm || !perm.dashboardCards) {
      return [];
    }
    return Object.keys(perm.dashboardCards).filter((key) => perm.dashboardCards![key]);
  }

  /** True if a specific dashboard card is visible. ADMIN sees all. */
  hasCard(moduleKey: string, cardKey: string): boolean {
    if (this.isAdmin()) {
      return true;
    }
    const perm = this.getModulePermission(moduleKey);
    if (!perm || !perm.dashboardCards) {
      return false;
    }
    return !!perm.dashboardCards[cardKey];
  }

  isAdmin(): boolean {
    const user = this.getStoredUser();
    if (!user) {
      return false;
    }
    if (user.admin === true) {
      return true;
    }
    return (user.role || '').toUpperCase() === 'ADMIN';
  }

  /** The full permission matrix keyed by module key. */
  getPermissions(): { [moduleKey: string]: ModulePermission } {
    const user = this.getStoredUser();
    return user && user.permissions ? user.permissions : {};
  }

  private getModulePermission(moduleKey: string): ModulePermission | undefined {
    return this.getPermissions()[moduleKey];
  }

  private isActionAllowed(perm: ModulePermission, action: string): boolean {
    const normalized = (action || '').toLowerCase();
    if ((STANDARD_ACTIONS as string[]).includes(normalized)) {
      return !!(perm as any)[normalized];
    }
    // Button / custom action-level lookup — try exact key, then upper-case.
    if (perm.actions) {
      if (perm.actions[action] !== undefined) {
        return !!perm.actions[action];
      }
      const upper = action.toUpperCase();
      if (perm.actions[upper] !== undefined) {
        return !!perm.actions[upper];
      }
    }
    return false;
  }

  getCurrentUser(): any {
    return this.getStoredUser();
  }

  private getStoredUser(): any {
    try {
      const userStr = localStorage.getItem('user');
      return userStr ? JSON.parse(userStr) : null;
    } catch {
      return null;
    }
  }
}
