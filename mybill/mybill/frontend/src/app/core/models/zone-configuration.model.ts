export interface ZoneConfiguration {
    id?: string;
    zoneName: string;
    zoneType: string;

    expressBaseWeight: number;
    expressIncrementalWeight: number;
    expressPerKgThreshold: number;

    surfaceSlab1Threshold: number;
    surfaceSlab1Max: number;
    surfaceSlab2Threshold: number;
    surfaceSlab2Max: number;

    isActive?: boolean;
    createdAt?: string;
    updatedAt?: string;
    lastUpdatedBy?: string;
}

export interface ZoneRateConfig {
    zoneId: string;
    zoneName?: string;
    expressBaseRate?: number;
    expressIncrementalRate?: number;
    expressPerKgRate?: number;
    surfaceSlab1Rate?: number;
    surfaceSlab2Rate?: number;
    standardBaseRate3Kg?: number;
    standardAdditionalPerKg?: number;
    standardRate1Kg?: number;
    standardRate2Kg?: number;
    standardRate3Kg?: number;
    standardRate4Kg?: number;
    standardRate5Kg?: number;
    standardPerKgAbove3?: number;
}
