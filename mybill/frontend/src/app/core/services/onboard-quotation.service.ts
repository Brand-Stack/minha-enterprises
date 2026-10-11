import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { OnboardQuotation } from '../models/onboard-quotation.model';

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class OnboardQuotationService {
  private readonly baseUrl = '/onboard-quotations';

  constructor(private api: ApiService, private http: HttpClient) {}

  findAll(
    page: number = 0,
    size: number = 10,
    sortBy: string = 'effectiveDate',
    sortDir: string = 'desc',
    effectiveFrom?: string,
    effectiveTo?: string,
    status?: string,
    searchTerm?: string
  ): Observable<PageResponse<OnboardQuotation>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);

    if (effectiveFrom) params = params.set('effectiveFrom', effectiveFrom);
    if (effectiveTo) params = params.set('effectiveTo', effectiveTo);
    if (status) params = params.set('status', status);
    if (searchTerm) params = params.set('searchTerm', searchTerm);

    return this.api.get<PageResponse<OnboardQuotation>>(this.baseUrl, params);
  }

  findById(id: string): Observable<OnboardQuotation> {
    return this.api.get<OnboardQuotation>(`${this.baseUrl}/${id}`);
  }

  create(quotation: OnboardQuotation): Observable<OnboardQuotation> {
    return this.api.post<OnboardQuotation>(this.baseUrl, quotation);
  }

  update(id: string, quotation: OnboardQuotation): Observable<OnboardQuotation> {
    return this.api.put<OnboardQuotation>(this.baseUrl, id, quotation);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(this.baseUrl, id);
  }

  downloadPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.api.getBaseUrl()}${this.baseUrl}/${id}/pdf`, {
      responseType: 'blob',
      observe: 'body'
    });
  }

  printPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.api.getBaseUrl()}${this.baseUrl}/${id}/print`, {
      responseType: 'blob',
      observe: 'body'
    });
  }

  exportExcel(id: string): Observable<Blob> {
    return this.http.get(`${this.api.getBaseUrl()}${this.baseUrl}/${id}/export/excel`, {
      responseType: 'blob',
      observe: 'body'
    });
  }

  sendEmail(id: string, email: string): Observable<any> {
    return this.api.post(`${this.baseUrl}/${id}/email`, { email });
  }
}
