import { ZoneRateConfig } from './zone-configuration.model';

export interface OnboardSlab {
  slabId: string;
  slabName: string;
  selected: boolean;
  zoneRates: ZoneRateConfig[];
}

export interface OnboardQuotation {
  id?: string;
  quotationNumber?: string;
  customerName: string;
  branchName?: string;
  effectiveDate: string;
  validTillDate: string;
  remarks?: string;
  status?: 'DRAFT' | 'APPROVED' | 'ACTIVE' | 'EXPIRED';
  fuelChargePercentage?: number;
  fovCharges?: number;
  selectedBankAccountId?: string;
  slabs: OnboardSlab[];
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}
