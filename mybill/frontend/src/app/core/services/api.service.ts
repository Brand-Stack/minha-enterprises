import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private apiUrl: string;

  constructor(private http: HttpClient) {
    // Dynamically determine API URL based on current hostname
    // This allows the app to work when accessed via server IP
    this.apiUrl = this.getApiUrl();
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

  get<T>(url: string, params?: any, options?: any): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.keys(params).forEach(key => {
        const v = params[key];
        if (v !== null && v !== undefined && v !== '') {
          httpParams = httpParams.set(key, v.toString());
        }
      });
    }

    // If responseType is blob, handle separately with type assertion
    if (options && options.responseType === 'blob') {
      const requestOptions: any = { params: httpParams, ...options };
      return this.http.get(`${this.apiUrl}${url}`, requestOptions) as any as Observable<T>;
    }

    // For normal requests, only include safe options that don't change response type
    const requestOptions: any = { params: httpParams };
    if (options) {
      // Only copy safe options that don't affect response type inference
      if (options.headers) {
        requestOptions.headers = options.headers;
      }
      if (options.responseType && options.responseType !== 'blob') {
        requestOptions.responseType = options.responseType;
      }
    }

    // Use type assertion to fix TypeScript inference issue
    return this.http.get<T>(`${this.apiUrl}${url}`, requestOptions) as Observable<T>;
  }

  post<T>(url: string, body: any): Observable<T> {
    return this.http.post<T>(`${this.apiUrl}${url}`, body);
  }

  put<T>(url: string, id: string, body: any): Observable<T> {
    return this.http.put<T>(`${this.apiUrl}${url}/${id}`, body);
  }

  patch<T>(url: string, body: any): Observable<T> {
    return this.http.patch<T>(`${this.apiUrl}${url}`, body);
  }

  delete<T>(url: string, id: string): Observable<T> {
    return this.http.delete<T>(`${this.apiUrl}${url}/${id}`);
  }

  /** DELETE relative to API root, e.g. `/collection-customers/{cid}/awbs/{rid}` */
  deletePath(path: string): Observable<void> {
    const p = path.startsWith('/') ? path : `/${path}`;
    return this.http.delete<void>(`${this.apiUrl}${p}`);
  }

  getPaged<T>(url: string, page: number = 0, size: number = 10, sortBy: string = 'createdAt', sortDir: string = 'desc'): Observable<PageResponse<T>> {
    return this.get<PageResponse<T>>(url, { page, size, sortBy, sortDir });
  }

  search<T>(url: string, searchTerm: string, page: number = 0, size: number = 10, sortBy: string = 'createdAt', sortDir: string = 'desc'): Observable<PageResponse<T>> {
    return this.get<PageResponse<T>>(`${url}/search`, { searchTerm, page, size, sortBy, sortDir });
  }

  getBaseUrl(): string {
    return this.apiUrl;
  }
}
