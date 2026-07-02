import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ApiService } from './api.service';

/** External India pincode API (browser call bypasses server-side Zscaler in many setups). */
const POSTAL_PINCODE_API = 'https://api.postalpincode.in/pincode/';

/** Normalized outcome from {@link PincodeLookupService#lookup}. */
export type PincodeLookupOutcome =
  | {
      kind: 'success';
      state: string;
      areaNames: string[];
      districtByAreaName: Record<string, string>;
      /** Present when every office maps to the same district (typical “city”). */
      uniformDistrict: string;
    }
  | { kind: 'invalid' }
  | { kind: 'unavailable' }
  | { kind: 'httpError' };

@Injectable({ providedIn: 'root' })
export class PincodeLookupService {
  constructor(
    private api: ApiService,
    private http: HttpClient
  ) {}

  /**
   * Resolves pincode details: tries the public API from the browser first (avoids server Zscaler block),
   * then falls back to {@code GET /pincode/{pincode}} on the billing backend.
   */
  lookup(pincode: string): Observable<PincodeLookupOutcome> {
    const trimmed = (pincode || '').trim();
    if (!/^\d{6}$/.test(trimmed)) {
      return of({ kind: 'invalid' as const });
    }
    return this.lookupViaBrowser(trimmed).pipe(
      switchMap((browser) =>
        browser.kind === 'success' ? of(browser) : this.lookupViaBackend(trimmed)
      )
    );
  }

  /** Direct call to postalpincode.in from the user's browser. */
  private lookupViaBrowser(pincode: string): Observable<PincodeLookupOutcome> {
    return this.http.get<unknown>(`${POSTAL_PINCODE_API}${pincode}`).pipe(
      map((body) => this.normalizePostalPincodeArray(body)),
      catchError(() => of({ kind: 'unavailable' as const }))
    );
  }

  private lookupViaBackend(pincode: string): Observable<PincodeLookupOutcome> {
    return this.api.get<any>(`/pincode/${pincode}`).pipe(
      map((res) => this.normalizeResponse(res)),
      catchError(() => of({ kind: 'unavailable' as const }))
    );
  }

  /** Parses postalpincode.in array JSON (browser direct response). */
  normalizePostalPincodeArray(body: unknown): PincodeLookupOutcome {
    if (!Array.isArray(body) || body.length === 0) {
      return { kind: 'invalid' };
    }
    const first = body[0] as { Status?: string; PostOffice?: { Name?: string; District?: string; State?: string }[] };
    if (String(first?.Status || '').toLowerCase() !== 'success') {
      return { kind: 'invalid' };
    }
    const offices = Array.isArray(first.PostOffice) ? first.PostOffice : [];
    if (!offices.length) {
      return { kind: 'invalid' };
    }
    const state = String(offices[0]?.State || '').trim();
    const districtByAreaName: Record<string, string> = {};
    for (const o of offices) {
      const n = String(o?.Name || '').trim();
      if (!n || districtByAreaName[n] !== undefined) {
        continue;
      }
      districtByAreaName[n] = String(o?.District || '').trim();
    }
    const areaNames = Object.keys(districtByAreaName);
    const districts = [...new Set(Object.values(districtByAreaName).filter(Boolean))];
    const uniformDistrict = districts.length === 1 ? districts[0] : '';
    return {
      kind: 'success',
      state,
      areaNames,
      districtByAreaName,
      uniformDistrict
    };
  }

  /** Parses billing backend {@code PincodeLookupResponseDto}. */
  normalizeResponse(res: any): PincodeLookupOutcome {
    if (res?.message === 'PINCODE_PROXY_BLOCKED') {
      return { kind: 'unavailable' };
    }
    if (!res?.success || !Array.isArray(res.areas) || res.areas.length === 0) {
      const msg = String(res?.message || '').toLowerCase();
      if (
        msg.includes('fetch') ||
        msg.includes('unavailable') ||
        msg.includes('could not') ||
        msg.includes('enter manually') ||
        msg.includes('proxy')
      ) {
        return { kind: 'unavailable' };
      }
      return { kind: 'invalid' };
    }
    const raw = res.areas as { name?: string; district?: string; state?: string }[];
    const state = String(raw[0].state || '').trim();
    const districtByAreaName: Record<string, string> = {};
    for (const a of raw) {
      const n = String(a.name || '').trim();
      if (!n) {
        continue;
      }
      if (districtByAreaName[n] === undefined) {
        districtByAreaName[n] = String(a.district || '').trim();
      }
    }
    const areaNames = Array.from(
      new Set(raw.map((a) => String(a.name || '').trim()).filter((n) => !!n))
    );
    const districts = [...new Set(Object.values(districtByAreaName).filter(Boolean))];
    const uniformDistrict = districts.length === 1 ? districts[0] : '';
    return {
      kind: 'success',
      state,
      areaNames,
      districtByAreaName,
      uniformDistrict
    };
  }

  isUnavailable(out: PincodeLookupOutcome): boolean {
    return out.kind === 'unavailable' || out.kind === 'httpError';
  }
}
