import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ModulePermission } from '../models/permission.model';

export interface ModuleActionMeta {
  key: string;
  displayName: string;
}

export interface ModuleRegistryEntry {
  id?: string;
  moduleKey: string;
  displayName: string;
  parent?: string;
  sortOrder: number;
  operations: string[];
  actions: ModuleActionMeta[];
  fields: ModuleActionMeta[];
  dashboardCards: ModuleActionMeta[];
  dashboard: boolean;
}

export interface Entitlement {
  id?: string;
  categoryId: string;
  categoryName?: string;
  modulePermissions: { [moduleKey: string]: ModulePermission };
}

@Injectable({
  providedIn: 'root'
})
export class EntitlementService {
  constructor(private api: ApiService, private http: HttpClient) {}

  getModuleRegistry(): Observable<ModuleRegistryEntry[]> {
    return this.api.get<ModuleRegistryEntry[]>('/module-registry');
  }

  getByCategory(categoryId: string): Observable<Entitlement> {
    return this.api.get<Entitlement>(`/entitlements/category/${categoryId}`);
  }

  save(entitlement: Entitlement): Observable<Entitlement> {
    // Backend exposes PUT /entitlements (no id in path).
    return this.http.put<Entitlement>(`${this.api.getBaseUrl()}/entitlements`, entitlement);
  }
}
