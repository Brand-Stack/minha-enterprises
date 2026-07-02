import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ZoneConfiguration } from '../models/zone-configuration.model';
import { PageResponse } from './api.service';

@Injectable({
    providedIn: 'root'
})
export class ZoneConfigurationService {
    private apiUrl: string;

    constructor(private http: HttpClient) {
        // Use same hostname as page to avoid Private Network Access blocking (localhost vs LAN IP)
        this.apiUrl = `${this.getApiUrl()}/zone-configurations`;
    }

    private getApiUrl(): string {
        if (typeof window !== 'undefined' && window.location) {
            const protocol = window.location.protocol;
            const hostname = window.location.hostname;
            
            if (protocol === 'https:') {
                // Option A: Reverse Proxy (Nginx) proxying /api requests to port 1104 (Recommended)
                return `https://${hostname}/api`;
                
                // Option B: Spring Boot directly running SSL on port 1104
                // To use Option B, uncomment the line below and comment out Option A above:
                // return `https://${hostname}:1104/api`;
            }
            
            // Local development or HTTP access
            return `http://${hostname}:1104/api`;
        }
        return environment.apiUrl;
    }

    getAll(page: number = 0, size: number = 10, sortBy: string = 'zoneName', sortDir: string = 'asc'): Observable<PageResponse<ZoneConfiguration>> {
        let params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString())
            .set('sortBy', sortBy)
            .set('sortDir', sortDir);
        return this.http.get<PageResponse<ZoneConfiguration>>(this.apiUrl, { params });
    }

    getActiveZones(): Observable<ZoneConfiguration[]> {
        return this.http.get<ZoneConfiguration[]>(`${this.apiUrl}/active`);
    }

    /** Get active zones filtered by quotation rate type (EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass). */
    getActiveZonesByRateType(rateType: string): Observable<ZoneConfiguration[]> {
        const params = new HttpParams().set('rateType', rateType);
        return this.http.get<ZoneConfiguration[]>(`${this.apiUrl}/active`, { params });
    }

    search(searchTerm: string, page: number = 0, size: number = 10, sortBy: string = 'zoneName', sortDir: string = 'asc'): Observable<PageResponse<ZoneConfiguration>> {
        let params = new HttpParams()
            .set('searchTerm', searchTerm)
            .set('page', page.toString())
            .set('size', size.toString())
            .set('sortBy', sortBy)
            .set('sortDir', sortDir);
        return this.http.get<PageResponse<ZoneConfiguration>>(`${this.apiUrl}/search`, { params });
    }

    getById(id: string): Observable<ZoneConfiguration> {
        return this.http.get<ZoneConfiguration>(`${this.apiUrl}/${id}`);
    }

    create(zoneConfig: ZoneConfiguration): Observable<ZoneConfiguration> {
        return this.http.post<ZoneConfiguration>(this.apiUrl, zoneConfig);
    }

    update(id: string, zoneConfig: ZoneConfiguration): Observable<ZoneConfiguration> {
        return this.http.put<ZoneConfiguration>(`${this.apiUrl}/${id}`, zoneConfig);
    }

    delete(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}
