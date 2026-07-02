import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';
import { PermissionGuard } from './core/guards/permission.guard';
import { AccessDeniedComponent } from './modules/shared/access-denied/access-denied.component';
import { Modules } from './core/models/permission.model';

const routes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadChildren: () => import('./modules/auth/auth.module').then(m => m.AuthModule)
  },
  {
    path: '403',
    component: AccessDeniedComponent
  },
  {
    path: 'dashboard',
    loadChildren: () => import('./modules/dashboard/dashboard.module').then(m => m.DashboardModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'clients',
    loadChildren: () => import('./modules/master/party/party.module').then(m => m.PartyModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.CLIENTS, action: 'view' } }
  },
  {
    path: 'small-clients',
    loadChildren: () => import('./modules/master/small-client/small-client.module').then(m => m.SmallClientModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.SMALL_CLIENTS, action: 'view' } }
  },
  {
    path: 'small-client-entries',
    loadChildren: () => import('./modules/master/small-client-entry/small-client-entry.module').then(m => m.SmallClientEntryModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.SMALL_CLIENT_ENTRY, action: 'view' } }
  },
  {
    path: 'collection-customer',
    loadChildren: () => import('./modules/master/collection-customer/collection-customer.module').then(m => m.CollectionCustomerModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.COLLECTION_CUSTOMER, action: 'view' } }
  },
  {
    path: 'cash-booking',
    loadChildren: () => import('./modules/master/cash-booking/cash-booking.module').then((m) => m.CashBookingModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.CASH_BOOKING, action: 'view' } }
  },
  {
    path: 'parties',
    redirectTo: 'clients',
    pathMatch: 'full'
  },
  {
    path: 'master/items',
    loadChildren: () => import('./modules/master/items/items.module').then(m => m.ItemsModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.ITEMS, action: 'view' } }
  },
  {
    path: 'master/awb-center',
    loadChildren: () => import('./modules/master/awb-center/awb-center.module').then(m => m.AwbCenterModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.AWB_CENTER, action: 'view' } }
  },
  {
    path: 'master/billing',
    loadChildren: () => import('./modules/master/billing/billing.module').then(m => m.BillingModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.BILLING, action: 'view' } }
  },
  {
    path: 'master/master-data',
    loadChildren: () => import('./modules/master/master-data/master-data.module').then(m => m.MasterDataModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.MASTER_DATA, action: 'view' } }
  },
  {
    path: 'master/zone-configurations',
    loadChildren: () => import('./modules/master/zone-configuration/zone-configuration.module').then(m => m.ZoneConfigurationModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.ZONE_CONFIG, action: 'view' } }
  },
  {
    path: 'quotations',
    loadChildren: () => import('./modules/master/quotation/quotation.module').then(m => m.QuotationModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.QUOTATION, action: 'view' } }
  },
  {
    path: 'courier-quotations',
    loadChildren: () => import('./modules/master/courier-quotation/courier-quotation.module').then(m => m.CourierQuotationModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.COURIER_QUOTATION, action: 'view' } }
  },
  {
    path: 'client-entries',
    loadChildren: () => import('./modules/master/monthly-courier-quotation/monthly-courier-quotation.module').then(m => m.MonthlyCourierQuotationModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.CLIENT_ENTRY, action: 'view' } }
  },
  {
    path: 'monthly-courier-quotations',
    loadChildren: () => import('./modules/master/monthly-courier-quotation/monthly-courier-quotation.module').then(m => m.MonthlyCourierQuotationModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.CLIENT_ENTRY, action: 'view' } }
  },
  // Legacy routes - redirect to new MASTER routes
  {
    path: 'items',
    redirectTo: '/master/items',
    pathMatch: 'full'
  },
  {
    path: 'invoices',
    redirectTo: '/master/billing',
    pathMatch: 'full'
  },
  {
    path: 'employees',
    loadChildren: () => import('./modules/employee/employee.module').then(m => m.EmployeeModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.EMPLOYEES, action: 'view' } }
  },
  {
    path: 'reports',
    loadChildren: () => import('./modules/reports/reports.module').then(m => m.ReportsModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.REPORTS, action: 'view' } }
  },
  {
    path: 'settings',
    loadChildren: () => import('./modules/settings/settings.module').then(m => m.SettingsModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.SETTINGS, action: 'view' } }
  },
  {
    path: 'profile',
    loadChildren: () => import('./modules/profile/profile.module').then(m => m.ProfileModule),
    canActivate: [AuthGuard]
  },
  {
    path: 'purchase-expense',
    loadChildren: () => import('./modules/purchase-expense/purchase-expense.module').then(m => m.PurchaseExpenseModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.PURCHASE_BILLS, action: 'view' } }
  },
  {
    path: 'accounting',
    loadChildren: () => import('./modules/accounting/accounting.module').then((m) => m.AccountingModule),
    canActivate: [AuthGuard, PermissionGuard],
    data: { permission: { module: Modules.ACCOUNTING, action: 'view' } }
  },
  {
    path: '**',
    redirectTo: '/dashboard'
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
