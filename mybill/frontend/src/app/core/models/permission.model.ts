/**
 * Mirrors the backend {@code FieldPermission} value object.
 */
export interface FieldPermission {
  visible: boolean;
  editable: boolean;
}

/**
 * Mirrors the backend {@code ModulePermission} value object returned in the
 * login response (`AuthResponse.permissions`).
 */
export interface ModulePermission {
  view: boolean;
  create: boolean;
  edit: boolean;
  delete: boolean;
  export: boolean;
  print: boolean;
  email: boolean;
  download: boolean;
  actions?: { [actionKey: string]: boolean };
  fields?: { [fieldKey: string]: FieldPermission };
  dashboardCards?: { [cardKey: string]: boolean };
}

/** Standard CRUD-style actions resolved directly from {@link ModulePermission} flags. */
export type StandardAction =
  | 'view'
  | 'create'
  | 'edit'
  | 'delete'
  | 'export'
  | 'print'
  | 'email'
  | 'download';

/** Module key constants — kept in sync with the backend {@code Modules} class. */
export const Modules = {
  DASHBOARD: 'DASHBOARD',
  CLIENTS: 'CLIENTS',
  SMALL_CLIENTS: 'SMALL_CLIENTS',
  COLLECTION_CUSTOMER: 'COLLECTION_CUSTOMER',
  CASH_BOOKING: 'CASH_BOOKING',
  ITEMS: 'ITEMS',
  AWB_CENTER: 'AWB_CENTER',
  MASTER_DATA: 'MASTER_DATA',
  ZONE_CONFIG: 'ZONE_CONFIG',
  COURIER_QUOTATION: 'COURIER_QUOTATION',
  QUOTATION: 'QUOTATION',
  CLIENT_ENTRY: 'CLIENT_ENTRY',
  SMALL_CLIENT_ENTRY: 'SMALL_CLIENT_ENTRY',
  COLLECTION_CENTER: 'COLLECTION_CENTER',
  BILLING: 'BILLING',
  INVOICE: 'INVOICE',
  ACCOUNTING: 'ACCOUNTING',
  PURCHASE_BILLS: 'PURCHASE_BILLS',
  PAYMENT_OUT: 'PAYMENT_OUT',
  EXPENSES: 'EXPENSES',
  CASH_IN_HAND: 'CASH_IN_HAND',
  EMPLOYEES: 'EMPLOYEES',
  REPORTS: 'REPORTS',
  SETTINGS: 'SETTINGS',
  ACCESS_CONTROL: 'ACCESS_CONTROL',
  ENTITLEMENT_MGMT: 'ENTITLEMENT_MGMT'
} as const;
