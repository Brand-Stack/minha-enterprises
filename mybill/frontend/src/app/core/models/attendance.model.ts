export interface AttendancePunch {
  punchId?: string;
  timestamp: string;
  type: 'IN' | 'OUT';
  source?: 'MANUAL' | 'FINGERPRINT_DEVICE' | 'MOBILE_APP' | 'SYSTEM_AUTO';
  deviceId?: string;
  rawPayload?: string;
  processed?: boolean;
  latitude?: number;
  longitude?: number;
  distanceFromOfficeMeters?: number;
  punchedByAdmin?: boolean;
  adminEmployeeId?: string;
}

export interface EmployeeBankAccount {
  id?: string;
  bankName: string;
  accountHolderName?: string;
  accountNumber: string;
  ifscCode?: string;
  bankBranch?: string;
  accountType?: string; // SAVINGS, CURRENT, SALARY
  isPrimary?: boolean;
  createdDate?: string;
}

export type AttendanceStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'LATE'
  | 'EARLY_CHECKOUT'
  | 'LATE_AND_EARLY_CHECKOUT'
  | 'HALF_DAY'
  | 'LEAVE'
  | 'HOLIDAY'
  | 'WEEK_OFF'
  | 'PERMISSION'
  | 'WORK_FROM_HOME'
  | 'OVERTIME'
  | 'INCOMPLETE';

export interface AttendanceRecord {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  department?: string;
  date: string;
  checkInTime?: string;
  checkOutTime?: string;
  totalWorkingHours?: number;
  expectedWorkingHours?: number;
  breakDurationMinutes?: number;
  lateArrival?: boolean;
  lateMinutes?: number;
  earlyDeparture?: boolean;
  earlyMinutes?: number;
  overtimeHours?: number;
  status: AttendanceStatus;
  sourceOfPunch?: string;
  remarks?: string;
  punches?: AttendancePunch[];
  manuallyCorrected?: boolean;
  correctedBy?: string;
  correctedAt?: string;
  correctionReason?: string;
}

export interface LeaveEntitlement {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  year: number;
  casualLeaveTotal: number;
  casualLeaveUsed: number;
  medicalLeaveTotal: number;
  medicalLeaveUsed: number;
  emergencyLeaveTotal: number;
  emergencyLeaveUsed: number;
  compOffTotal?: number;
  compOffUsed?: number;
  otherLeaveTotal: number;
  otherLeaveUsed: number;
  carryForwardDays?: number;
}

export interface LeaveRequest {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  department?: string;
  leaveType: 'CASUAL_LEAVE' | 'MEDICAL_LEAVE' | 'EMERGENCY_LEAVE' | 'COMP_OFF' | 'OTHER_LEAVE' | 'MATERNITY_LEAVE' | 'PATERNITY_LEAVE' | 'UNPAID_LOP';
  fromDate: string;
  toDate: string;
  numberOfDays?: number;
  isHalfDay?: boolean;
  reason?: string;
  attachmentPath?: string;
  status?: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  appliedDate?: string;
  approvedBy?: string;
  approvedDate?: string;
  adminRemarks?: string;
}

export interface PermissionRequest {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  date: string;
  startTime: string;
  endTime: string;
  durationMinutes?: number;
  reason?: string;
  status?: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  approvedBy?: string;
  approvedDate?: string;
  adminRemarks?: string;
}

export interface SalaryStructure {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  basicSalary: number;
  hra: number;
  allowances: number;
  pfDeduction: number;
  professionalTax: number;
  incomeTax: number;
  effectiveFrom?: string;
  active?: boolean;
}

export type AdvanceTransactionType = 'ADVANCE_GIVEN' | 'REPAYMENT' | 'PAYROLL_DEDUCTION' | 'ADJUSTMENT';

export interface EmployeeAdvanceAccount {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  totalAdvanceGiven?: number;
  totalRepaid?: number;
  totalPayrollDeducted?: number;
  outstandingBalance: number;
  status?: string;
}

export interface AdvanceTransaction {
  id?: string;
  advanceAccountId?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  transactionType: AdvanceTransactionType;
  amount: number;
  transactionDate?: string;
  paymentMode?: string;
  otherPaymentModeDetails?: string;
  referenceNumber?: string;
  description?: string;
  reason?: string;
  remarks?: string;
  notes?: string;
  payrollMonth?: string;
  payslipId?: string;
  previousBalance?: number;
  resultingBalance?: number;
  createdBy?: string;
  createdAt?: string;
  status?: string;
}

export interface EmployeeBonus {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  bonusType: 'PERFORMANCE_BONUS' | 'FESTIVAL_BONUS' | 'SPECIAL_BONUS' | 'YEARLY_BONUS' | 'INCENTIVE' | 'OTHER_BONUS';
  amount: number;
  bonusYear?: number;
  paymentDate?: string;
  payrollMonth?: string;
  remarks?: string;
  approvedBy?: string;
}

export interface PayrollRecord {
  id?: string;
  payslipNumber: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  department?: string;
  designation?: string;
  joiningDate?: string;
  payrollMonth: string;
  totalWorkingDays?: number;
  presentDays?: number;
  leaveDays?: number;
  lopDays?: number;
  overtimeHours?: number;
  basicSalary: number;
  hra: number;
  allowances: number;
  overtimeAmount: number;
  bonusAmount: number;
  yearlyBonusAmount: number;
  otherEarnings: number;
  pfAmount: number;
  taxAmount: number;
  professionalTax: number;
  lopAmount: number;
  advanceDeductionAmount: number;
  otherDeductions: number;
  grossSalary: number;
  totalEarnings: number;
  totalDeductions: number;
  netSalary: number;
  bankName?: string;
  accountHolderName?: string;
  accountNumber?: string;
  ifscCode?: string;
  bankBranch?: string;
  status?: string;
  isLocked?: boolean;
  lockedAt?: string;
  lockedBy?: string;
  version?: number;
  advanceId?: string;
  originalAdvanceAmount?: number;
  previousAdvanceBalance?: number;
  remainingAdvanceBalance?: number;
  totalAdvanceRecoveredSoFar?: number;
  generatedAt?: string;
  generatedBy?: string;
  auditLogs?: any[];
}

export interface AttendanceDevice {
  id?: string;
  deviceId: string;
  deviceName: string;
  deviceIp?: string;
  port?: number;
  serialNumber?: string;
  location?: string;
  status?: 'ONLINE' | 'OFFLINE' | 'SYNCING' | 'ERROR';
  lastSyncTime?: string;
  lastSyncMessage?: string;
  syncIntervalMinutes?: number;
}

export interface DeviceEmployeeMapping {
  id?: string;
  employeeId: string;
  employeeCode?: string;
  employeeName?: string;
  deviceId: string;
  deviceUserId: string;
  cardNo?: string;
  active?: boolean;
}
