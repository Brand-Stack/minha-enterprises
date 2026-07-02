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
      <header class="access-hero">
        <div class="access-hero__icon">
          <mat-icon>admin_panel_settings</mat-icon>
        </div>
        <div>
          <h1>Access Management</h1>
          <p class="access-sub">
            Configure role-based permissions for each employee category.
            Categories are managed under
            <strong>Master &gt; Create Category &gt; Employee Category</strong>.
          </p>
        </div>
      </header>

      <mat-card class="access-toolbar-card">
        <div class="access-toolbar">
          <div class="access-toolbar__left">
            <span class="access-toolbar__label">Role</span>
            <mat-form-field appearance="outline" class="category-select">
              <mat-label>Select employee category</mat-label>
              <mat-icon matPrefix>badge</mat-icon>
              <mat-select [(value)]="selectedCategoryId" (selectionChange)="onCategoryChange()">
                <mat-option *ngFor="let cat of categories" [value]="cat.id">{{ cat.name }}</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="access-toolbar__right" *ngIf="selectedCategoryId">
            <button mat-stroked-button class="action-btn" (click)="selectAll(true)" [disabled]="loading || saving">
              <mat-icon>done_all</mat-icon>
              Select All
            </button>
            <button mat-stroked-button class="action-btn" (click)="selectAll(false)" [disabled]="loading || saving">
              <mat-icon>clear_all</mat-icon>
              Clear All
            </button>
            <button mat-raised-button color="primary" class="save-btn" (click)="save()" [disabled]="saving || loading">
              <mat-icon>{{ saving ? 'hourglass_top' : 'save' }}</mat-icon>
              {{ saving ? 'Saving...' : 'Save Permissions' }}
            </button>
          </div>
        </div>

        <div class="access-stats" *ngIf="selectedCategoryId && !loading">
          <div class="stat-chip">
            <mat-icon>category</mat-icon>
            <span>{{ selectedCategoryName }}</span>
          </div>
          <div class="stat-chip stat-chip--muted">
            <mat-icon>apps</mat-icon>
            <span>{{ modules.length }} modules</span>
          </div>
          <div class="stat-chip stat-chip--success">
            <mat-icon>verified_user</mat-icon>
            <span>{{ grantedCount }} permissions granted</span>
          </div>
        </div>
      </mat-card>

      <div class="access-loading" *ngIf="loading">
        <mat-spinner diameter="40"></mat-spinner>
        <p>Loading permission matrix...</p>
      </div>

      <mat-card class="access-empty" *ngIf="!selectedCategoryId && !loading">
        <mat-icon>touch_app</mat-icon>
        <h3>Select a category to begin</h3>
        <p>Choose an employee category above to view and configure its module permissions.</p>
      </mat-card>

      <mat-card class="matrix-card" *ngIf="selectedCategoryId && !loading">
        <div class="matrix-scroll">
          <table class="matrix-table">
            <thead>
              <tr>
                <th class="module-col">
                  <span class="th-label">Module</span>
                </th>
                <th *ngFor="let op of standardOps" class="op-col">
                  <div class="op-header">
                    <mat-icon>{{ opMeta[op].icon }}</mat-icon>
                    <span>{{ opMeta[op].label }}</span>
                  </div>
                </th>
                <th class="adv-col"></th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let group of groups">
                <tr class="group-row" (click)="group.expanded = !group.expanded">
                  <td class="module-col">
                    <div class="group-label">
                      <mat-icon class="chevron">{{ group.expanded ? 'expand_more' : 'chevron_right' }}</mat-icon>
                      <span class="group-badge">{{ group.parent }}</span>
                      <span class="group-count">{{ group.modules.length }} modules</span>
                    </div>
                  </td>
                  <td *ngFor="let op of standardOps" class="op-col" (click)="$event.stopPropagation()">
                    <mat-checkbox
                      *ngIf="groupHasOp(group, op)"
                      color="primary"
                      class="perm-checkbox perm-checkbox--group"
                      [checked]="isGroupOpChecked(group, op)"
                      [indeterminate]="isGroupOpIndeterminate(group, op)"
                      (change)="toggleGroupOp(group, op, $event)">
                    </mat-checkbox>
                    <span *ngIf="!groupHasOp(group, op)" class="na">—</span>
                  </td>
                  <td class="adv-col"></td>
                </tr>

                <ng-container *ngIf="group.expanded">
                  <ng-container *ngFor="let mod of group.modules">
                    <tr class="module-row">
                      <td class="module-col">
                        <div class="module-label">
                          <mat-icon class="module-icon">folder_open</mat-icon>
                          <span>{{ mod.displayName }}</span>
                        </div>
                      </td>
                      <td *ngFor="let op of standardOps" class="op-col">
                        <mat-checkbox
                          *ngIf="mod.operations.includes(op)"
                          color="primary"
                          class="perm-checkbox"
                          [checked]="isOpChecked(mod.moduleKey, op)"
                          (change)="toggleOp(mod.moduleKey, op, $event)">
                        </mat-checkbox>
                        <span *ngIf="!mod.operations.includes(op)" class="na">—</span>
                      </td>
                      <td class="adv-col">
                        <button mat-icon-button
                                class="adv-btn"
                                *ngIf="hasAdvanced(mod)"
                                (click)="toggleAdvanced(mod.moduleKey)"
                                matTooltip="Actions, fields & dashboard cards">
                          <mat-icon>{{ advancedOpen[mod.moduleKey] ? 'expand_less' : 'tune' }}</mat-icon>
                        </button>
                      </td>
                    </tr>

                    <tr class="advanced-row" *ngIf="advancedOpen[mod.moduleKey] && hasAdvanced(mod)">
                      <td [attr.colspan]="standardOps.length + 2">
                        <div class="advanced-panel">
                          <div class="adv-section" *ngIf="mod.actions?.length">
                            <h4><mat-icon>bolt</mat-icon> Actions</h4>
                            <div class="adv-grid">
                              <label class="adv-item" *ngFor="let a of mod.actions">
                                <mat-checkbox
                                  color="primary"
                                  [checked]="getAction(mod.moduleKey, a.key)"
                                  (change)="toggleAction(mod.moduleKey, a.key, $event)">
                                  {{ a.displayName }}
                                </mat-checkbox>
                              </label>
                            </div>
                          </div>
                          <div class="adv-section" *ngIf="mod.dashboardCards?.length">
                            <h4><mat-icon>dashboard</mat-icon> Dashboard Cards</h4>
                            <div class="adv-grid">
                              <label class="adv-item" *ngFor="let c of mod.dashboardCards">
                                <mat-checkbox
                                  color="primary"
                                  [checked]="getCard(mod.moduleKey, c.key)"
                                  (change)="toggleCard(mod.moduleKey, c.key, $event)">
                                  {{ c.displayName }}
                                </mat-checkbox>
                              </label>
                            </div>
                          </div>
                          <div class="adv-section" *ngIf="mod.fields?.length">
                            <h4><mat-icon>view_column</mat-icon> Fields</h4>
                            <div class="adv-grid">
                              <label class="adv-item" *ngFor="let f of mod.fields">
                                <mat-checkbox
                                  color="primary"
                                  [checked]="getFieldVisible(mod.moduleKey, f.key)"
                                  (change)="toggleField(mod.moduleKey, f.key, $event)">
                                  {{ f.displayName }} <span class="adv-hint">(visible)</span>
                                </mat-checkbox>
                              </label>
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
      </mat-card>
    </div>
  `,
  styles: [`
    .access-page {
      padding: 24px;
      max-width: 1320px;
      margin: 0 auto;
    }

    .access-hero {
      display: flex;
      align-items: flex-start;
      gap: 18px;
      margin-bottom: 24px;
    }

    .access-hero__icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 14px;
      background: linear-gradient(135deg, #4f46e5 0%, #6366f1 50%, #818cf8 100%);
      box-shadow: 0 8px 24px rgba(79, 70, 229, 0.28);
      flex-shrink: 0;

      mat-icon {
        color: #fff;
        font-size: 28px;
        width: 28px;
        height: 28px;
      }
    }

    h1 {
      margin: 0 0 6px;
      font-size: 1.75rem;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
    }

    .access-sub {
      margin: 0;
      color: #64748b;
      font-size: 0.925rem;
      line-height: 1.55;
      max-width: 720px;

      strong { color: #475569; font-weight: 600; }
    }

    .access-toolbar-card {
      padding: 18px 22px !important;
      margin-bottom: 20px;
      border-radius: 14px !important;
      border: 1px solid #e2e8f0;
      box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04) !important;
    }

    .access-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }

    .access-toolbar__left {
      display: flex;
      align-items: center;
      gap: 14px;
      flex: 1;
      min-width: 280px;
    }

    .access-toolbar__label {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      flex-shrink: 0;
    }

    .category-select {
      width: 100%;
      max-width: 340px;
      margin-bottom: -1.25em;
    }

    .access-toolbar__right {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
    }

    .action-btn {
      border-color: #cbd5e1 !important;
      color: #475569 !important;
      font-weight: 500;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        margin-right: 4px;
      }
    }

    .save-btn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      margin-right: 4px;
    }

    .access-stats {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #f1f5f9;
    }

    .stat-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 999px;
      background: #eef2ff;
      color: #4338ca;
      font-size: 13px;
      font-weight: 500;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
      }
    }

    .stat-chip--muted {
      background: #f8fafc;
      color: #64748b;
    }

    .stat-chip--success {
      background: #ecfdf5;
      color: #059669;
    }

    .access-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      padding: 64px 24px;
      color: #64748b;
    }

    .access-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      padding: 56px 32px !important;
      border-radius: 14px !important;
      border: 1px dashed #cbd5e1;
      background: #f8fafc !important;
      box-shadow: none !important;

      mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        color: #94a3b8;
        margin-bottom: 12px;
      }

      h3 {
        margin: 0 0 8px;
        font-size: 1.1rem;
        font-weight: 600;
        color: #334155;
      }

      p {
        margin: 0;
        color: #64748b;
        max-width: 360px;
      }
    }

    .matrix-card {
      padding: 0 !important;
      border-radius: 14px !important;
      border: 1px solid #e2e8f0;
      overflow: hidden;
      box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06) !important;
    }

    .matrix-scroll {
      overflow-x: auto;
    }

    .matrix-table {
      width: 100%;
      border-collapse: separate;
      border-spacing: 0;
      min-width: 900px;
    }

    .matrix-table thead {
      position: sticky;
      top: 0;
      z-index: 2;
    }

    .matrix-table th {
      background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
      border-bottom: 2px solid #e2e8f0;
      padding: 14px 10px;
      text-align: center;
      vertical-align: middle;
    }

    .th-label {
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
    }

    .op-header {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #6366f1;
      }

      span {
        font-size: 10px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #64748b;
      }
    }

    .module-col {
      text-align: left !important;
      min-width: 260px;
      padding-left: 20px !important;
    }

    .op-col {
      text-align: center;
      width: 72px;
      padding: 10px 8px !important;
    }

    .adv-col {
      width: 52px;
      padding-right: 12px !important;
    }

    .group-row {
      background: #fafbff;
      cursor: pointer;
      transition: background 0.15s ease;

      &:hover { background: #f1f5ff; }

      td {
        border-bottom: 1px solid #e2e8f0;
        padding: 12px 8px;
      }
    }

    .group-label {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .chevron {
      font-size: 22px;
      width: 22px;
      height: 22px;
      color: #6366f1;
    }

    .group-badge {
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #4338ca;
      background: #eef2ff;
      padding: 4px 10px;
      border-radius: 6px;
    }

    .group-count {
      font-size: 12px;
      color: #94a3b8;
    }

    .module-row {
      transition: background 0.12s ease;

      &:hover { background: #fefefe; }

      td {
        padding: 10px 8px;
        border-bottom: 1px solid #f1f5f9;
        text-align: center;
      }
    }

    .module-label {
      display: flex;
      align-items: center;
      gap: 10px;
      padding-left: 28px;
      color: #334155;
      font-size: 14px;
      font-weight: 500;
    }

    .module-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #94a3b8;
    }

    .na {
      color: #cbd5e1;
      font-size: 14px;
      user-select: none;
    }

    .perm-checkbox {
      ::ng-deep .mdc-checkbox { padding: 0; }
    }

    .perm-checkbox--group ::ng-deep .mdc-label { display: none; }

    .adv-btn {
      color: #6366f1;

      mat-icon { font-size: 20px; }
    }

    .advanced-row td {
      background: linear-gradient(180deg, #fafbff 0%, #f8fafc 100%);
      padding: 16px 24px 20px 48px !important;
      border-bottom: 1px solid #e2e8f0;
    }

    .advanced-panel {
      display: flex;
      gap: 28px;
      flex-wrap: wrap;
    }

    .adv-section {
      flex: 1;
      min-width: 200px;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 14px 16px;

      h4 {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 11px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #64748b;
        margin: 0 0 12px;

        mat-icon {
          font-size: 16px;
          width: 16px;
          height: 16px;
          color: #6366f1;
        }
      }
    }

    .adv-grid {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .adv-item {
      display: block;
      font-size: 13px;
      color: #475569;
    }

    .adv-hint {
      color: #94a3b8;
      font-size: 12px;
    }

    @media (max-width: 768px) {
      .access-page { padding: 16px; }
      .access-hero { flex-direction: column; gap: 12px; }
      .access-toolbar__left { flex-direction: column; align-items: stretch; }
      .category-select { max-width: none; }
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

  toggleOp(moduleKey: string, op: StandardAction, event: MatCheckboxChange): void {
    this.matrix[moduleKey][op] = event.checked;
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

  toggleAction(moduleKey: string, key: string, event: MatCheckboxChange): void {
    const perm = this.matrix[moduleKey];
    perm.actions = perm.actions || {};
    perm.actions[key] = event.checked;
  }

  getCard(moduleKey: string, key: string): boolean {
    return !!this.matrix[moduleKey].dashboardCards?.[key];
  }

  toggleCard(moduleKey: string, key: string, event: MatCheckboxChange): void {
    const perm = this.matrix[moduleKey];
    perm.dashboardCards = perm.dashboardCards || {};
    perm.dashboardCards[key] = event.checked;
  }

  getFieldVisible(moduleKey: string, key: string): boolean {
    const field = this.matrix[moduleKey].fields?.[key];
    return field ? field.visible : false;
  }

  toggleField(moduleKey: string, key: string, event: MatCheckboxChange): void {
    const checked = event.checked;
    const perm = this.matrix[moduleKey];
    perm.fields = perm.fields || {};
    perm.fields[key] = { visible: checked, editable: checked };
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
