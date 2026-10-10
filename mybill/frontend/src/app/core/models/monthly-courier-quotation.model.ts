export interface MonthlyCourierQuotation {
    id?: string;
    shopId?: string;
    customerId: string;
    customerName?: string;
    title?: string;
    month: string;
    year: number;
    zone?: string;
    totalShipments?: number;
    totalWeight?: number;
    totalAmount?: number;
    baseAmount?: number;
    /** Format: 0001/2026-27 (running number / financial year). Editable. */
    invoiceNumber?: string;
    /** Invoice date; used to derive financial year for auto-increment. Editable. */
    invoiceDate?: string;
    /** Internal notes; UI only, not in invoice or breakup PDF/Excel. */
    note?: string;
    /** Client Entry Report: Pending | Paid | Partial */
    amountStatus?: string;
    /** Client Entry Report description */
    description?: string;
    /** True once monthly invoice PDF has been downloaded (report list). */
    isDownloaded?: boolean;
    /** True once invoice has been explicitly generated. */
    invoiceGenerated?: boolean;
    // Authoritative totals from backend invoice engine
    fuelPercentage?: number;
    fuelAmount?: number;
    fovPercentage?: number;
    fovAmount?: number;
    subTotal?: number;
    taxableAmount?: number;
    gstAmount?: number;
    applyQuotationRates?: boolean;
    fuelChargePercentage?: number | null;
    fovCharges?: number | null;
    gstPercentage?: number | null;
    includeFuel?: boolean;
    includeGst?: boolean;
    includeFov?: boolean;
    discountType?: 'PERCENTAGE' | 'AMOUNT' | string | null;
    discountValue?: number | null;
    discountAmount?: number | null;
    additionalCharges?: number | null;
    discountDescription?: string;
    additionalChargesDescription?: string;
    createdBy?: string;
    createdAt?: string;
    updatedAt?: string;
    lastUpdatedBy?: string;
}

export interface MonthlyCourierEntry {
    id?: string;
    monthlyQuotationId: string;
    entryDate: string;
    consignor: string;
    receiverName?: string;
    receiverPhoneNo?: string;
    pincode?: string;
    areaName?: string;
    state?: string;
    destinationCity?: string;
    fullAddress?: string;
    consigneeAddress: string;
    courierType: string;
    weight: number;
    trackingNumber: string;
    itemType: string;
    deliveryStatus?: string;
    /** Payment / amount status (Cash, GPay, COD, custom). */
    amountStatus?: string;
    zone?: string;
    /** Rate set for calculation: EXPRESS_RATE, SURFACE_RATE, SafetyPlus, PriorityClass */
    rateType?: string;
    rate?: number;
    amount?: number;
    /** True when amount was edited manually (skips auto recalculation on save). */
    amountOverridden?: boolean;
    /** Optional; added to shipment total for this entry. */
    additionalCharges?: number;
    /** Optional description for additional charges (e.g. Handling Charges). */
    additionalChargesDescription?: string;
    /** When false, line excluded from GST in invoice-style total; omit or true = apply. */
    gstApplicable?: boolean;
    /** When false, line excluded from fuel allocation; omit or true = apply. */
    fuelApplicable?: boolean;
    /** When false, line excluded from FOV allocation; omit or true = apply. */
    fovApplicable?: boolean;
    createdBy?: string;
    createdAt?: string;
    updatedAt?: string;
    lastUpdatedBy?: string;
}
