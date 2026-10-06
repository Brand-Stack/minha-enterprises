import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { SmallClientEntryQuotation, SmallClientEntry } from '../models/small-client-entry.model';

@Injectable({
    providedIn: 'root'
})
export class SmallClientEntryQuotationService {
    private basePath = '/small-client-entries';

    constructor(private apiService: ApiService) { }

    getAll(page: number = 0, size: number = 10, filters?: {
        search?: string;
        title?: string;
        customerId?: string;
        zone?: string;
        fromDate?: string;
        toDate?: string;
        month?: string;
        year?: number;
        trackingNumber?: string;
        amountStatus?: string;
        isDownloaded?: boolean;
        invoiceNumber?: string;
        customerName?: string;
        invoiceMonth?: number;
        invoiceYear?: number;
        invoiceDateFrom?: string;
        invoiceDateTo?: string;
        description?: string;
    }): Observable<any> {
        let params: any = {
            page: page.toString(),
            size: size.toString()
        };
        if (filters) {
            const f = filters;
            if (f.search?.trim()) params.search = f.search.trim();
            if (f.title?.trim()) params.title = f.title.trim();
            if (f.customerId?.trim()) params.customerId = f.customerId.trim();
            if (f.zone?.trim()) params.zone = f.zone.trim();
            if (f.fromDate?.trim()) params.fromDate = f.fromDate.trim();
            if (f.toDate?.trim()) params.toDate = f.toDate.trim();
            if (f.month?.trim()) params.month = f.month.trim();
            if (f.year != null && f.year !== undefined && !Number.isNaN(f.year)) params.year = String(f.year);
            if (f.trackingNumber?.trim()) params.trackingNumber = f.trackingNumber.trim();
            if (f.amountStatus?.trim()) params.amountStatus = f.amountStatus.trim();
            if (f.isDownloaded === true) params.isDownloaded = 'true';
            if (f.invoiceNumber?.trim()) params.invoiceNumber = f.invoiceNumber.trim();
            if (f.customerName?.trim()) params.customerName = f.customerName.trim();
            if (f.invoiceMonth != null && !Number.isNaN(f.invoiceMonth)) params.invoiceMonth = String(f.invoiceMonth);
            if (f.invoiceYear != null && !Number.isNaN(f.invoiceYear)) params.invoiceYear = String(f.invoiceYear);
            if (f.invoiceDateFrom?.trim()) params.invoiceDateFrom = f.invoiceDateFrom.trim();
            if (f.invoiceDateTo?.trim()) params.invoiceDateTo = f.invoiceDateTo.trim();
            if (f.description?.trim()) params.description = f.description.trim();
        }
        return this.apiService.get(this.basePath, params);
    }

    getById(id: string): Observable<SmallClientEntryQuotation> {
        return this.apiService.get<SmallClientEntryQuotation>(`${this.basePath}/${id}`);
    }

    create(data: SmallClientEntryQuotation): Observable<SmallClientEntryQuotation> {
        return this.apiService.post<SmallClientEntryQuotation>(this.basePath, data);
    }

    update(id: string, data: SmallClientEntryQuotation): Observable<SmallClientEntryQuotation> {
        return this.apiService.put<SmallClientEntryQuotation>(this.basePath, id, data);
    }

    delete(id: string): Observable<void> {
        return this.apiService.delete<void>(this.basePath, id);
    }

    generateInvoice(id: string): Observable<SmallClientEntryQuotation> {
        return this.apiService.post<SmallClientEntryQuotation>(`${this.basePath}/${id}/generate-invoice`, {});
    }

    deleteFromReport(id: string): Observable<void> {
        return this.apiService.delete<void>(`${this.basePath}/report`, id);
    }



    getEntries(quotationId: string): Observable<SmallClientEntry[]> {
        return this.apiService.get<SmallClientEntry[]>(`${this.basePath}/${quotationId}/entries`);
    }

    addEntry(quotationId: string, data: SmallClientEntry): Observable<SmallClientEntry> {
        return this.apiService.post<SmallClientEntry>(`${this.basePath}/${quotationId}/entries`, data);
    }

    updateEntry(entryId: string, data: SmallClientEntry): Observable<SmallClientEntry> {
        return this.apiService.put<SmallClientEntry>(`${this.basePath}/entries`, entryId, data);
    }

    deleteEntry(entryId: string): Observable<void> {
        return this.apiService.delete<void>(`${this.basePath}/entries`, entryId);
    }

    calculateAmount(quotationId: string, zone: string, rateType: string, weight: number): Observable<number> {
        const params: any = { zone, weight: weight.toString() };
        if (rateType) params.rateType = rateType;
        return this.apiService.get<number>(`${this.basePath}/${quotationId}/calculate`, params);
    }
}

