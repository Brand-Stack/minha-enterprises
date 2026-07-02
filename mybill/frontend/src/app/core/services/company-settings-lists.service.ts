import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ApiService } from './api.service';

/** Courier / item / status strings and email from {@code GET /company-settings}. */
export interface CompanySettingsLists {
  couriers: string[];
  items: string[];
  statuses: string[];
  email: string;
}

@Injectable({ providedIn: 'root' })
export class CompanySettingsListsService {
  constructor(private api: ApiService) {}

  getLists(): Observable<CompanySettingsLists> {
    return this.api.get<any>('/company-settings').pipe(
      map((s) => ({
        couriers: Array.isArray(s?.couriers) ? s.couriers : [],
        items: Array.isArray(s?.items) ? s.items : [],
        statuses: Array.isArray(s?.statuses) ? s.statuses : [],
        email: typeof s?.email === 'string' ? s.email : ''
      })),
      catchError(() =>
        of({
          couriers: [] as string[],
          items: [] as string[],
          statuses: [] as string[],
          email: ''
        })
      )
    );
  }
}
