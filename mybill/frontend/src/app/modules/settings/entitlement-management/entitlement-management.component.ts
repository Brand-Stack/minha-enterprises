import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { forkJoin } from 'rxjs';
import {
  Entitlement,
  EntitlementService,
  ModuleRegistryEntry
} from '../../../core/services/entitlement.service';
import { ModulePermission, StandardAction } from '../../../core/models/permission.model';
import { ApiService } from '../../../core/services/api.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

interface EmployeeCategoryOption {
  id: string;
  name: string;
}

interface ModuleGroup {
  parent: string;
  modules: ModuleRegistryEntry[];
  expanded: boolean;
}

const STANDARD_OPS: StandardAction[] = ['view', 'create', 'edit', 'delete', 'export', 'print', 'email', 'download'];

const OP_META: Record<StandardAction, { icon: string; label: string }> = {
  view: { icon: 'visibility', label: 'View' },
  create: { icon: 'add_circle_outline', label: 'Create' },
  edit: { icon: 'edit', label: 'Edit' },
  delete: { icon: 'delete_outline', label: 'Delete' },
  export: { icon: 'ios_share', label: 'Export' },
  print: { icon: 'print', label: 'Print' },
  email: { icon: 'mail_outline', label: 'Email' },
  download: { icon: 'download', label: 'Download' }
};

@Component({
  selector: 'app-entitlement-management',
  template: `
    <div class="access-page">
      <!-- Page Header -->
      <header class="access-header">
        <div class="header-left">
          <div class="header-icon">
            <mat-icon>shield</mat-icon>
          </div>
          <div>
            <h1 class="page-title">Access Management</h1>
            <p class="page-subtitle">
              Manage role-based module permissions and capabilities across employee categories.
            </p>
          </div>
        </div>
      </header>

      <!-- Main Toolbar & Control Card -->
      <div class="control-card">
        <div class="toolbar-row">
          <div class="role-selector-group">
            <mat-form-field appearance="outline" class="role-select-field">
              <mat-label>Employee Category / Role</mat-label>
              <mat-select [(value)]="selectedCategoryId" (selectionChange)="onCategoryChange()">
                <mat-option *ngFor="let cat of categories" [value]="cat.id">
                  {{ cat.name }}
                </mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="toolbar-actions" *ngIf="selectedCategoryId">
            <button mat-button type="button" class="btn-action btn-select-all" (click)="selectAll(true)" [disabled]="loading || saving">
              <mat-icon>done_all</mat-icon>
              <span>Select All</span>
            </button>
            <button mat-button type="button" class="btn-action btn-clear-all" (click)="selectAll(false)" [disabled]="loading || saving">
              <mat-icon>clear_all</mat-icon>
              <span>Clear All</span>
            </button>
            <button mat-raised-button type="button" class="btn-action btn-save" (click)="save()" [disabled]="saving || loading">
              <mat-icon>{{ saving ? 'hourglass_top' : 'save' }}</mat-icon>
              <span>{{ saving ? 'Saving...' : 'Save Permissions' }}</span>
            </button>
          </div>
        </div>

        <!-- Summary Stats Bar -->
        <div class="stats-row" *ngIf="selectedCategoryId && !loading">
          <div class="stat-item">
            <span class="stat-label">Active Role:</span>
            <span class="stat-value highlight">{{ selectedCategoryName }}</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-label">Total Modules:</span>
            <span class="stat-value">{{ modules.length }} modules</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-label">Granted Permissions:</span>
            <span class="stat-value success">{{ grantedCount }} active</span>
          </div>
        </div>
      </div>

      <!-- Loading State -->
      <div class="state-card loading-state" *ngIf="loading">
        <mat-spinner diameter="36" strokeWidth="3"></mat-spinner>
        <span>Loading permission matrix...</span>
      </div>

      <!-- Empty State -->
      <div class="state-card empty-state" *ngIf="!selectedCategoryId && !loading">
        <div class="empty-icon">
          <mat-icon>admin_panel_settings</mat-icon>
        </div>
        <h3>Select an Employee Category</h3>
        <p>Choose an employee category from the dropdown above to configure permissions.</p>
      </div>

      <!-- Permission Matrix Card -->
      <div class="matrix-card" *ngIf="selectedCategoryId && !loading">
        <div class="table-container">
          <table class="perm-table">
            <thead>
              <tr>
                <th class="col-module">
                  <div class="th-inner">
                    <mat-icon>grid_view</mat-icon>
                    <span>Module / Feature</span>
                  </div>
                </th>
                <th *ngFor="let op of standardOps" class="col-op">
                  <div class="op-header">
                    <mat-icon class="op-icon">{{ opMeta[op].icon }}</mat-icon>
                    <span class="op-label">{{ opMeta[op].label }}</span>
                  </div>
                </th>
                <th class="col-adv"></th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let group of groups">
                <!-- Group Header Row -->
                <tr class="row-group" (click)="group.expanded = !group.expanded">
                  <td class="col-module">
                    <div class="group-title">
                      <mat-icon class="chevron-icon">{{ group.expanded ? 'expand_more' : 'chevron_right' }}</mat-icon>
                      <span class="group-name">{{ group.parent }}</span>
                      <span class="group-count">({{ group.modules.length }})</span>
                    </div>
                  </td>
                  <td *ngFor="let op of standardOps" class="col-op" (click)="$event.stopPropagation()">
                    <div class="cell-center" *ngIf="groupHasOp(group, op)">
                      <mat-checkbox
                        color="primary"
                        [checked]="isGroupOpChecked(group, op)"
                        [indeterminate]="isGroupOpIndeterminate(group, op)"
                        (change)="toggleGroupOp(group, op, $event)">
                      </mat-checkbox>
                    </div>
                    <span *ngIf="!groupHasOp(group, op)" class="cell-na">—</span>
                  </td>
                  <td class="col-adv"></td>
                </tr>

                <!-- Module Rows -->
                <ng-container *ngIf="group.expanded">
                  <ng-container *ngFor="let mod of group.modules">
                    <tr class="row-module" [class.is-expanded]="advancedOpen[mod.moduleKey]">
                      <td class="col-module">
                        <div class="module-title">
                          <mat-icon class="mod-icon">{{ getModuleIcon(mod.moduleKey) }}</mat-icon>
                          <span>{{ mod.displayName }}</span>
                        </div>
                      </td>
                      <td *ngFor="let op of standardOps" class="col-op">
                        <div class="cell-center" *ngIf="mod.operations.includes(op)">
                          <mat-checkbox
                            color="primary"
                            [checked]="isOpChecked(mod.moduleKey, op)"
                            (change)="toggleOp(mod.moduleKey, op, $event)">
                          </mat-checkbox>
                        </div>
                        <span *ngIf="!mod.operations.includes(op)" class="cell-na">—</span>
                      </td>
                      <td class="col-adv">
                        <button mat-icon-button
                                type="button"
                                class="btn-tune"
                                [class.active]="advancedOpen[mod.moduleKey]"
                                *ngIf="hasAdvanced(mod)"
                                (click)="toggleAdvanced(mod.moduleKey)"
                                matTooltip="Granular settings">
                          <mat-icon>{{ advancedOpen[mod.moduleKey] ? 'expand_less' : 'tune' }}</mat-icon>
                        </button>
                      </td>
                    </tr>

                    <!-- Advanced Panel -->
                    <tr class="row-advanced" *ngIf="advancedOpen[mod.moduleKey] && hasAdvanced(mod)">
                      <td [attr.colspan]="standardOps.length + 2">
                        <div class="advanced-container">
                          <div class="adv-title">
                            <mat-icon>tune</mat-icon>
                            <span>Granular Permissions — {{ mod.displayName }}</span>
                          </div>
                          <div class="adv-grid-wrapper">
                            <div class="adv-box" *ngIf="mod.actions?.length">
                              <h5>Specific Actions</h5>
                              <div class="adv-options">
                                <label *ngFor="let a of mod.actions">
                                  <mat-checkbox
                                    color="primary"
                                    [checked]="getAction(mod.moduleKey, a.key)"
                                    (change)="toggleAction(mod.moduleKey, a.key, $event)">
                                    {{ a.displayName }}
                                  </mat-checkbox>
                                </label>
                              </div>
                            </div>

                            <div class="adv-box" *ngIf="mod.dashboardCards?.length">
                              <h5>Dashboard Cards</h5>
                              <div class="adv-options">
                                <label *ngFor="let c of mod.dashboardCards">
                                  <mat-checkbox
                                    color="primary"
                                    [checked]="getCard(mod.moduleKey, c.key)"
                                    (change)="toggleCard(mod.moduleKey, c.key, $event)">
                                    {{ c.displayName }}
                                  </mat-checkbox>
                                </label>
                              </div>
                            </div>

                            <div class="adv-box" *ngIf="mod.fields?.length">
                              <h5>Field Visibility</h5>
                              <div class="adv-options">
                                <label *ngFor="let f of mod.fields">
                                  <mat-checkbox
                                    color="primary"
                                    [checked]="getFieldVisible(mod.moduleKey, f.key)"
                                    (change)="toggleField(mod.moduleKey, f.key, $event)">
                                    {{ f.displayName }} <span class="hint">(visible)</span>
                                  </mat-checkbox>
                                </label>
                              </div>
                            </div>
                          </div>
                        </div>
                      </td>
                    </tr>
                  </ng-container>
                </ng-container>
              </ng-container>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .access-page {
      padding: 24px 32px;
      max-width: 1400px;
      margin: 0 auto;
      font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #0f172a;
    }

    .access-header {
      margin-bottom: 20px;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .header-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: #eff6ff;
      color: #2563eb;
      border: 1px solid #bfdbfe;

      mat-icon {
        font-size: 24px;
        width: 24px;
        height: 24px;
      }
    }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
    }

    .page-subtitle {
      margin: 2px 0 0;
      font-size: 0.875rem;
      color: #64748b;
    }

    .control-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 18px 24px;
      margin-bottom: 20px;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
    }

    .toolbar-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 20px;
      flex-wrap: wrap;
    }

    .role-selector-group {
      flex: 1;
      min-width: 280px;
      max-width: 380px;
    }

    .role-select-field {
      width: 100%;
      margin-bottom: -1.25em;

      ::ng-deep .mat-mdc-form-field-flex {
        background-color: #ffffff !important;
      }
    }

    .toolbar-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .btn-action {
      height: 40px !important;
      padding: 0 18px !important;
      border-radius: 8px !important;
      font-size: 13px !important;
      font-weight: 700 !important;
      letter-spacing: 0.01em;
      transition: all 0.15s ease !important;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        margin-right: 6px;
      }
    }

    .btn-select-all {
      background-color: #f0fdf4 !important;
      border: 1px solid #86efac !important;
      color: #15803d !important;

      mat-icon {
        color: #16a34a !important;
      }

      &:hover:not([disabled]) {
        background-color: #dcfce7 !important;
        border-color: #4ade80 !important;
        color: #166534 !important;
      }
    }

    .btn-clear-all {
      background-color: #fef2f2 !important;
      border: 1px solid #fca5a5 !important;
      color: #b91c1c !important;

      mat-icon {
        color: #dc2626 !important;
      }

      &:hover:not([disabled]) {
        background-color: #fee2e2 !important;
        border-color: #f87171 !important;
        color: #991b1b !important;
      }
    }

    .btn-save {
      background: linear-gradient(135deg, #4f46e5 0%, #4338ca 100%) !important;
      color: #ffffff !important;
      box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3) !important;

      mat-icon {
        color: #ffffff !important;
      }

      &:hover:not([disabled]) {
        background: linear-gradient(135deg, #4338ca 0%, #3730a3 100%) !important;
        box-shadow: 0 6px 16px rgba(79, 70, 229, 0.4) !important;
      }
    }

    .stats-row {
      display: flex;
      align-items: center;
      gap: 20px;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #f1f5f9;
      flex-wrap: wrap;
    }

    .stat-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
    }

    .stat-label {
      color: #64748b;
      font-weight: 500;
    }

    .stat-value {
      color: #0f172a;
      font-weight: 600;

      &.highlight {
        color: #2563eb;
        background: #eff6ff;
        padding: 2px 10px;
        border-radius: 6px;
        border: 1px solid #bfdbfe;
      }

      &.success {
        color: #166534;
        background: #f0fdf4;
        padding: 2px 10px;
        border-radius: 6px;
        border: 1px solid #bbf7d0;
      }
    }

    .stat-divider {
      width: 1px;
      height: 16px;
      background: #e2e8f0;
    }

    .state-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 48px 24px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 12px;
    }

    .empty-state {
      border: 2px dashed #e2e8f0;
      background: #f8fafc;

      .empty-icon {
        width: 52px;
        height: 52px;
        border-radius: 12px;
        background: #eff6ff;
        color: #2563eb;
        display: flex;
        align-items: center;
        justify-content: center;

        mat-icon {
          font-size: 28px;
          width: 28px;
          height: 28px;
        }
      }

      h3 {
        margin: 0;
        font-size: 1.1rem;
        font-weight: 600;
        color: #0f172a;
      }

      p {
        margin: 0;
        font-size: 0.875rem;
        color: #64748b;
        max-width: 380px;
      }
    }

    .loading-state {
      color: #64748b;
      font-size: 0.875rem;
      font-weight: 500;
    }

    .matrix-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
    }

    .table-container {
      max-height: calc(100vh - 270px);
      min-height: 400px;
      overflow: auto;
    }

    .perm-table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      min-width: 960px;
      table-layout: fixed;

      thead {
        position: sticky;
        top: 0;
        z-index: 10;
      }

      th {
        background: #f8fafc;
        border-bottom: 2px solid #e2e8f0;
        padding: 12px 10px;
        text-align: center;
        vertical-align: middle;
      }
    }

    .col-module {
      width: 280px;
      min-width: 280px;
      text-align: left !important;
      padding-left: 20px !important;
    }

    .col-op {
      width: 80px;
      min-width: 80px;
    }

    .col-adv {
      width: 52px;
      min-width: 52px;
    }

    .th-inner {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #475569;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: #64748b;
      }
    }

    .op-header {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;

      .op-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #475569;
      }

      .op-label {
        font-size: 10px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #475569;
      }
    }

    .row-group {
      background: #f1f5f9;
      cursor: pointer;

      &:hover {
        background: #e2e8f0;
      }

      td {
        border-top: 1px solid #cbd5e1;
        border-bottom: 1px solid #cbd5e1;
        padding: 10px 8px;
      }
    }

    .group-title {
      display: flex;
      align-items: center;
      gap: 6px;

      .chevron-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: #475569;
      }

      .group-name {
        font-size: 12px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #0f172a;
      }

      .group-count {
        font-size: 12px;
        color: #64748b;
        font-weight: 500;
      }
    }

    .row-module {
      &:hover {
        background: #f8fafc;
      }

      &.is-expanded {
        background: #f1f5f9;
      }

      td {
        padding: 10px 8px;
        border-bottom: 1px solid #f1f5f9;
        text-align: center;
        vertical-align: middle;
      }
    }

    .module-title {
      display: flex;
      align-items: center;
      gap: 10px;
      padding-left: 24px;
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;

      .mod-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #64748b;
      }
    }

    .cell-center {
      display: flex;
      align-items: center;
      justify-content: center;

      ::ng-deep .mdc-checkbox {
        padding: 2px;
      }
    }

    .cell-na {
      color: #cbd5e1;
      font-size: 13px;
      user-select: none;
    }

    .btn-tune {
      color: #64748b;

      &:hover, &.active {
        color: #2563eb;
        background: #eff6ff;
      }

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }

    .row-advanced td {
      background: #f8fafc;
      padding: 16px 24px 16px 40px !important;
      border-bottom: 1px solid #e2e8f0;
    }

    .advanced-container {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 16px;
    }

    .adv-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #2563eb;
      margin-bottom: 12px;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
    }

    .adv-grid-wrapper {
      display: flex;
      gap: 20px;
      flex-wrap: wrap;
    }

    .adv-box {
      flex: 1;
      min-width: 200px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 12px;

      h5 {
        margin: 0 0 8px;
        font-size: 11px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #475569;
      }
    }

    .adv-options {
      display: flex;
      flex-direction: column;
      gap: 4px;

      label {
        font-size: 13px;
        color: #334155;
      }

      .hint {
        color: #94a3b8;
        font-size: 12px;
      }
    }
  `]
})
export class EntitlementManagementComponent implements OnInit {
  categories: EmployeeCategoryOption[] = [];
  modules: ModuleRegistryEntry[] = [];
  groups: ModuleGroup[] = [];
  standardOps = STANDARD_OPS;
  opMeta = OP_META;

  selectedCategoryId: string | null = null;
  matrix: { [moduleKey: string]: ModulePermission } = {};
  advancedOpen: { [moduleKey: string]: boolean } = {};

  loading = false;
  saving = false;

  get selectedCategoryName(): string {
    return this.categories.find((c) => c.id === this.selectedCategoryId)?.name ?? '';
  }

  get grantedCount(): number {
    let count = 0;
    for (const mod of this.modules) {
      const perm = this.matrix[mod.moduleKey];
      if (!perm) continue;
      for (const op of STANDARD_OPS) {
        if (mod.operations.includes(op) && perm[op]) count++;
      }
      Object.values(perm.actions || {}).forEach((v) => { if (v) count++; });
      Object.values(perm.dashboardCards || {}).forEach((v) => { if (v) count++; });
      Object.values(perm.fields || {}).forEach((f) => { if (f?.visible) count++; });
    }
    return count;
  }

  getModuleIcon(moduleKey: string): string {
    if (!moduleKey) return 'folder_open';
    const key = moduleKey.toUpperCase();
    if (key.includes('DASHBOARD')) return 'dashboard';
    if (key.includes('CLIENT') || key.includes('CUSTOMER')) return 'people';
    if (key.includes('SMALL_CLIENT')) return 'storefront';
    if (key.includes('INVOICE') || key.includes('BILLING') || key.includes('CASH')) return 'receipt_long';
    if (key.includes('ACCOUNTING')) return 'account_balance';
    if (key.includes('INVENTORY') || key.includes('ITEM')) return 'inventory_2';
    if (key.includes('MASTER') || key.includes('CATEGORY')) return 'category';
    if (key.includes('SETTING') || key.includes('ENTITLEMENT') || key.includes('ACCESS')) return 'admin_panel_settings';
    if (key.includes('COURIER') || key.includes('AWB') || key.includes('QUOTATION')) return 'local_shipping';
    if (key.includes('EMPLOYEE')) return 'badge';
    return 'apps';
  }

  constructor(
    private apiService: ApiService,
    private entitlementService: EntitlementService,
    private toast: ToastService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loading = true;
    forkJoin({
      categories: this.apiService.get<Array<{ id: string; name: string; active?: boolean }>>(
        '/master-data/type/EMPLOYEE_CATEGORY'
      ),
      modules: this.entitlementService.getModuleRegistry()
    }).subscribe({
      next: ({ categories, modules }) => {
        this.categories = (categories || [])
          .filter((c) => c.active !== false && !!c.id)
          .map((c) => ({ id: c.id, name: (c as any).role || c.name }))
          .sort((a, b) => a.name.localeCompare(b.name));
        this.modules = (modules || []).sort((a, b) => a.sortOrder - b.sortOrder);
        this.buildGroups();
        this.loading = false;

        const preselect = this.route.snapshot.queryParamMap.get('categoryId');
        if (preselect && this.categories.some((c) => c.id === preselect)) {
          this.selectedCategoryId = preselect;
          this.onCategoryChange();
        }
      },
      error: () => {
        this.toast.error('Error', 'Failed to load entitlement data');
        this.loading = false;
      }
    });
  }

  private buildGroups(): void {
    const map = new Map<string, ModuleRegistryEntry[]>();
    for (const mod of this.modules) {
      const parent = mod.parent || mod.moduleKey;
      if (!map.has(parent)) {
        map.set(parent, []);
      }
      map.get(parent)!.push(mod);
    }
    this.groups = Array.from(map.entries()).map(([parent, mods]) => ({
      parent,
      modules: mods,
      expanded: true
    }));
  }

  onCategoryChange(): void {
    if (!this.selectedCategoryId) {
      return;
    }
    this.loading = true;
    this.entitlementService.getByCategory(this.selectedCategoryId).subscribe({
      next: (ent) => {
        this.matrix = this.normalizeMatrix(ent?.modulePermissions || {});
        this.loading = false;
      },
      error: () => {
        this.matrix = this.normalizeMatrix({});
        this.loading = false;
      }
    });
  }

  /** Ensure every registered module has a permission object so the UI binds cleanly. */
  private normalizeMatrix(existing: { [k: string]: ModulePermission }): { [k: string]: ModulePermission } {
    const result: { [k: string]: ModulePermission } = {};
    for (const mod of this.modules) {
      const e = existing[mod.moduleKey];
      result[mod.moduleKey] = {
        view: !!e?.view,
        create: !!e?.create,
        edit: !!e?.edit,
        delete: !!e?.delete,
        export: !!e?.export,
        print: !!e?.print,
        email: !!e?.email,
        download: !!e?.download,
        actions: e?.actions ? { ...e.actions } : {},
        fields: e?.fields ? { ...e.fields } : {},
        dashboardCards: e?.dashboardCards ? { ...e.dashboardCards } : {}
      };
    }
    return result;
  }

  getPerm(moduleKey: string): ModulePermission {
    return this.matrix[moduleKey];
  }

  isOpChecked(moduleKey: string, op: StandardAction): boolean {
    return !!this.matrix[moduleKey]?.[op];
  }

  toggleOp(moduleKey: string, op: StandardAction, event: MatCheckboxChange | any): void {
    const checked = event && typeof event === 'object' && 'checked' in event ? event.checked : (event?.target as any)?.checked;
    this.matrix[moduleKey][op] = !!checked;
  }

  // --- Advanced (actions / fields / cards) ---
  hasAdvanced(mod: ModuleRegistryEntry): boolean {
    return !!(mod.actions?.length || mod.fields?.length || mod.dashboardCards?.length);
  }

  toggleAdvanced(moduleKey: string): void {
    this.advancedOpen[moduleKey] = !this.advancedOpen[moduleKey];
  }

  getAction(moduleKey: string, key: string): boolean {
    return !!this.matrix[moduleKey].actions?.[key];
  }

  toggleAction(moduleKey: string, key: string, event: MatCheckboxChange | any): void {
    const checked = event && typeof event === 'object' && 'checked' in event ? event.checked : (event?.target as any)?.checked;
    const perm = this.matrix[moduleKey];
    perm.actions = perm.actions || {};
    perm.actions[key] = !!checked;
  }

  getCard(moduleKey: string, key: string): boolean {
    return !!this.matrix[moduleKey].dashboardCards?.[key];
  }

  toggleCard(moduleKey: string, key: string, event: MatCheckboxChange | any): void {
    const checked = event && typeof event === 'object' && 'checked' in event ? event.checked : (event?.target as any)?.checked;
    const perm = this.matrix[moduleKey];
    perm.dashboardCards = perm.dashboardCards || {};
    perm.dashboardCards[key] = !!checked;
  }

  getFieldVisible(moduleKey: string, key: string): boolean {
    const field = this.matrix[moduleKey].fields?.[key];
    return field ? field.visible : false;
  }

  toggleField(moduleKey: string, key: string, event: MatCheckboxChange | any): void {
    const checked = event && typeof event === 'object' && 'checked' in event ? event.checked : (event?.target as any)?.checked;
    const perm = this.matrix[moduleKey];
    perm.fields = perm.fields || {};
    perm.fields[key] = { visible: !!checked, editable: !!checked };
  }

  // --- Group-level helpers ---
  groupHasOp(group: ModuleGroup, op: StandardAction): boolean {
    return group.modules.some((m) => m.operations.includes(op));
  }

  isGroupOpChecked(group: ModuleGroup, op: StandardAction): boolean {
    const applicable = group.modules.filter((m) => m.operations.includes(op));
    return applicable.length > 0 && applicable.every((m) => this.matrix[m.moduleKey][op]);
  }

  isGroupOpIndeterminate(group: ModuleGroup, op: StandardAction): boolean {
    const applicable = group.modules.filter((m) => m.operations.includes(op));
    if (applicable.length === 0) return false;
    const checkedCount = applicable.filter((m) => this.matrix[m.moduleKey][op]).length;
    return checkedCount > 0 && checkedCount < applicable.length;
  }

  toggleGroupOp(group: ModuleGroup, op: StandardAction, event: MatCheckboxChange): void {
    const checked = event.checked;
    group.modules
      .filter((m) => m.operations.includes(op))
      .forEach((m) => (this.matrix[m.moduleKey][op] = checked));
  }

  selectAll(value: boolean): void {
    for (const mod of this.modules) {
      const perm = this.matrix[mod.moduleKey];
      for (const op of STANDARD_OPS) {
        if (mod.operations.includes(op)) {
          perm[op] = value;
        }
      }
      (mod.actions || []).forEach((a) => {
        perm.actions = perm.actions || {};
        perm.actions[a.key] = value;
      });
      (mod.dashboardCards || []).forEach((c) => {
        perm.dashboardCards = perm.dashboardCards || {};
        perm.dashboardCards[c.key] = value;
      });
      (mod.fields || []).forEach((f) => {
        perm.fields = perm.fields || {};
        perm.fields[f.key] = { visible: value, editable: value };
      });
    }
  }

  save(): void {
    if (!this.selectedCategoryId) {
      return;
    }
    this.saving = true;
    const payload: Entitlement = {
      categoryId: this.selectedCategoryId,
      modulePermissions: this.matrix
    };
    this.entitlementService.save(payload).subscribe({
      next: () => {
        this.toast.success('Success', 'Permissions saved');
        this.saving = false;
      },
      error: (err) => {
        this.toast.error('Error', err?.error?.message || 'Failed to save permissions');
        this.saving = false;
      }
    });
  }
}
