import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from './api.service';

export interface UiConfig {
  collectionYearRangePast: number;
  collectionYearRangeFuture: number;
}

@Injectable({ providedIn: 'root' })
export class UiConfigService {
  constructor(private api: ApiService) {}

  getUi(): Observable<UiConfig> {
    return this.api.get<UiConfig>('/config/ui').pipe(
      map((c) => ({
        collectionYearRangePast: Number(c?.collectionYearRangePast ?? 5),
        collectionYearRangeFuture: Number(c?.collectionYearRangeFuture ?? 5)
      }))
    );
  }
}
