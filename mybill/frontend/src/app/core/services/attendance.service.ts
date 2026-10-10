import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import {
  AttendanceDevice,
  AttendanceRecord,
  DeviceEmployeeMapping,
  EmployeeBonus,
  LeaveEntitlement,
  LeaveRequest,
  PayrollRecord,
  PermissionRequest,
  SalaryStructure,
  EmployeeAdvanceAccount,
  AdvanceTransaction
} from '../models/attendance.model';

@Injectable({
  providedIn: 'root'
})
export class AttendanceService {

  public leaveChanged$ = new Subject<void>();

  constructor(private apiService: ApiService) {}

  // --- ATTENDANCE ---
  punch(punchType: 'IN' | 'OUT', latitude?: number, longitude?: number, targetEmployeeId?: string): Observable<AttendanceRecord> {
    const payload: any = { punchType };
    if (latitude !== undefined && latitude !== null) payload.latitude = latitude;
    if (longitude !== undefined && longitude !== null) payload.longitude = longitude;
    if (targetEmployeeId) payload.targetEmployeeId = targetEmployeeId;
    return this.apiService.post<AttendanceRecord>('/attendance/punch', payload);
  }

  getMyAttendance(startDate?: string, endDate?: string): Observable<AttendanceRecord[]> {
    let params: any = {};
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    return this.apiService.get<AttendanceRecord[]>('/attendance/my-attendance', params);
  }

  getDailyAttendance(date?: string, startDate?: string, endDate?: string, employeeId?: string): Observable<AttendanceRecord[]> {
    let params: any = {};
    if (date) params.date = date;
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<AttendanceRecord[]>('/attendance/daily', params);
  }

  correctAttendance(id: string, payload: any): Observable<AttendanceRecord> {
    return this.apiService.put<AttendanceRecord>('/attendance/correct', id, payload);
  }

  updateAttendance(payload: any): Observable<AttendanceRecord> {
    return this.apiService.put<AttendanceRecord>('/attendance/update', payload);
  }

  deleteMyAttendance(id: string): Observable<void> {
    return this.apiService.deletePath(`/attendance/my-attendance/${id}`);
  }

  deleteAttendance(id: string): Observable<void> {
    return this.apiService.deletePath(`/attendance/${id}`);
  }

  getDashboardKpis(date?: string, startDate?: string, endDate?: string, department?: string, employeeId?: string): Observable<any> {
    let params: any = {};
    if (date) params.date = date;
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    if (department) params.department = department;
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<any>('/attendance/dashboard-kpis', params);
  }

  getKpiDetails(category: string, startDate?: string, endDate?: string, employeeId?: string): Observable<AttendanceRecord[]> {
    let params: any = { category };
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<AttendanceRecord[]>('/attendance/kpi-details', params);
  }

  getMyDashboardKpis(): Observable<any> {
    return this.apiService.get<any>('/attendance/my-dashboard-kpis');
  }

  // --- LEAVES ---
  getMyLeaveEntitlement(year?: number): Observable<LeaveEntitlement> {
    return this.apiService.get<LeaveEntitlement>('/leaves/my-entitlements', year ? { year } : {});
  }

  getAllEntitlements(year?: number): Observable<LeaveEntitlement[]> {
    return this.apiService.get<LeaveEntitlement[]>('/leaves/entitlements', year ? { year } : {});
  }

  applyLeave(request: LeaveRequest): Observable<LeaveRequest> {
    return this.apiService.post<LeaveRequest>('/leaves/apply', request).pipe(
      tap(() => this.leaveChanged$.next())
    );
  }

  getMyLeaveRequests(): Observable<LeaveRequest[]> {
    return this.apiService.get<LeaveRequest[]>('/leaves/my-requests');
  }

  getPendingLeaveRequests(): Observable<LeaveRequest[]> {
    return this.apiService.get<LeaveRequest[]>('/leaves/pending');
  }

  getAllLeaveRequests(employeeId?: string): Observable<LeaveRequest[]> {
    let params: any = {};
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<LeaveRequest[]>('/leaves/all', params);
  }

  approveOrRejectLeave(id: string, approve: boolean, adminRemarks?: string): Observable<LeaveRequest> {
    return this.apiService.put<LeaveRequest>('/leaves/approve', id, { approve, adminRemarks }).pipe(
      tap(() => this.leaveChanged$.next())
    );
  }

  updateLeave(id: string, request: Partial<LeaveRequest>): Observable<LeaveRequest> {
    return this.apiService.put<LeaveRequest>('/leaves', id, request).pipe(
      tap(() => this.leaveChanged$.next())
    );
  }

  deleteLeave(id: string): Observable<void> {
    return this.apiService.deletePath(`/leaves/${id}`).pipe(
      tap(() => this.leaveChanged$.next())
    );
  }

  // --- PERMISSIONS ---
  requestPermission(request: PermissionRequest): Observable<PermissionRequest> {
    return this.apiService.post<PermissionRequest>('/permissions/request', request);
  }

  getMyPermissionRequests(): Observable<PermissionRequest[]> {
    return this.apiService.get<PermissionRequest[]>('/permissions/my-requests');
  }

  getPendingPermissionRequests(): Observable<PermissionRequest[]> {
    return this.apiService.get<PermissionRequest[]>('/permissions/pending');
  }

  getAllPermissionRequests(employeeId?: string): Observable<PermissionRequest[]> {
    let params: any = {};
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<PermissionRequest[]>('/permissions/all', params);
  }

  approveOrRejectPermission(id: string, approve: boolean, adminRemarks?: string): Observable<PermissionRequest> {
    return this.apiService.put<PermissionRequest>('/permissions/approve', id, { approve, adminRemarks });
  }

  updatePermission(id: string, request: Partial<PermissionRequest>): Observable<PermissionRequest> {
    return this.apiService.put<PermissionRequest>('/permissions', id, request);
  }

  deletePermission(id: string): Observable<void> {
    return this.apiService.deletePath(`/permissions/${id}`);
  }

  // --- PAYROLL & SALARY ---
  getSalaryStructure(employeeId: string): Observable<SalaryStructure> {
    return this.apiService.get<SalaryStructure>(`/payroll/salary-structure/${employeeId}`);
  }

  saveSalaryStructure(structure: SalaryStructure): Observable<SalaryStructure> {
    return this.apiService.post<SalaryStructure>('/payroll/salary-structure', structure);
  }

  getPagedDailyAttendance(
    startDate?: string,
    endDate?: string,
    employeeId?: string,
    status?: string,
    search?: string,
    page: number = 0,
    size: number = 10
  ): Observable<any> {
    let params: any = { page, size };
    if (startDate) params.startDate = startDate;
    if (endDate) params.endDate = endDate;
    if (employeeId) params.employeeId = employeeId;
    if (status && status !== 'ALL') params.status = status;
    if (search && search.trim()) params.search = search.trim();
    return this.apiService.get<any>('/attendance/paged', params);
  }

  saveDraftPayroll(draft: PayrollRecord): Observable<PayrollRecord> {
    return this.apiService.post<PayrollRecord>('/payroll/save-draft', draft);
  }

  addBonus(bonus: EmployeeBonus): Observable<EmployeeBonus> {
    return this.apiService.post<EmployeeBonus>('/payroll/bonus', bonus);
  }

  getMyBonuses(): Observable<EmployeeBonus[]> {
    return this.apiService.get<EmployeeBonus[]>('/payroll/bonus/my-bonuses');
  }

  getAllBonuses(employeeId?: string): Observable<EmployeeBonus[]> {
    let params: any = {};
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<EmployeeBonus[]>('/payroll/bonus/all', params);
  }

  // --- ADVANCE ACCOUNTS & TRANSACTIONS ---
  getAllAdvanceAccounts(): Observable<EmployeeAdvanceAccount[]> {
    return this.apiService.get<EmployeeAdvanceAccount[]>('/payroll/advance-account/all');
  }

  getEmployeeAdvanceAccount(employeeId: string): Observable<EmployeeAdvanceAccount> {
    return this.apiService.get<EmployeeAdvanceAccount>(`/payroll/advance-account/${employeeId}`);
  }

  getAdvanceTransactions(employeeId: string): Observable<AdvanceTransaction[]> {
    return this.apiService.get<AdvanceTransaction[]>(`/payroll/advance-account/${employeeId}/transactions`);
  }

  checkOutstandingAdvance(employeeId: string): Observable<number> {
    return this.apiService.get<number>(`/payroll/check-advance/${employeeId}`);
  }

  addSalaryAdvance(employeeId: string, transaction: Partial<AdvanceTransaction>): Observable<AdvanceTransaction> {
    return this.apiService.post<AdvanceTransaction>(`/payroll/advance-account/${employeeId}/advance`, transaction);
  }

  recordRepayment(employeeId: string, transaction: Partial<AdvanceTransaction>): Observable<AdvanceTransaction> {
    return this.apiService.post<AdvanceTransaction>(`/payroll/advance-account/${employeeId}/repayment`, transaction);
  }

  addAdvanceTransaction(employeeId: string, transaction: AdvanceTransaction): Observable<AdvanceTransaction> {
    return this.apiService.post<AdvanceTransaction>(`/payroll/advance-account/${employeeId}/transaction`, transaction);
  }

  updateAdvanceTransaction(transactionId: string, transaction: Partial<AdvanceTransaction>): Observable<AdvanceTransaction> {
    return this.apiService.put<AdvanceTransaction>(`/payroll/advance-transaction/${transactionId}`, transaction);
  }

  deleteAdvanceTransaction(transactionId: string): Observable<void> {
    return this.apiService.deletePath(`/payroll/advance-transaction/${transactionId}`);
  }

  batchProcessPayroll(payrollMonth: string, employeeDeductionsMap?: { [employeeId: string]: number }): Observable<PayrollRecord[]> {
    return this.apiService.post<PayrollRecord[]>('/payroll/batch-process', { payrollMonth, employeeDeductionsMap });
  }

  processMonthlyPayroll(payrollMonth: string): Observable<PayrollRecord[]> {
    return this.apiService.post<PayrollRecord[]>(`/payroll/process/${payrollMonth}`, {});
  }

  processPayrollForEmployee(employeeId: string, payrollMonth: string, advanceDeductionAmount?: number): Observable<PayrollRecord> {
    let url = `/payroll/process-single/${employeeId}/${payrollMonth}`;
    if (advanceDeductionAmount !== undefined && advanceDeductionAmount !== null) {
      url += `?advanceDeductionAmount=${advanceDeductionAmount}`;
    }
    return this.apiService.post<PayrollRecord>(url, {});
  }


  lockPayroll(id: string): Observable<PayrollRecord> {
    return this.apiService.put<PayrollRecord>(`/payroll/lock/${id}`, {});
  }

  unlockPayroll(id: string, reason?: string): Observable<PayrollRecord> {
    return this.apiService.put<PayrollRecord>(`/payroll/unlock/${id}${reason ? '?reason=' + encodeURIComponent(reason) : ''}`, {});
  }

  getMyPayslips(): Observable<PayrollRecord[]> {
    return this.apiService.get<PayrollRecord[]>('/payroll/payslip/my-payslips');
  }

  getPayslipsByMonth(payrollMonth: string, employeeId?: string): Observable<PayrollRecord[]> {
    let params: any = {};
    if (employeeId) params.employeeId = employeeId;
    return this.apiService.get<PayrollRecord[]>(`/payroll/payslip/month/${payrollMonth}`, params);
  }

  downloadPayslipPdf(employeeId: string, payrollMonth: string): Observable<Blob> {
    return this.apiService.getBlob(`/payroll/payslip/${employeeId}/${payrollMonth}/pdf`);
  }

  deletePayslip(id: string, reason?: string): Observable<void> {
    const path = `/payroll/payslip/${id}${reason ? '?reason=' + encodeURIComponent(reason) : ''}`;
    return this.apiService.deletePath(path);
  }

  // --- HOLIDAYS ---
  getHolidays(year?: number): Observable<any[]> {
    return this.apiService.get<any[]>('/holidays', year ? { year } : {});
  }

  createHoliday(holiday: any): Observable<any> {
    return this.apiService.post<any>('/holidays', holiday);
  }

  updateHoliday(id: string, holiday: any): Observable<any> {
    return this.apiService.put<any>(`/holidays/${id}`, holiday);
  }

  deleteHoliday(id: string): Observable<void> {
    return this.apiService.delete<void>('/holidays', id);
  }

  // --- BIOMETRIC DEVICES ---
  registerDevice(device: AttendanceDevice): Observable<AttendanceDevice> {
    return this.apiService.post<AttendanceDevice>('/biometric-devices/register', device);
  }

  listDevices(): Observable<AttendanceDevice[]> {
    return this.apiService.get<AttendanceDevice[]>('/biometric-devices/list');
  }

  mapEmployeeToDevice(payload: { employeeId: string; deviceUserId: string; deviceId: string; cardNo?: string }): Observable<DeviceEmployeeMapping> {
    return this.apiService.post<DeviceEmployeeMapping>('/biometric-devices/map-employee', payload);
  }

  getEmployeeDeviceMappings(employeeId: string): Observable<DeviceEmployeeMapping[]> {
    return this.apiService.get<DeviceEmployeeMapping[]>(`/biometric-devices/mappings/${employeeId}`);
  }

  testDeviceConnection(deviceId: string): Observable<any> {
    return this.apiService.post<any>(`/biometric-devices/test-connection/${deviceId}`, {});
  }

  syncDeviceNow(deviceId: string): Observable<{ success: boolean; syncedCount: number }> {
    return this.apiService.post<{ success: boolean; syncedCount: number }>(`/biometric-devices/sync/${deviceId}`, {});
  }
}
